package com.osrs.careerguild.presentation.scaffold

import androidx.activity.compose.BackHandler
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.osrs.careerguild.domain.model.GuildBuilding
import com.osrs.careerguild.presentation.dashboard.DashboardPane
import com.osrs.careerguild.presentation.hub.GuildHubPane

/**
 * Root scaffold for the OSRS Grand Exchange Career Guild.
 *
 * Uses [ListDetailPaneScaffold] (Material3 Adaptive) to support the Google Pixel 10 Pro Fold:
 *
 *  ┌────────────────────┬────────────────────────────┐
 *  │  UI_3D_GUILD_HUB   │  Dashboard / Detail Pane   │
 *  │  (Filament 120Hz)  │  (Bounty Board, Skill Tree,│
 *  │  Left / List Pane  │   Scribe Tower, Courier)   │
 *  └────────────────────┴────────────────────────────┘
 *
 * Folded Single-Screen Mode:
 *  - Displays 3D Town Hub as the root screen.
 *  - Tapping any building navigates into that detail screen.
 *  - System Back button/gesture or top-left "← Back to Guild" button safely
 *    returns back to the 3D Town Hub rather than closing the app.
 *
 * Node: UI_SPLIT_SCAFFOLD
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun GuildScaffold() {
    val navigator = rememberListDetailPaneScaffoldNavigator<GuildBuilding>()
    val scaffoldValue = navigator.scaffoldValue

    // Detect if folded / single-pane (either List or Detail is Hidden)
    val isSinglePane = scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden ||
                       scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Hidden

    // Active building selected by user
    var selectedBuilding by remember { mutableStateOf<GuildBuilding?>(null) }

    val handleNavigateBack: () -> Unit = {
        selectedBuilding = null
        if (navigator.canNavigateBack()) {
            navigator.navigateBack()
        }
    }

    // Task 1: Explicit BackHandler for single-screen mode
    BackHandler(enabled = isSinglePane && (navigator.canNavigateBack() || selectedBuilding != null)) {
        handleNavigateBack()
    }

    val handleSelectBuilding: (GuildBuilding) -> Unit = { building ->
        selectedBuilding = building
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, building)
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value     = navigator.scaffoldValue,
        listPane  = {
            // Left pane: 120Hz Filament 3D OSRS Living Town Hub
            GuildHubPane(
                onBuildingSelected = handleSelectBuilding,
            )
        },
        detailPane = {
            // Right pane: Material3 Adaptive Dashboard
            DashboardPane(
                selectedBuilding     = selectedBuilding,
                isSinglePane         = isSinglePane,
                onNavigateBack       = handleNavigateBack,
                onNavigateToBuilding = handleSelectBuilding,
            )
        },
    )
}
