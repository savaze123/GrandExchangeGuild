package com.osrs.careerguild.presentation.scaffold

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osrs.careerguild.domain.model.GuildBuilding
import com.osrs.careerguild.presentation.common.InfoTooltipTrigger
import kotlin.math.roundToInt

// OSRS Color Palette
private val OsrsGold       = Color(0xFFFFD700)
private val OsrsStone      = Color(0xFF3D2B1F)
private val OsrsDarkStone  = Color(0xFF2B1D14)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsTextDim    = Color(0xFF9E8A6A)
private val OsrsCyan       = Color(0xFF00E5FF)

/**
 * Collapsible Guild Tab Overlay (Node: UI_SPLIT_SCAFFOLD / UI_3D_GUILD_HUB).
 *
 * Implements Section 3 constraints:
 *  1. Non-Occluding / Non-Unmounting Overlay:
 *     The 3D Town Hub sits underneath in an uncropped edge-to-edge viewport and is
 *     NEVER unmounted or recreated during drawer transitions.
 *  2. Bottom Trigger & Drag Gestures:
 *     Users can swipe up from the bottom peek bar to expand the drawer, or swipe down
 *     on the drag header to collapse back to the 3D diorama.
 *  3. Dynamic Information Tooltips:
 *     Mounts accessible info-circle triggers `(i)` to clarify overlay mechanics and
 *     feature pipelines.
 *  4. 4 Guild Tabs:
 *     Bounty Board, Scribe Tower, Skill Altar, and Courier Guild.
 */
@Composable
fun CollapsibleGuildOverlay(
    isExpanded        : Boolean,
    onExpandedChange  : (Boolean) -> Unit,
    selectedBuilding  : GuildBuilding,
    onBuildingSelected: (GuildBuilding) -> Unit,
    modifier          : Modifier = Modifier,
    content           : @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val isUnfoldedWide = maxWidth >= 600.dp
        val screenHeightPx = constraints.maxHeight.toFloat()
        val peekHeightDp = if (isUnfoldedWide) 52.dp else 48.dp
        val peekHeightPx = with(density) { peekHeightDp.toPx() }

        // Animation progress: 0.0f = collapsed (compact bottom bar only), 1.0f = fully expanded
        val targetExpansion = if (isExpanded) 1f else 0f
        val animatedProgress by animateFloatAsState(
            targetValue = targetExpansion,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "GuildDrawerExpansion",
        )

        // Expanded drawer occupies ~88% of screen height to guarantee the top status bar & header banner are never occluded
        val expandedHeightPx = screenHeightPx * 0.88f
        val collapsedOffsetY = screenHeightPx - peekHeightPx
        val expandedOffsetY = screenHeightPx - expandedHeightPx

        val currentOffsetY = collapsedOffsetY - (collapsedOffsetY - expandedOffsetY) * animatedProgress
        val activeHeightDp = if (!isExpanded && animatedProgress < 0.05f) {
            peekHeightDp
        } else {
            with(density) { expandedHeightPx.toDp() }
        }

        // Responsive container: centered with balanced max-width when unfolded so cards never stretch awkwardly across the full dual display
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(
                    if (isUnfoldedWide) {
                        Modifier
                            .widthIn(max = if (isExpanded) 780.dp else 560.dp)
                            .fillMaxWidth(if (isExpanded) 0.92f else 0.72f)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
                .height(activeHeightDp)
                .offset { IntOffset(x = 0, y = currentOffsetY.roundToInt()) },
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = OsrsDarkStone.copy(alpha = 0.96f),
                border = BorderStroke(1.5.dp, OsrsGold),
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        1.dp,
                        OsrsDarkBorder,
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isExpanded) Modifier.navigationBarsPadding() else Modifier),
                ) {
                    // --- 1. Compact Top Drag Handle & Trigger Bar ---
                    DrawerTopDragBar(
                        isExpanded       = isExpanded,
                        onToggleExpand   = { onExpandedChange(!isExpanded) },
                        selectedBuilding = selectedBuilding,
                        onSelectBuilding = { building ->
                            onBuildingSelected(building)
                            if (!isExpanded) onExpandedChange(true)
                        },
                    )

                    // --- 2. Expanded Navigation Header & Guild Tab Strip ---
                    if (animatedProgress > 0.12f) {
                        GuildDrawerHeader(
                            selectedBuilding   = selectedBuilding,
                            onBuildingSelected = onBuildingSelected,
                            onCollapse         = { onExpandedChange(false) },
                        )

                        // --- 3. Screen Content Area (Hosted without unmounting 3D world) ---
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top drag handle pill and compact collapsed trigger bar.
 */
@Composable
private fun DrawerTopDragBar(
    isExpanded      : Boolean,
    onToggleExpand  : () -> Unit,
    selectedBuilding: GuildBuilding,
    onSelectBuilding: (GuildBuilding) -> Unit,
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .padding(top = 5.dp, bottom = 3.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Gold Pill Drag Indicator
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(5.dp)
                .background(OsrsGold.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
                .pointerInput(isExpanded) {
                    detectVerticalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onDragEnd = {
                            if (dragAccumulator < -20f && !isExpanded) {
                                onToggleExpand()
                            } else if (dragAccumulator > 20f && isExpanded) {
                                onToggleExpand()
                            }
                            dragAccumulator = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulator += dragAmount
                        },
                    )
                },
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (isExpanded) "▼ Guild Drawer (Tap to Collapse)" else "▲ Guild Drawer",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                ),
                color = OsrsGold,
                modifier = Modifier.padding(vertical = 2.dp),
            )

            if (!isExpanded) {
                Text(
                    text = "Tap to Expand · 4 Modules ⚔",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = OsrsCyan,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Top Tab Bar rendered inside the expanded drawer.
 */
@Composable
private fun GuildDrawerHeader(
    selectedBuilding  : GuildBuilding,
    onBuildingSelected: (GuildBuilding) -> Unit,
    onCollapse        : () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "⚔ GUILD MODULES",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = OsrsGold,
                )
                InfoTooltipTrigger(
                    title = "Guild Modules",
                    explanation = "Select any of the 4 Guild Modules to forge ATS resumes, track live tech bounties, inspect your skill matrix, or dispatch applications with automated ledger telemetry.",
                )
            }
            Surface(
                onClick = onCollapse,
                shape = RoundedCornerShape(6.dp),
                color = OsrsStone,
                border = BorderStroke(1.dp, OsrsGold),
            ) {
                Text(
                    text = "▼ 3D Hub",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = OsrsGold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // Tab Row: 4 Guild Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GuildBuilding.entries.forEach { building ->
                val isSelected = building == selectedBuilding
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) OsrsBrown else OsrsStone.copy(alpha = 0.85f),
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) OsrsGold else OsrsDarkBorder,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onBuildingSelected(building) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = when (building) {
                                GuildBuilding.BOUNTY_BOARD  -> "📋 Bounty"
                                GuildBuilding.SCRIBE_TOWER  -> "📜 Scribe"
                                GuildBuilding.SKILL_ALTAR   -> "⚗ Skill"
                                GuildBuilding.COURIER_GUILD -> "📬 Courier"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) OsrsGold else OsrsTextDim,
                            maxLines = 1,
                        )
                        Text(
                            text = when (building) {
                                GuildBuilding.BOUNTY_BOARD  -> "Job Postings"
                                GuildBuilding.SCRIBE_TOWER  -> "Resume Forge"
                                GuildBuilding.SKILL_ALTAR   -> "Skill Matrix"
                                GuildBuilding.COURIER_GUILD -> "Auto-Apply"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (isSelected) OsrsGold.copy(alpha = 0.85f) else OsrsTextDim.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private val OsrsBrown = Color(0xFF5C3A1E)
