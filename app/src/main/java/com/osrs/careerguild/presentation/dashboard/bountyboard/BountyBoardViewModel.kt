package com.osrs.careerguild.presentation.dashboard.bountyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osrs.careerguild.core.location.JobLocationUtils
import com.osrs.careerguild.core.token.TokenGovernor
import com.osrs.careerguild.domain.model.JobListing
import com.osrs.careerguild.domain.model.Skill
import com.osrs.careerguild.domain.repository.JobRepository
import com.osrs.careerguild.domain.repository.SkillRepository
import com.osrs.careerguild.domain.usecase.bountyboard.BlockCompanyUseCase
import com.osrs.careerguild.domain.usecase.bountyboard.FetchAndScoreJobsUseCase
import com.osrs.careerguild.domain.usecase.bountyboard.MarkAppliedUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the Bounty Board screen.
 *
 * Owns [BountyBoardUiState] and dispatches to:
 *  - [FetchAndScoreJobsUseCase]  — TECH_RADAR_SCRAPER + TRAJECTORY_SCORER pipeline
 *  - [BlockCompanyUseCase]       — LOCAL_VAULT blocklist write
 *  - [MarkAppliedUseCase]        — job applied timestamp
 *
 * Budget gating: [TokenGovernor] gates whether Gemini scoring is invoked.
 * If the $10/month cap is exceeded, the use case falls back to Ktor keyword
 * search pre-filtered results persisted in Room (score = 0, no AI scoring).
 *
 * Skill filter: active skills from DYNAMIC_SKILL_MATRIX are surfaced via
 * [BountyBoardUiState.activeSkills] so the Skill Altar selection drives
 * scraper keyword injection and UI-side job filtering simultaneously.
 *
 * Observes [JobRepository.observeAll] / [JobRepository.search] for reactive
 * job list updates (Room Flow → StateFlow).
 *
 * Node: UI_SPLIT_SCAFFOLD (right pane — Bounty Board)
 */
class BountyBoardViewModel(
    private val jobRepository      : JobRepository,
    private val skillRepository    : SkillRepository,
    private val fetchAndScoreJobs  : FetchAndScoreJobsUseCase,
    private val blockCompany       : BlockCompanyUseCase,
    private val markApplied        : MarkAppliedUseCase,
    private val tokenGovernor      : TokenGovernor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BountyBoardUiState())
    val uiState: StateFlow<BountyBoardUiState> = _uiState.asStateFlow()

    private var cachedAllJobs: List<JobListing> = emptyList()

    init {
        observeJobs()
        observeActiveSkills()
        _uiState.update { it.copy(isGeminiAllowed = tokenGovernor.isGeminiAllowed) }
        viewModelScope.launch {
            refresh()
        }
    }

    // ---------------------------------------------------------------------------
    // Filtering logic combining search query, active skills, and broad CS match
    // ---------------------------------------------------------------------------

    private fun filterJobs(
        jobs: List<JobListing>,
        query: String,
        activeSkills: List<Skill>,
        zipCode: String = _uiState.value.zipCode,
        radius: String = _uiState.value.searchRadius,
        workMode: String = _uiState.value.workMode,
    ): List<JobListing> {
        val q = query.trim()
        val activeSkillNames = activeSkills.filter { it.isActive }.map { it.name.lowercase() }

        return jobs.filter { j ->
            // Text search: matches title, company, or any matched skill tag
            val matchesQuery = q.isBlank() ||
                j.title.contains(q, ignoreCase = true) ||
                j.company.contains(q, ignoreCase = true) ||
                j.matchedSkills.any { it.contains(q, ignoreCase = true) }

            // Active skill filter — show job if it matches any active skill or broad CS skills, or if no active skills
            val matchesSkill = activeSkillNames.isEmpty() ||
                j.matchedSkills.isEmpty() ||
                j.matchedSkills.any { ms ->
                    ms.contains("Software", ignoreCase = true) ||
                    ms.contains("Engineering", ignoreCase = true) ||
                    ms.contains("Systems", ignoreCase = true) ||
                    ms.contains("Algorithms", ignoreCase = true) ||
                    activeSkillNames.any { s -> ms.lowercase().contains(s) || s.contains(ms.lowercase()) }
                } ||
                activeSkillNames.any { s ->
                    j.title.lowercase().contains(s) || j.company.lowercase().contains(s)
                }

            // Geographic radius filter
            val matchesRadius = JobLocationUtils.matchesRadius(
                jobLocation = j.location,
                workMode = j.workMode,
                userZip = zipCode.ifBlank { "11581" },
                searchRadius = radius,
            )

            // Work mode filter (All, Local, Hybrid, Remote)
            val matchesWorkMode = JobLocationUtils.matchesWorkMode(
                jobWorkMode = j.workMode,
                jobLocation = j.location,
                filterMode = workMode,
            )

            matchesQuery && matchesSkill && matchesRadius && matchesWorkMode
        }
    }

    // ---------------------------------------------------------------------------
    // Observe LOCAL_VAULT — reactive updates as Room writes land
    // ---------------------------------------------------------------------------

    private fun observeJobs() {
        viewModelScope.launch {
            jobRepository.observeAll()
                .catch { e ->
                    _uiState.update { it.copy(error = "Failed to load jobs: ${e.message}", isLoading = false) }
                }
                .collectLatest { jobs ->
                    cachedAllJobs = jobs
                    _uiState.update { state ->
                        state.copy(
                            jobs = filterJobs(cachedAllJobs, state.searchQuery, state.activeSkills, state.zipCode, state.searchRadius, state.workMode),
                            isLoading = false,
                        )
                    }
                }
        }
    }

    // ---------------------------------------------------------------------------
    // Observe DYNAMIC_SKILL_MATRIX active skills for query injection
    // ---------------------------------------------------------------------------

    private fun observeActiveSkills() {
        viewModelScope.launch {
            skillRepository.observeActive()
                .catch { /* non-fatal: scraper uses cached skills */ }
                .collectLatest { skills ->
                    _uiState.update { state ->
                        state.copy(
                            activeSkills = skills,
                            jobs = filterJobs(cachedAllJobs, state.searchQuery, skills, state.zipCode, state.searchRadius, state.workMode),
                        )
                    }
                }
        }
    }

    // ---------------------------------------------------------------------------
    // Refresh — triggers TECH_RADAR_SCRAPER → TRAJECTORY_SCORER pipeline
    // Respects TokenGovernor: if budget exceeded, Gemini is skipped and the
    // use-case falls through to keyword-filtered Room cache.
    // ---------------------------------------------------------------------------

    fun refresh() {
        viewModelScope.launch {
            android.util.Log.i("BountyBoardVM", "refresh() called on BountyBoardViewModel")
            val geminiAllowed = tokenGovernor.isGeminiAllowed
            _uiState.update {
                it.copy(
                    isScraping     = true,
                    isScoring      = false,
                    error          = null,
                    isGeminiAllowed = geminiAllowed,
                )
            }

            val scoredCount = intArrayOf(0)
            fetchAndScoreJobs(
                onProgress = { current, total, title ->
                    scoredCount[0] = current
                    _uiState.update { state ->
                        state.copy(
                            isScraping      = false,
                            isScoring       = true,
                            scoringProgress = ScoringProgress(
                                current         = current,
                                total           = total,
                                currentJobTitle = title,
                            ),
                        )
                    }
                }
            ).onFailure { e ->
                android.util.Log.e("BountyBoardVM", "fetchAndScoreJobs failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isScraping      = false,
                        isScoring       = false,
                        scoringProgress = null,
                        error           = e.message ?: "Scrape failed",
                    )
                }
            }.onSuccess {
                // Record cost if Gemini was used for this batch
                if (geminiAllowed && scoredCount[0] > 0) {
                    tokenGovernor.recordScoringBatch(scoredCount[0])
                }
                _uiState.update {
                    it.copy(
                        isScraping      = false,
                        isScoring       = false,
                        scoringProgress = null,
                        isGeminiAllowed = tokenGovernor.isGeminiAllowed,
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------------------

    fun search(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                jobs = filterJobs(cachedAllJobs, query, state.activeSkills, state.zipCode, state.searchRadius, state.workMode),
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Search Parameters (Section 3)
    // ---------------------------------------------------------------------------

    fun updateZipCode(zip: String) {
        _uiState.update { it.copy(zipCode = zip) }
    }

    fun applyZipCode(zip: String) {
        _uiState.update { state ->
            val clean = zip.trim()
            state.copy(
                zipCode = clean,
                jobs = filterJobs(cachedAllJobs, state.searchQuery, state.activeSkills, clean, state.searchRadius, state.workMode),
            )
        }
    }

    fun updateSearchRadius(radius: String) {
        _uiState.update { state ->
            state.copy(
                searchRadius = radius,
                jobs = filterJobs(cachedAllJobs, state.searchQuery, state.activeSkills, state.zipCode, radius, state.workMode),
            )
        }
    }

    fun updateWorkMode(mode: String) {
        _uiState.update { state ->
            state.copy(
                workMode = mode,
                jobs = filterJobs(cachedAllJobs, state.searchQuery, state.activeSkills, state.zipCode, state.searchRadius, mode),
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Block company
    // ---------------------------------------------------------------------------

    fun blockCompany(company: String) {
        viewModelScope.launch {
            blockCompany.invoke(company)
                .onSuccess {
                    _uiState.update { it.copy(blockedCompany = company) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Block failed: ${e.message}") }
                }
        }
    }

    fun clearBlockedConfirmation() {
        _uiState.update { it.copy(blockedCompany = null) }
    }

    // ---------------------------------------------------------------------------
    // Mark applied
    // ---------------------------------------------------------------------------

    fun markApplied(jobId: String) {
        viewModelScope.launch {
            markApplied.invoke(jobId)
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Could not mark applied: ${e.message}") }
                }
        }
    }

    // ---------------------------------------------------------------------------
    // Error dismissal
    // ---------------------------------------------------------------------------

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
