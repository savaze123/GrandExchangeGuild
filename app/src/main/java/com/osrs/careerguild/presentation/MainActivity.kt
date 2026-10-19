package com.osrs.careerguild.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.osrs.careerguild.CareerGuildApp
import com.osrs.careerguild.di.LocalAppContainer
import com.osrs.careerguild.presentation.theme.CareerGuildTheme
import com.osrs.careerguild.presentation.scaffold.GuildScaffold

/**
 * Single-Activity entry point for the OSRS Grand Exchange Career Guild.
 *
 * Hosts [GuildScaffold] which resolves to:
 *  - **Unfolded / Dual-Screen**: ListDetailPaneScaffold with Filament 3D hub on
 *    the left pane and Material3 Adaptive Dashboard on the right pane.
 *  - **Folded / Outer Screen**: Single-pane prioritised flow with bottom nav.
 *  - **Tabletop / Flex Mode**: 3D hub top, dashboard controls bottom.
 *
 * Node: UI_SPLIT_SCAFFOLD
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CareerGuildApp
        setContent {
            CompositionLocalProvider(LocalAppContainer provides app.appContainer) {
                CareerGuildTheme {
                    GuildScaffold()
                }
            }
        }
    }
}
