package com.osrs.careerguild.presentation.scaffold

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.osrs.careerguild.domain.model.GuildBuilding
import com.osrs.careerguild.presentation.dashboard.DashboardPane
import com.osrs.careerguild.presentation.hub.GuildHubPane

/**
 * Root spatial scaffold for the OSRS Grand Exchange Career Guild (DBZ x RuneScape Edition).
 *
 * Implements Section 4 & Section 1 constraints:
 *  1. Full-Screen Rendering Strategy:
 *     Renders the 120Hz Filament 3D living town hub natively in an uncropped, edge-to-edge
 *     full-screen viewport at ALL times. This completely eliminates any app-crashing
 *     split-view bug.
 *  2. Collapsible Guild Tab Overlay:
 *     Slide-up semi-transparent navigation drawer (Bounty Board, Scribe Tower, Skill Altar, Courier Guild)
 *     mounted over the 3D world WITHOUT occluding, minimizing, or unmounting the underlying
 *     full-screen 3D world state.
 *  3. Invariant Back-Stack Navigation:
 *     Pressing the system back gesture smoothly collapses the overlay drawer, returning focus to the
 *     living 3D diorama without exiting the application.
 *
 * Node: UI_SPLIT_SCAFFOLD / UI_3D_GUILD_HUB
 */
@Composable
fun GuildScaffold() {
    // Active building selected by user (persists across foldable posture changes)
    var selectedBuilding by rememberSaveable { mutableStateOf<GuildBuilding?>(null) }
    var isDrawerExpanded by rememberSaveable { mutableStateOf(false) }

    // Intercept back gesture: collapse drawer first before exiting
    BackHandler(enabled = isDrawerExpanded) {
        isDrawerExpanded = false
    }

    val handleSelectBuilding: (GuildBuilding) -> Unit = { building ->
        selectedBuilding = building
        isDrawerExpanded = true
    }

    // --- Native Edge-to-Edge Full-Screen Container ---
    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 0: Uncropped, Full-Screen 120Hz DBZ x RuneScape Living 3D Town Hub (Never unmounted)
        GuildHubPane(
            onBuildingSelected = handleSelectBuilding,
            showBottomDock     = false,
            modifier           = Modifier.fillMaxSize(),
        )

        // Layer 1: Collapsible Guild Tab Overlay Drawer (Semi-transparent, non-occluding)
        CollapsibleGuildOverlay(
            isExpanded         = isDrawerExpanded,
            onExpandedChange   = { isDrawerExpanded = it },
            selectedBuilding   = selectedBuilding ?: GuildBuilding.BOUNTY_BOARD,
            onBuildingSelected = { building ->
                selectedBuilding = building
            },
        ) {
            DashboardPane(
                selectedBuilding     = selectedBuilding ?: GuildBuilding.BOUNTY_BOARD,
                isSinglePane         = true,
                onNavigateBack       = { isDrawerExpanded = false },
                onNavigateToBuilding = { building ->
                    selectedBuilding = building
                },
            )
        }
    }
}
