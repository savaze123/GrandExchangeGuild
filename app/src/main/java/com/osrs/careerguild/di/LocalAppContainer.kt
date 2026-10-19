package com.osrs.careerguild.di

import androidx.compose.runtime.compositionLocalOf

/**
 * Compose CompositionLocal that provides [AppContainer] to any composable
 * in the tree without threading it through every parameter.
 *
 * Provided at the root in [MainActivity.setContent] via:
 * ```kotlin
 * CompositionLocalProvider(LocalAppContainer provides app.appContainer) {
 *     GuildScaffold()
 * }
 * ```
 *
 * Throws at runtime if accessed outside a [CompositionLocalProvider] —
 * a deliberate crash-fast rather than a silent null.
 */
val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("LocalAppContainer not provided. Wrap your composable tree in CompositionLocalProvider.")
}
