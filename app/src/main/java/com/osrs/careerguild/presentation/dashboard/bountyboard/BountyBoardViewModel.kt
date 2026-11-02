package com.osrs.careerguild.presentation.dashboard.bountyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osrs.careerguild.core.location.JobLocationUtils
import com.osrs.careerguild.core.network.LinkValidator
import com.osrs.careerguild.core.token.TokenGovernor
import com.osrs.careerguild.domain.model.JobListing
import com.osrs.careerguild.domain.model.Skill
import com.osrs.careerguild.domain.repository.JobRepository
import com.osrs.careerguild.domain.repository.SkillRepository
import com.osrs.careerguild.domain.usecase.bountyboard.BlockCompanyUseCase
import com.osrs.careerguild.domain.usecase.bountyboard.FetchAndScoreJobsUseCase
import com.osrs.careerguild.domain.usecase.bountyboard.MarkAppliedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val searchQueryFlow = MutableStateFlow("")
    private var cachedAllJobs: List<JobListing> = emptyList()

    init {
        observeJobs()
        observeActiveSkills()
        observeSearchQuery()
        _uiState.update { it.copy(isGeminiAllowed = tokenGovernor.isGeminiAllowed) }
        viewModelScope.launch {
            refresh()
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(150L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val currentState = _uiState.value
                    val filtered = withContext(Dispatchers.Default) {
                        filterJobs(
                            cachedAllJobs,
                            query,
                            currentState.activeSkills,
                            currentState.zipCode,
                            currentState.searchRadius,
                            currentState.workMode,
                            currentState.sortOption,
                        )
                    }
                    _uiState.update { state ->
                        state.copy(jobs = filtered)
                    }
                }
        }
    }

    // ---------------------------------------------------------------------------
    // Filtering & dynamic skill scoring logic
    // ---------------------------------------------------------------------------

    private fun enrichJobWithActiveSkills(job: JobListing, activeSkills: List<Skill>): JobListing {
        val active = activeSkills.filter { it.isActive }
        val resolvedCoords = JobLocationUtils.resolveJobCoordinates(job.location)
        val lat = job.lat ?: resolvedCoords.lat
        val lng = job.lng ?: resolvedCoords.lon
        val baseRequired = if (job.requiredSkills.isNotEmpty()) job.requiredSkills else job.matchedSkills

        if (active.isEmpty()) {
            return job.copy(
                lat = lat,
                lng = lng,
                requiredSkills = baseRequired,
            )
        }

        val searchableText = "${job.title} ${job.company} ${job.description} ${baseRequired.joinToString(" ")}".lowercase()
        val matchingActive = active.filter { skill ->
            val sName = skill.name.lowercase()
            searchableText.contains(sName) ||
            (sName.contains("ai") && searchableText.contains("ai")) ||
            (sName.contains("python") && searchableText.contains("python")) ||
            (sName.contains("sql") && searchableText.contains("sql")) ||
            (sName.contains("docker") && searchableText.contains("docker"))
        }

        val dynamicMatchedSkills = (matchingActive.map { it.name } + baseRequired).distinct()
        val intersectionRatio = if (baseRequired.isNotEmpty()) {
            val matchedReqCount = baseRequired.count { req ->
                val r = req.lowercase()
                active.any { s -> s.name.lowercase().contains(r) || r.contains(s.name.lowercase()) }
            }
            (matchedReqCount.toDouble() / baseRequired.size.coerceAtLeast(1).toDouble()).coerceIn(0.0, 1.0)
        } else {
            (matchingActive.size.toDouble() / active.size.coerceAtLeast(1).toDouble()).coerceIn(0.0, 1.0)
        }

        // Composite skill match score: blends intersection percentage and matched active skill count (0-99%)
        val dynamicScore = if (matchingActive.isNotEmpty()) {
            val pctBonus = (intersectionRatio * 20).toInt()
            (58 + (matchingActive.size * 7) + pctBonus).coerceIn(60, 99)
        } else {
            job.score.coerceIn(40, 75)
        }

        return job.copy(
            score = dynamicScore,
            matchedSkills = dynamicMatchedSkills,
            requiredSkills = dynamicMatchedSkills,
            lat = lat,
            lng = lng,
        )
    }

    private fun filterJobs(
        jobs: List<JobListing>,
        query: String,
        activeSkills: List<Skill>,
        zipCode: String = _uiState.value.zipCode,
        radius: String = _uiState.value.searchRadius,
        workMode: String = _uiState.value.workMode,
        sortOption: String = _uiState.value.sortOption,
    ): List<JobListing> {
        val q = query.trim()
        val activeSkillNames = activeSkills.filter { it.isActive }.map { it.name.lowercase() }
        val effectiveZip = zipCode.ifBlank { "11581" }

        val filtered = jobs
            .map { enrichJobWithActiveSkills(it, activeSkills) }
            .filter { j ->
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
                    userZip = effectiveZip,
                    searchRadius = radius,
                )

                // Work mode filter (All, Local, Hybrid, Remote)
                val matchesWorkMode = JobLocationUtils.matchesWorkMode(
                    jobWorkMode = j.workMode,
                    jobLocation = j.location,
                    filterMode = workMode,
                )

                // Strict ATS link requirement (Task 2: drop dead, seeded, or unverified links)
                val directUrl = j.applyUrl.ifBlank { j.atsUrl }
                val hasVerifiedDirectLink = j.linkStatus != "DEAD" &&
                    j.linkStatus != "SEED" &&
                    LinkValidator.isValidDirectAtsUrl(directUrl, j.linkStatus)

                matchesQuery && matchesSkill && matchesRadius && matchesWorkMode && hasVerifiedDirectLink
            }

        return when (sortOption) {
            "Latest" -> filtered.sortedWith(
                compareByDescending<JobListing> { it.timePosted }
                    .thenByDescending { it.score }
            )
            "Closest" -> filtered.sortedWith(
                compareBy<JobListing> { j ->
                    JobLocationUtils.calculateJobDistanceMiles(
                        jobLocation = j.location,
                        userZip = effectiveZip,
                        lat = j.lat,
                        lng = j.lng,
                    )
                }.thenByDescending { it.score }
            )
            else -> filtered.sortedWith(
                compareByDescending<JobListing> { it.score }
                    .thenByDescending { it.timePosted }
                    .thenBy { j ->
                        JobLocationUtils.calculateJobDistanceMiles(
                            jobLocation = j.location,
                            userZip = effectiveZip,
                            lat = j.lat,
                            lng = j.lng,
                        )
                    }
            )
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
                            jobs = filterJobs(cachedAllJobs, state.searchQuery, state.activeSkills, state.zipCode, state.searchRadius, state.workMode, state.sortOption),
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
                    val activeNames = skills.filter { it.isActive }.map { it.name.lowercase() }.toSet()
                    _uiState.update { state ->
                        state.copy(
                            activeSkills = skills,
                            activeSkillNames = activeNames,
                            jobs = filterJobs(cachedAllJobs, state.searchQuery, skills, state.zipCode, state.searchRadius, state.workMode, state.sortOption),
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
            val previousCount = cachedAllJobs.size
            _uiState.update {
                it.copy(
                    isScraping      = true,
                    isScoring       = false,
                    error           = null,
                    isGeminiAllowed = geminiAllowed,
                    dataSourceMode  = "LIVE_FETCHING",
                    lastSyncMessage = "Live Fetching ATS Feeds (Greenhouse · Lever · Ashby · Remotive · Arbeitnow)…",
                )
            }

            val scoredCount = intArrayOf(0)
            val totalQueued = intArrayOf(0)
            fetchAndScoreJobs(
                onProgress = { current, total, title ->
                    scoredCount[0] = current
                    totalQueued[0] = total
                    _uiState.update { state ->
                        state.copy(
                            isScraping      = false,
                            isScoring       = true,
                            dataSourceMode  = "LIVE_FETCHING",
                            lastSyncMessage = "Live Scoring $current/$total ATS Postings…",
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
                        dataSourceMode  = "OFFLINE_CACHE",
                        lastSyncMessage = "Offline Cached Data (${it.jobs.size} verified quests in Room Vault)",
                        error           = e.message ?: "Scrape failed",
                    )
                }
            }.onSuccess {
                // Record cost if Gemini was used for this batch
                if (geminiAllowed && scoredCount[0] > 0) {
                    tokenGovernor.recordScoringBatch(scoredCount[0])
                }
                val currentTotal = cachedAllJobs.size
                val delta = (currentTotal - previousCount).coerceAtLeast(scoredCount[0])
                val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                _uiState.update {
                    it.copy(
                        isScraping         = false,
                        isScoring          = false,
                        scoringProgress    = null,
                        isGeminiAllowed    = tokenGovernor.isGeminiAllowed,
                        dataSourceMode     = if (delta > 0 || currentTotal > 0) "LIVE_SYNCED" else "OFFLINE_CACHE",
                        newJobsPulledCount = delta,
                        lastSyncMessage    = if (delta > 0) {
                            "Live Feed Synced at $timeStr (+$delta verified postings)"
                        } else {
                            "Live Feed Verified at $timeStr (${it.jobs.size} active postings · 0 new)"
                        },
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
            state.copy(searchQuery = query)
        }
        searchQueryFlow.value = query
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
                jobs = filterJobs(cachedAllJobs, state.searchQuery, state.activeSkills, clean, state.searchRadius, state.workMode, state.sortOption),
            )
        }
    }

    fun updateSearchRadius(radius: String) {
        _uiState.update { state ->
            state.copy(
                searchRadius = radius,
                jobs = filterJobs(cachedAllJobs, state.searchQuery, state.activeSkills, state.zipCode, radius, state.workMode, state.sortOption),
            )
        }
    }

    fun updateWorkMode(mode: String) {
        _uiState.update { state ->
            state.copy(
                workMode = mode,
                jobs = filterJobs(cachedAllJobs, state.searchQuery, state.activeSkills, state.zipCode, state.searchRadius, mode, state.sortOption),
            )
        }
    }

    fun updateSortOption(sort: String) {
        _uiState.update { state ->
            state.copy(
                sortOption = sort,
                jobs = filterJobs(cachedAllJobs, state.searchQuery, state.activeSkills, state.zipCode, state.searchRadius, state.workMode, sort),
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
