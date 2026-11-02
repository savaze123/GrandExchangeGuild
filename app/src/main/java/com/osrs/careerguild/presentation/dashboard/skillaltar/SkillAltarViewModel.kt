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

                    val defaultTabs = listOf("AI & Agents", "Automation & Web", "Backend & Data")
                    _uiState.update { state ->
                        val combinedCategories = (defaultTabs + state.customCategories + skills.map { it.category })
                            .distinct()
                            .filter { it != "Custom" || skills.any { s -> s.category == "Custom" } }
                            .sorted()
                        state.copy(
                            skills     = skills.sortedWith(compareBy({ it.category }, { it.name })),
                            categories = combinedCategories,
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
        val name        = _uiState.value.addSkillName.trim()
        val rawCategory = _uiState.value.addSkillCategory
        val customName  = _uiState.value.customCategoryName.trim()

        val category = if (rawCategory.equals("Custom", ignoreCase = true) && customName.isNotBlank()) {
            customName
        } else {
            rawCategory
        }

        if (name.isBlank()) return

        viewModelScope.launch {
            addSkill(name, category)
                .onSuccess {
                    _uiState.update { state ->
                        val updatedCustom = if (category != "Custom" &&
                            !state.customCategories.contains(category) &&
                            !listOf("AI & Agents", "Automation & Web", "Backend & Data").contains(category)) {
                            state.customCategories + category
                        } else {
                            state.customCategories
                        }
                        state.copy(
                            addSkillName       = "",
                            customCategoryName = "",
                            addSkillCategory   = category,
                            customCategories   = updatedCustom,
                            categories         = (state.categories + updatedCustom).distinct().sorted(),
                            successMessage     = "'$name' added to Altar under '$category'."
                        )
                    }
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
        val defaultTabs = listOf("AI & Agents", "Automation & Web", "Backend & Data")
        val targetSkill = _uiState.value.skills.find { it.name.equals(name, ignoreCase = true) }
        val parentCategory = targetSkill?.category

        viewModelScope.launch {
            removeSkill.invoke(name)
                .onSuccess {
                    _uiState.update { state ->
                        val remainingSkills = state.skills.filterNot { it.name.equals(name, ignoreCase = true) }
                        val remainingInParent = if (parentCategory != null) {
                            remainingSkills.count { it.category.equals(parentCategory, ignoreCase = true) }
                        } else {
                            1
                        }
                        val isCustomEmpty = parentCategory != null &&
                            remainingInParent == 0 &&
                            defaultTabs.none { it.equals(parentCategory, ignoreCase = true) }

                        val updatedCustomCategories = if (isCustomEmpty) {
                            state.customCategories.filterNot { it.equals(parentCategory, ignoreCase = true) }
                        } else {
                            state.customCategories
                        }
                        val updatedCategories = (defaultTabs + updatedCustomCategories + remainingSkills.map { it.category })
                            .distinct()
                            .filter { it != "Custom" || remainingSkills.any { s -> s.category == "Custom" } }
                            .sorted()
                        val updatedSelectedCategory = if (isCustomEmpty && state.selectedCategory.equals(parentCategory, ignoreCase = true)) {
                            null
                        } else {
                            state.selectedCategory
                        }
                        val updatedAddCategory = if (isCustomEmpty && state.addSkillCategory.equals(parentCategory, ignoreCase = true)) {
                            defaultTabs.first()
                        } else {
                            state.addSkillCategory
                        }

                        state.copy(
                            skills           = remainingSkills,
                            customCategories = updatedCustomCategories,
                            categories       = updatedCategories,
                            selectedCategory = updatedSelectedCategory,
                            addSkillCategory = updatedAddCategory,
                            successMessage   = if (isCustomEmpty) {
                                "'$name' removed and empty category '$parentCategory' deleted."
                            } else {
                                "'$name' removed."
                            },
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Remove failed: ${e.message}") }
                }
        }
    }

    fun removeCustomCategory(category: String) {
        val defaultTabs = listOf("AI & Agents", "Automation & Web", "Backend & Data")
        if (defaultTabs.any { it.equals(category, ignoreCase = true) }) return
        viewModelScope.launch {
            val skillsInCategory = _uiState.value.skills.filter { it.category.equals(category, ignoreCase = true) }
            skillsInCategory.forEach { skill ->
                removeSkill.invoke(skill.name)
            }
            _uiState.update { state ->
                val remainingSkills = state.skills.filterNot { it.category.equals(category, ignoreCase = true) }
                val updatedCustom = state.customCategories.filterNot { it.equals(category, ignoreCase = true) }
                val updatedCategories = (defaultTabs + updatedCustom + remainingSkills.map { it.category })
                    .distinct()
                    .filter { it != "Custom" || remainingSkills.any { s -> s.category == "Custom" } }
                    .sorted()
                state.copy(
                    skills           = remainingSkills,
                    customCategories = updatedCustom,
                    categories       = updatedCategories,
                    selectedCategory = if (state.selectedCategory.equals(category, ignoreCase = true)) null else state.selectedCategory,
                    addSkillCategory = if (state.addSkillCategory.equals(category, ignoreCase = true)) defaultTabs.first() else state.addSkillCategory,
                    successMessage   = "Custom category '$category' removed.",
                )
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Form state
    // ---------------------------------------------------------------------------

    fun updateAddSkillName(name: String)               { _uiState.update { it.copy(addSkillName = name) } }
    fun updateAddSkillCategory(category: String)       { _uiState.update { it.copy(addSkillCategory = category) } }
    fun updateCustomCategoryName(name: String)         { _uiState.update { it.copy(customCategoryName = name) } }
    fun toggleAddSkillPanel()                          { _uiState.update { it.copy(isAddSkillExpanded = !it.isAddSkillExpanded) } }

    fun addCustomCategory(category: String) {
        val trimmed = category.trim()
        if (trimmed.isBlank()) return
        _uiState.update { state ->
            val updated = if (!state.customCategories.contains(trimmed)) state.customCategories + trimmed else state.customCategories
            val allCats = (state.categories + updated).distinct().sorted()
            state.copy(
                customCategories   = updated,
                categories         = allCats,
                addSkillCategory   = trimmed,
                customCategoryName = "",
                successMessage     = "Category '$trimmed' added to filter tabs.",
            )
        }
    }

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
