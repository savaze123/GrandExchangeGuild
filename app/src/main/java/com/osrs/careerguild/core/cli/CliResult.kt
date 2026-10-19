package com.osrs.careerguild.core.cli

/**
 * Result type returned by [CliDispatcher] after executing a [CliCommand].
 *
 * Sealed to force callers to handle all outcomes — no silent failures.
 */
sealed interface CliResult {
    /** Command executed successfully. */
    data class Success(val message: String) : CliResult
    /** Command was well-formed but encountered a runtime error (e.g. DB write fail). */
    data class Failure(val reason: String)  : CliResult
    /** Command token was not recognised — display help hint to the user. */
    data class UnknownCommand(val raw: String) : CliResult
}
