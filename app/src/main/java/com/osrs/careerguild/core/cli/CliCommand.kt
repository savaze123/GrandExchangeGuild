package com.osrs.careerguild.core.cli

/**
 * SAFE_CLI_ENGINE — Type-safe AST for the in-app command bar.
 *
 * Security invariants enforced by this sealed hierarchy:
 *  1. ZERO eval() or dynamic code execution — all commands are compile-time sealed.
 *  2. ZERO shell execution — commands only mutate DataStore or Room via typed actions.
 *  3. Unknown tokens are represented as [Unknown] and must be rejected by the dispatcher.
 *
 * Supported grammar:
 *   /theme <theme_id>             → mutate UI colour tokens via DataStore
 *   /skill add <name> [category]  → insert skill into DYNAMIC_SKILL_MATRIX
 *   /skill toggle <name>          → flip isActive on a skill
 *   /skill remove <name>          → delete skill from DYNAMIC_SKILL_MATRIX
 *   /help                         → display command reference
 *
 * Node: SAFE_CLI_ENGINE
 * Edges:
 *   → DYNAMIC_SKILL_MATRIX (adds_or_toggles_skills_via_safe_commands)
 *   → UI_SPLIT_SCAFFOLD    (mutates_ui_tokens_without_code_execution)
 */
sealed interface CliCommand {

    /** `/theme <themeId>` — updates DataStore UI tokens; no code execution. */
    data class SetTheme(val themeId: String) : CliCommand

    /** Skill sub-commands targeting DYNAMIC_SKILL_MATRIX. */
    sealed interface SkillCommand : CliCommand {
        /** `/skill add <name> [category]` */
        data class Add(val name: String, val category: String = "General") : SkillCommand
        /** `/skill toggle <name>` */
        data class Toggle(val name: String) : SkillCommand
        /** `/skill remove <name>` */
        data class Remove(val name: String) : SkillCommand
    }

    /** `/help` — returns the command reference string to the UI. */
    data object Help : CliCommand

    /**
     * Represents an unrecognised or malformed command token.
     * The dispatcher MUST surface a typed error to the user and take no action.
     */
    data class Unknown(val raw: String) : CliCommand
}
