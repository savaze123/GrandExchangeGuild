package com.osrs.careerguild.presentation.scaffold

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osrs.careerguild.domain.model.GuildBuilding
import com.osrs.careerguild.presentation.dashboard.DashboardPane
import com.osrs.careerguild.presentation.hub.GuildHubPane

private val OsrsGold      = Color(0xFFFFD700)
private val OsrsStone     = Color(0xFF3D2B1F)
private val OsrsDarkStone = Color(0xFF2B1D14)

enum class ScaffoldLayoutMode {
    FULL_3D_OVERLAY,
    SPLIT_PANE,
}

/**
 * Root scaffold for the OSRS Grand Exchange Career Guild.
 *
 * Implements Section 3 constraints:
 *  1. Full-Screen 3D Town Hub:
 *     Renders the 120Hz Filament 3D town hub natively in an uncropped, edge-to-edge viewport.
 *  2. Collapsible Guild Tab Overlay:
 *     Slide-up semi-transparent navigation drawer (Bounty Board, Scribe Tower, Skill Altar, Courier Guild)
 *     mounted over the 3D world WITHOUT occluding or unmounting the underlying 3D engine state.
 *  3. Adaptive Flexibility:
 *     Defaults to Full-Screen 3D Town Hub with Collapsible Guild Tab Overlay. An OSRS layout switcher
 *     allows toggling to side-by-side Split-Pane mode on unfolded dual-screen foldables.
 *
 * Node: UI_SPLIT_SCAFFOLD
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun GuildScaffold() {
    val navigator = rememberListDetailPaneScaffoldNavigator<GuildBuilding>()
    val scaffoldValue = navigator.scaffoldValue

    // Detect if physical screen is wide enough for dual-pane
    val canSupportSplit = scaffoldValue[ListDetailPaneScaffoldRole.List] != PaneAdaptedValue.Hidden &&
                          scaffoldValue[ListDetailPaneScaffoldRole.Detail] != PaneAdaptedValue.Hidden

    var layoutMode by remember { mutableStateOf(ScaffoldLayoutMode.FULL_3D_OVERLAY) }
    var selectedBuilding by remember { mutableStateOf<GuildBuilding?>(null) }
    var isDrawerExpanded by remember { mutableStateOf(false) }

    // Intercept back gesture: collapse drawer first before exiting
    BackHandler(enabled = layoutMode == ScaffoldLayoutMode.FULL_3D_OVERLAY && isDrawerExpanded) {
        isDrawerExpanded = false
    }

    val handleSelectBuilding: (GuildBuilding) -> Unit = { building ->
        selectedBuilding = building
        if (layoutMode == ScaffoldLayoutMode.FULL_3D_OVERLAY) {
            isDrawerExpanded = true
        } else {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, building)
        }
    }

    if (layoutMode == ScaffoldLayoutMode.FULL_3D_OVERLAY || !canSupportSplit) {
        // --- Full-Screen 3D Town Hub + Collapsible Guild Tab Overlay ---
        Box(modifier = Modifier.fillMaxSize()) {
            // Layer 0: Uncropped, Edge-to-Edge 120Hz 3D Town Hub (Never unmounted)
            GuildHubPane(
                onBuildingSelected = handleSelectBuilding,
                showBottomDock     = false,
                modifier           = Modifier.fillMaxSize(),
            )

            // Optional Split View Toggle Button (Top-Right, visible when screen supports split)
            if (canSupportSplit) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = { layoutMode = ScaffoldLayoutMode.SPLIT_PANE },
                        shape   = RoundedCornerShape(8.dp),
                        border  = BorderStroke(1.5.dp, OsrsGold),
                        colors  = ButtonDefaults.outlinedButtonColors(containerColor = OsrsDarkStone.copy(alpha = 0.9f)),
                    ) {
                        Text(
                            text  = "◫ Split View",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = OsrsGold,
                        )
                    }
                }
            }

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
    } else {
        // --- Dual-Screen Split-Pane Posture (Pixel 10 Pro Fold Unfolded) ---
        Box(modifier = Modifier.fillMaxSize()) {
            ListDetailPaneScaffold(
                directive = navigator.scaffoldDirective,
                value     = navigator.scaffoldValue,
                listPane  = {
                    // Left pane: Full 120Hz Filament 3D Town Hub
                    GuildHubPane(
                        onBuildingSelected = handleSelectBuilding,
                        showBottomDock     = true,
                    )
                },
                detailPane = {
                    // Right pane: Material3 Adaptive Dashboard
                    DashboardPane(
                        selectedBuilding     = selectedBuilding ?: GuildBuilding.BOUNTY_BOARD,
                        isSinglePane         = false,
                        onNavigateBack       = { selectedBuilding = null },
                        onNavigateToBuilding = handleSelectBuilding,
                    )
                },
            )

            // Switch back to Full 3D Overlay View
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { layoutMode = ScaffoldLayoutMode.FULL_3D_OVERLAY },
                    shape   = RoundedCornerShape(8.dp),
                    border  = BorderStroke(1.5.dp, OsrsGold),
                    colors  = ButtonDefaults.outlinedButtonColors(containerColor = OsrsDarkStone.copy(alpha = 0.9f)),
                ) {
                    Text(
                        text  = "⚔ Full 3D View",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = OsrsGold,
                    )
                }
            }
        }
    }
}
