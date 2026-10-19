package com.osrs.careerguild.core.cli

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.osrs.careerguild.domain.usecase.skillmatrix.AddSkillUseCase
import com.osrs.careerguild.domain.usecase.skillmatrix.RemoveSkillUseCase
import com.osrs.careerguild.domain.usecase.skillmatrix.ToggleSkillUseCase

/**
 * SAFE_CLI_ENGINE dispatcher — routes parsed [CliCommand] nodes to the
 * appropriate typed Kotlin actions.
 *
 * Security invariants (GEMINI.md §2):
 *  - No `eval()`, no reflection, no shell execution.
 *  - Only exhaustive `when` over the sealed [CliCommand] hierarchy.
 *  - Unknown tokens return [CliResult.UnknownCommand] — no silent action.
 *  - All mutations go through validated use-cases that enforce Room
 *    parameterised queries and length limits.
 *
 * Node: SAFE_CLI_ENGINE
 * Edges:
 *   → DYNAMIC_SKILL_MATRIX (adds_or_toggles_skills_via_safe_commands)
 *   → UI_SPLIT_SCAFFOLD    (mutates_ui_tokens_without_code_execution)
 */
class CliDispatcher(
    private val addSkill    : AddSkillUseCase,
    private val toggleSkill : ToggleSkillUseCase,
    private val removeSkill : RemoveSkillUseCase,
    private val dataStore   : DataStore<Preferences>,
) {

    /**
     * Dispatch a pre-parsed [CliCommand] to the correct typed handler.
     * Callers pass the output of [CliParser.parse]; raw strings never enter here.
     */
    suspend fun dispatch(command: CliCommand): CliResult = when (command) {

        is CliCommand.SetTheme -> handleSetTheme(command.themeId)

        is CliCommand.SkillCommand.Add ->
            addSkill(command.name, command.category).fold(
                onSuccess = { CliResult.Success("Skill '${command.name}' added to your Altar.") },
                onFailure = { CliResult.Failure(it.message ?: "Failed to add skill.") },
            )

        is CliCommand.SkillCommand.Toggle ->
            toggleSkill(command.name).fold(
                onSuccess = { CliResult.Success("Skill '${command.name}' toggled.") },
                onFailure = { CliResult.Failure(it.message ?: "Failed to toggle skill.") },
            )

        is CliCommand.SkillCommand.Remove ->
            removeSkill(command.name).fold(
                onSuccess = { CliResult.Success("Skill '${command.name}' removed from Altar.") },
                onFailure = { CliResult.Failure(it.message ?: "Failed to remove skill.") },
            )

        is CliCommand.Help -> CliResult.Success(HELP_TEXT)

        is CliCommand.Unknown -> CliResult.UnknownCommand(command.raw)
    }

    // ---------------------------------------------------------------------------
    // Theme token mutation (DataStore — zero code execution)
    // ---------------------------------------------------------------------------

    private suspend fun handleSetTheme(themeId: String): CliResult {
        val allowed = setOf("varrock_dark", "varrock_light", "ge_dark", "ge_light", "lumbridge", "falador")
        if (themeId !in allowed) {
            return CliResult.Failure("Unknown theme '$themeId'. Available: ${allowed.joinToString()}")
        }
        dataStore.edit { prefs -> prefs[PREF_THEME_ID] = themeId }
        return CliResult.Success("Theme set to '$themeId'.")
    }

    companion object {
        val PREF_THEME_ID: Preferences.Key<String> = stringPreferencesKey("active_theme_id")

        val HELP_TEXT: String = """
            ⚔ Grand Exchange Career Guild — CLI Commands
            ─────────────────────────────────────────────
            /skill add <name> [category]   Add a skill to the Altar
            /skill toggle <name>           Toggle a skill active/inactive
            /skill remove <name>           Remove a skill from the Altar
            /theme <theme_id>              Switch UI theme
                                           (varrock_dark | varrock_light | ge_dark | ge_light | lumbridge | falador)
            /help                          Show this command reference
        """.trimIndent()
    }
}
