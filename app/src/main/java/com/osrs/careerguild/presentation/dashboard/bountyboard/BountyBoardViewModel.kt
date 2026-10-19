package com.osrs.careerguild.presentation.dashboard.bountyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osrs.careerguild.domain.repository.JobRepository
import com.osrs.careerguild.domain.usecase.bountyboard.BlockCompanyUseCase
import com.osrs.careerguild.domain.usecase.bountyboard.FetchAndScoreJobsUseCase
import com.osrs.careerguild.domain.usecase.bountyboard.MarkAppliedUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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
 * Observes [JobRepository.observeAll] / [JobRepository.search] for reactive
 * job list updates (Room Flow → StateFlow).
 *
 * Node: UI_SPLIT_SCAFFOLD (right pane — Bounty Board)
 */
class BountyBoardViewModel(
    private val jobRepository      : JobRepository,
    private val fetchAndScoreJobs  : FetchAndScoreJobsUseCase,
    private val blockCompany       : BlockCompanyUseCase,
    private val markApplied        : MarkAppliedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BountyBoardUiState())
    val uiState: StateFlow<BountyBoardUiState> = _uiState.asStateFlow()

    init {
        observeJobs()
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
                        val filtered = if (query.isBlank()) jobs
                                       else jobs.filter { j ->
                                           j.title.contains(query, ignoreCase = true) ||
                                           j.company.contains(query, ignoreCase = true)
                                       }
                        state.copy(jobs = filtered, isLoading = false)
                    }
                }
        }
    }

    // ---------------------------------------------------------------------------
    // Refresh — triggers TECH_RADAR_SCRAPER → TRAJECTORY_SCORER pipeline
    // ---------------------------------------------------------------------------

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScraping = true, isScoring = false, error = null) }

            fetchAndScoreJobs(
                onProgress = { current, total, title ->
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
                _uiState.update {
                    it.copy(
                        isScraping      = false,
                        isScoring       = false,
                        scoringProgress = null,
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
