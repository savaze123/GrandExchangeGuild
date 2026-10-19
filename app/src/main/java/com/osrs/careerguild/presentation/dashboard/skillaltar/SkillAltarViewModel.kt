package com.osrs.careerguild.presentation.dashboard.skillaltar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osrs.careerguild.domain.repository.SkillRepository
import com.osrs.careerguild.domain.usecase.skillmatrix.AddSkillUseCase
import com.osrs.careerguild.domain.usecase.skillmatrix.RemoveSkillUseCase
import com.osrs.careerguild.domain.usecase.skillmatrix.SeedDefaultSkillsUseCase
import com.osrs.careerguild.domain.usecase.skillmatrix.ToggleSkillUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the Skill Altar screen (DYNAMIC_SKILL_MATRIX).
 *
 * Responsibilities:
 *  1. Observe all skills from LOCAL_VAULT as a reactive Room Flow.
 *  2. Seed default skills from project_graph.json on first launch.
 *  3. Dispatch add / toggle / remove actions to the domain use cases.
 *  4. Maintain category filter selection.
 *  5. Expose add-skill form state.
 *
 * Node: DYNAMIC_SKILL_MATRIX
 * Edges:
 *   → TECH_RADAR_SCRAPER (active skills injected into search queries)
 *   → RESUME_FORGE_PDF   (active skills used for ATS tailoring)
 */
class SkillAltarViewModel(
    private val skillRepository      : SkillRepository,
    private val addSkill             : AddSkillUseCase,
    private val toggleSkill          : ToggleSkillUseCase,
    private val removeSkill          : RemoveSkillUseCase,
    private val seedDefaultSkills    : SeedDefaultSkillsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillAltarUiState())
    val uiState: StateFlow<SkillAltarUiState> = _uiState.asStateFlow()

    init {
        observeSkills()
    }

    // ---------------------------------------------------------------------------
    // Observe LOCAL_VAULT — reactive Room Flow
    // ---------------------------------------------------------------------------

    private fun observeSkills() {
        viewModelScope.launch {
            skillRepository.observeAll()
                .catch { e ->
                    _uiState.update { it.copy(error = "Failed to load skills: ${e.message}", isLoading = false) }
                }
                .collectLatest { skills ->
                    val categories = skills.map { it.category }.distinct().sorted()

                    // Seed defaults on first launch (table was empty before flow emitted)
                    if (skills.isEmpty()) {
                        seedDefaultSkills()
                        return@collectLatest
                    }

                    _uiState.update { state ->
                        state.copy(
                            skills     = skills.sortedWith(compareBy({ it.category }, { it.name })),
                            categories = categories,
                            isLoading  = false,
                        )
                    }
                }
        }
    }

    // ---------------------------------------------------------------------------
    // Skill CRUD
    // ---------------------------------------------------------------------------

    fun addSkill() {
        val name     = _uiState.value.addSkillName.trim()
        val category = _uiState.value.addSkillCategory
        if (name.isBlank()) return

        viewModelScope.launch {
            addSkill(name, category)
                .onSuccess {
                    _uiState.update { it.copy(addSkillName = "", successMessage = "'$name' added to Altar.") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to add skill.") }
                }
        }
    }

    fun toggleSkill(name: String) {
        viewModelScope.launch {
            toggleSkill.invoke(name)
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Toggle failed: ${e.message}") }
                }
        }
    }

    fun removeSkill(name: String) {
        viewModelScope.launch {
            removeSkill.invoke(name)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "'$name' removed.") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Remove failed: ${e.message}") }
                }
        }
    }

    // ---------------------------------------------------------------------------
    // Form state
    // ---------------------------------------------------------------------------

    fun updateAddSkillName(name: String)         { _uiState.update { it.copy(addSkillName = name) } }
    fun updateAddSkillCategory(category: String) { _uiState.update { it.copy(addSkillCategory = category) } }
    fun toggleAddSkillPanel()                    { _uiState.update { it.copy(isAddSkillExpanded = !it.isAddSkillExpanded) } }

    // ---------------------------------------------------------------------------
    // Category filter
    // ---------------------------------------------------------------------------

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    // ---------------------------------------------------------------------------
    // Message dismissal
    // ---------------------------------------------------------------------------

    fun dismissSuccess() { _uiState.update { it.copy(successMessage = null) } }
    fun dismissError()   { _uiState.update { it.copy(error = null) } }
}
