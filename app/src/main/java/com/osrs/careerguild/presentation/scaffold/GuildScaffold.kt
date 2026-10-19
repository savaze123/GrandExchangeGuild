package com.osrs.careerguild.presentation.scaffold

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
 * Uses [ListDetailPaneScaffold] (Material3 Adaptive) to implement the
 * dual-screen split layout for the Google Pixel 10 Pro Fold:
 *
 *  ┌────────────────────┬────────────────────────────┐
 *  │  UI_3D_GUILD_HUB   │  Dashboard / Detail Pane   │
 *  │  (Filament 120Hz)  │  (Job Radar, Skill Tree,   │
 *  │  Left / List Pane  │   Resume Forge, CLI Bar)    │
 *  └────────────────────┴────────────────────────────┘
 *
 * On folded/outer-screen the scaffold collapses to whichever pane is active.
 *
 * Node: UI_SPLIT_SCAFFOLD
 * Edge: UI_3D_GUILD_HUB → UI_SPLIT_SCAFFOLD (opens_job_detail_and_resume_forge_on_right_pane)
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun GuildScaffold() {
    val navigator = rememberListDetailPaneScaffoldNavigator<GuildBuilding>()

    // Track which building the user last tapped in the 3D hub so the
    // detail pane can render the correct sub-screen.
    var selectedBuilding by remember { mutableStateOf<GuildBuilding?>(null) }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value     = navigator.scaffoldValue,
        listPane  = {
            // Left pane: 120Hz Filament 3D OSRS Town Hub (UI_3D_GUILD_HUB)
            GuildHubPane(
                onBuildingSelected = { building ->
                    selectedBuilding = building
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, building)
                },
            )
        },
        detailPane = {
            // Right pane: Material3 Adaptive Dashboard (job radar, skill tree, etc.)
            DashboardPane(selectedBuilding = selectedBuilding)
        },
    )
}
