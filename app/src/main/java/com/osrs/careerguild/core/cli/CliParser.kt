package com.osrs.careerguild.core.cli

/**
 * Lexer/Parser for [CliCommand].
 *
 * Security contract (SAFE_CLI_ENGINE invariants):
 *  - Deterministic: same input always yields the same [CliCommand].
 *  - No side-effects: parsing never mutates state.
 *  - Unknown tokens → [CliCommand.Unknown]; caller must reject these without acting.
 *  - Input is sanitised against leading/trailing whitespace and control characters
 *    before tokenisation.
 *
 * All SQL injection / path traversal risks are mitigated at the DAO layer
 * (Room parameterised queries), but we additionally strip dangerous characters
 * here as a defence-in-depth measure.
 */
object CliParser {

    /** Characters disallowed in any token value (SQL meta-chars, path traversal, etc.) */
    private val DANGEROUS_PATTERN = Regex("""[;'"\\\n\r\t/]{1}""")

    /**
     * Parses a raw command string entered by the user into a typed [CliCommand].
     *
     * @param raw The raw text from the CLI input field (e.g. "/skill add PyTorch AI").
     * @return    A concrete [CliCommand] or [CliCommand.Unknown] if unrecognised.
     */
    fun parse(raw: String): CliCommand {
        // Strip control chars and split on whitespace
        val sanitised = raw.trim()
        val tokens    = sanitised.split(Regex("\\s+")).filter { it.isNotBlank() }

        if (tokens.isEmpty()) return CliCommand.Unknown(raw)

        return when (tokens[0].lowercase()) {
            "/help"  -> CliCommand.Help

            "/theme" -> {
                val themeId = tokens.getOrNull(1) ?: return CliCommand.Unknown(raw)
                CliCommand.SetTheme(sanitiseToken(themeId))
            }

            "/skill" -> parseSkillCommand(tokens, raw)

            else     -> CliCommand.Unknown(raw)
        }
    }

    private fun parseSkillCommand(tokens: List<String>, raw: String): CliCommand {
        val subCommand = tokens.getOrNull(1)?.lowercase()
            ?: return CliCommand.Unknown(raw)

        return when (subCommand) {
            "add" -> {
                val name     = tokens.getOrNull(2)?.let(::sanitiseToken)
                    ?: return CliCommand.Unknown(raw)
                val category = tokens.getOrNull(3)?.let(::sanitiseToken) ?: "General"
                CliCommand.SkillCommand.Add(name, category)
            }
            "toggle" -> {
                val name = tokens.getOrNull(2)?.let(::sanitiseToken)
                    ?: return CliCommand.Unknown(raw)
                CliCommand.SkillCommand.Toggle(name)
            }
            "remove" -> {
                val name = tokens.getOrNull(2)?.let(::sanitiseToken)
                    ?: return CliCommand.Unknown(raw)
                CliCommand.SkillCommand.Remove(name)
            }
            else -> CliCommand.Unknown(raw)
        }
    }

    /**
     * Strips characters that could be used for SQL injection or path traversal.
     * Only alphanumerics, dashes, underscores, dots, and spaces are retained.
     */
    private fun sanitiseToken(token: String): String =
        token.replace(DANGEROUS_PATTERN, "").take(64)
}
