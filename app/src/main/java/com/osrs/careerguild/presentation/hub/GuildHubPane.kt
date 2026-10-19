package com.osrs.careerguild.presentation.hub

import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.osrs.careerguild.domain.model.GuildBuilding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

// OSRS Color Palette
private val OsrsGold       = Color(0xFFFFD700)
private val OsrsStone      = Color(0xFF3D2B1F)
private val OsrsDarkStone  = Color(0xFF2B1D14)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsTextDim    = Color(0xFF9E8A6A)
private val OsrsCyan       = Color(0xFF00E5FF)

/**
 * Left pane of [GuildScaffold]: 120Hz Filament 3D Living Town Hub (Node: UI_3D_GUILD_HUB).
 *
 * Features:
 *  1. Lowered Header: 'Grand Exchange Career Build' lowered below status bar with dual-layer OSRS gold border.
 *  2. 2.5D Isometric Diorama Camera: Pitch locked at ~45 degrees, smooth horizontal yaw orbit and clamped zoom.
 *  3. Interactive 3D Town Buildings: Floating 2.5D nameplates over the 4 guild buildings (Bounty Board,
 *     Scribe Tower, Skill Altar, Courier Guild) with direct-tap routing.
 *  4. Quick-travel bottom dock for single-tap navigation in any posture.
 */
@Composable
fun GuildHubPane(
    onBuildingSelected: (GuildBuilding) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Isometric camera parameters
    var cameraYaw by remember { mutableDoubleStateOf(45.0) }
    var cameraZoom by remember { mutableDoubleStateOf(15.0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 1. Google Filament 3D Virtual Town Environment
        FilamentLivingTown(
            cameraYaw = cameraYaw,
            cameraZoom = cameraZoom,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Horizontal drag = smooth orbital yaw rotation around the plaza
                        cameraYaw += dragAmount.x * 0.35
                        // Vertical drag = smooth clamped zoom (pitch locked at 45° diorama angle)
                        cameraZoom = (cameraZoom - dragAmount.y * 0.03).coerceIn(8.5, 22.0)
                    }
                },
        )

        // 2. Floating 2.5D Interactive Building Nameplates across the 3D diorama
        FloatingBuildingNameplates(
            onBuildingSelected = onBuildingSelected,
            modifier = Modifier.fillMaxSize(),
        )

        // 3. Overlaid UI Controls (Lowered Header + Bottom Quick-Travel Dock)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Lowered Header with visible borders
            GuildHeaderBanner()

            // Bottom Building Navigation Dock
            GuildBuildingDock(
                onBuildingSelected = onBuildingSelected,
            )
        }
    }
}

/**
 * Header banner displaying 'Grand Exchange Career Build', lowered below status bar
 * with visible dual-layer OSRS gold and dark borders.
 */
@Composable
private fun GuildHeaderBanner() {
    Surface(
        shape  = RoundedCornerShape(8.dp),
        color  = OsrsDarkStone.copy(alpha = 0.94f),
        border = BorderStroke(2.dp, OsrsGold),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OsrsDarkBorder, RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = "⚔ Grand Exchange Career Build",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                color = OsrsGold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Living 3D Town Hub • 120Hz Filament Engine • Drag to Orbit",
                style = MaterialTheme.typography.labelSmall,
                color = OsrsTextDim,
            )
        }
    }
}

/**
 * 3D Living Town Environment rendered via Google Filament at 120Hz.
 */
@Composable
private fun FilamentLivingTown(
    cameraYaw: Double,
    cameraZoom: Double,
    modifier: Modifier = Modifier,
) {
    var modelViewer by remember { mutableStateOf<ModelViewer?>(null) }
    val choreographer = remember { Choreographer.getInstance() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Update isometric camera angle whenever yaw/zoom changes
    androidx.compose.runtime.LaunchedEffect(modelViewer, cameraYaw, cameraZoom) {
        val viewer = modelViewer ?: return@LaunchedEffect
        val rad = Math.toRadians(cameraYaw)
        // 45-degree isometric projection looking down at the Grand Exchange central plaza (0, 0.4, 0)
        val eyeX = cameraZoom * cos(rad) * 0.707
        val eyeY = cameraZoom * 0.707
        val eyeZ = cameraZoom * sin(rad) * 0.707
        viewer.camera.lookAt(eyeX, eyeY, eyeZ, 0.0, 0.4, 0.0, 0.0, 1.0, 0.0)
    }

    // Lifecycle-aware 120Hz frame render loop
    DisposableEffect(lifecycleOwner, modelViewer) {
        val viewer = modelViewer ?: return@DisposableEffect onDispose {}
        var isRendering = true

        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (isRendering) {
                    choreographer.postFrameCallback(this)
                    viewer.render(frameTimeNanos)
                }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (!isRendering) {
                        isRendering = true
                        choreographer.postFrameCallback(frameCallback)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isRendering = false
                    choreographer.removeFrameCallback(frameCallback)
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        choreographer.postFrameCallback(frameCallback)

        onDispose {
            isRendering = false
            choreographer.removeFrameCallback(frameCallback)
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                viewer.destroyModel()
            } catch (_: Throwable) {}
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                val viewer = ModelViewer(surfaceView = this)

                // Dark OSRS stone skybox
                val skybox = Skybox.Builder()
                    .color(0.10f, 0.07f, 0.05f, 1.0f)
                    .build(viewer.engine)
                viewer.scene.skybox = skybox

                // Load upgraded OSRS living town GLB
                try {
                    context.assets.open("models/osrs_guild.glb").use { input ->
                        val bytes = input.readBytes()
                        val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                            order(ByteOrder.nativeOrder())
                            put(bytes)
                            rewind()
                        }
                        viewer.loadModelGlb(buffer)
                        viewer.transformToUnitCube()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FilamentHub", "Failed to load OSRS town GLB", e)
                }

                // Initial 45° isometric diorama camera
                val rad = Math.toRadians(45.0)
                val eyeX = 15.0 * cos(rad) * 0.707
                val eyeY = 15.0 * 0.707
                val eyeZ = 15.0 * sin(rad) * 0.707
                viewer.camera.lookAt(eyeX, eyeY, eyeZ, 0.0, 0.4, 0.0, 0.0, 1.0, 0.0)

                modelViewer = viewer
            }
        },
    )
}

/**
 * Floating 2.5D building nameplates rendered directly in the diorama screen space.
 */
@Composable
private fun FloatingBuildingNameplates(
    onBuildingSelected: (GuildBuilding) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(horizontal = 16.dp, vertical = 72.dp)) {
        // North-West: Bounty Board (Job Radar)
        BuildingFloatingBadge(
            building = GuildBuilding.BOUNTY_BOARD,
            icon = "📋",
            title = "Bounty Board",
            subtitle = "Live Jobs",
            badgeColor = OsrsGold,
            onClick = { onBuildingSelected(GuildBuilding.BOUNTY_BOARD) },
            modifier = Modifier.align(Alignment.TopStart).padding(top = 28.dp),
        )

        // North-East: Scribe Tower (Resume Forge)
        BuildingFloatingBadge(
            building = GuildBuilding.SCRIBE_TOWER,
            icon = "📜",
            title = "Scribe Tower",
            subtitle = "ATS Resume",
            badgeColor = Color(0xFF81C784),
            onClick = { onBuildingSelected(GuildBuilding.SCRIBE_TOWER) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp),
        )

        // South-West: Skill Altar (Skill Matrix)
        BuildingFloatingBadge(
            building = GuildBuilding.SKILL_ALTAR,
            icon = "⚗",
            title = "Skill Altar",
            subtitle = "Skill Tree",
            badgeColor = OsrsCyan,
            onClick = { onBuildingSelected(GuildBuilding.SKILL_ALTAR) },
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 76.dp),
        )

        // South-East: Courier Guild (Auto-Apply)
        BuildingFloatingBadge(
            building = GuildBuilding.COURIER_GUILD,
            icon = "📬",
            title = "Courier Guild",
            subtitle = "Auto-Apply",
            badgeColor = Color(0xFFBA68C8),
            onClick = { onBuildingSelected(GuildBuilding.COURIER_GUILD) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 76.dp),
        )
    }
}

@Composable
private fun BuildingFloatingBadge(
    building: GuildBuilding,
    icon: String,
    title: String,
    subtitle: String,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = OsrsDarkStone.copy(alpha = 0.92f),
        border = BorderStroke(1.5.dp, badgeColor),
        modifier = modifier
            .clickable(onClick = onClick)
            .border(1.dp, OsrsDarkBorder, RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = icon, fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = badgeColor,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = OsrsTextDim,
                )
            }
        }
    }
}

/**
 * Bottom dock displaying interactive building portals with visible OSRS borders.
 */
@Composable
private fun GuildBuildingDock(
    onBuildingSelected: (GuildBuilding) -> Unit,
) {
    Surface(
        shape  = RoundedCornerShape(10.dp),
        color  = OsrsDarkStone.copy(alpha = 0.94f),
        border = BorderStroke(1.5.dp, OsrsGold),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OsrsDarkBorder, RoundedCornerShape(10.dp)),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🏰 Guild Buildings (Tap to Enter)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = OsrsGold,
                )
                Text(
                    text = "Grand Exchange",
                    style = MaterialTheme.typography.labelSmall,
                    color = OsrsTextDim,
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(GuildBuilding.entries) { building ->
                    BuildingDockCard(
                        building           = building,
                        onBuildingSelected = onBuildingSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildingDockCard(
    building: GuildBuilding,
    onBuildingSelected: (GuildBuilding) -> Unit,
) {
    Card(
        modifier = Modifier
            .clickable { onBuildingSelected(building) }
            .border(1.dp, OsrsGold.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = OsrsStone),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text  = building.displayName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = OsrsGold,
            )
            Text(
                text  = when (building) {
                    GuildBuilding.BOUNTY_BOARD  -> "Job Radar"
                    GuildBuilding.SCRIBE_TOWER  -> "Resume Forge"
                    GuildBuilding.SKILL_ALTAR   -> "Skill Tree"
                    GuildBuilding.COURIER_GUILD -> "Auto-Apply"
                },
                style = MaterialTheme.typography.bodySmall,
                color = OsrsTextDim,
            )
        }
    }
}
