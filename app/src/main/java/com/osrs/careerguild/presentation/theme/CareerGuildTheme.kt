package com.osrs.careerguild.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// OSRS-inspired colour palette (Varrock Stone / Grand Exchange Gold / Rune Blue)
// Tokens are mutated at runtime by SAFE_CLI_ENGINE via DataStore.
// ---------------------------------------------------------------------------

private val OsrsGold   = Color(0xFFD4AA00)
private val RuneBlue   = Color(0xFF3A6EA5)
private val VarrockGrey = Color(0xFF2B2B2B)
private val ParchmentWhite = Color(0xFFF5F0E8)

private val DarkColorScheme = darkColorScheme(
    primary          = OsrsGold,
    secondary        = RuneBlue,
    background       = VarrockGrey,
    surface          = Color(0xFF1E1E1E),
    onPrimary        = Color.Black,
    onSecondary      = Color.White,
    onBackground     = ParchmentWhite,
    onSurface        = ParchmentWhite,
)

private val LightColorScheme = lightColorScheme(
    primary          = OsrsGold,
    secondary        = RuneBlue,
    background       = ParchmentWhite,
    surface          = Color(0xFFF0EAD6),
    onPrimary        = Color.Black,
    onSecondary      = Color.White,
    onBackground     = VarrockGrey,
    onSurface        = VarrockGrey,
)

/**
 * Wraps [MaterialTheme] with OSRS-flavoured colour tokens.
 *
 * Theme is selected from DataStore (SAFE_CLI_ENGINE `/theme <id>` command)
 * and defaults to the system dark-mode preference.
 *
 * Node: SAFE_CLI_ENGINE → UI_SPLIT_SCAFFOLD (mutates_ui_tokens_without_code_execution)
 *
 * @param darkTheme Whether to apply the dark OSRS colour scheme.
 * @param content   Composable content to theme.
 */
@Composable
fun CareerGuildTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content,
    )
}
