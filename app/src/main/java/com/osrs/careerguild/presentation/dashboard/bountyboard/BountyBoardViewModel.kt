package com.osrs.careerguild.presentation.dashboard.bountyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osrs.careerguild.core.token.TokenGovernor
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

    init {
        observeJobs()
        observeActiveSkills()
        // Surface budget status immediately so the UI can warn the user
        _uiState.update { it.copy(isGeminiAllowed = tokenGovernor.isGeminiAllowed) }
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
                    _uiState.update { state ->
                        val query = state.searchQuery
                        val activeSkillNames = state.activeSkills.filter { it.isActive }.map { it.name.lowercase() }
                        val filtered = jobs.filter { j ->
                            // Text search
                            val matchesQuery = query.isBlank() ||
                                j.title.contains(query, ignoreCase = true) ||
                                j.company.contains(query, ignoreCase = true)
                            // Active skill filter — show job if it matches any active skill or if no active skills are set
                            val matchesSkill = activeSkillNames.isEmpty() ||
                                j.matchedSkills.any { ms -> activeSkillNames.any { s -> ms.lowercase().contains(s) } } ||
                                activeSkillNames.any { s ->
                                    j.title.lowercase().contains(s) || j.company.lowercase().contains(s)
                                }
                            matchesQuery && matchesSkill
                        }
                        state.copy(jobs = filtered, isLoading = false)
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
                    _uiState.update { it.copy(activeSkills = skills) }
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
        _uiState.update { it.copy(searchQuery = query) }
        // Re-filter is handled reactively in observeJobs() on each collection pass.
        // For an explicit search we trigger a re-observe by launching a search flow.
        viewModelScope.launch {
            val results = if (query.isBlank()) {
                jobRepository.observeAll()
            } else {
                jobRepository.search(query)
            }
            results.catch { /* already handled in observeJobs */ }
                .collectLatest { jobs ->
                    _uiState.update { it.copy(jobs = jobs) }
                }
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
