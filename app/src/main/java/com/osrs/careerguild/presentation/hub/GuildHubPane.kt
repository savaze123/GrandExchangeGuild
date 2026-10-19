package com.osrs.careerguild.presentation.hub

import android.view.Choreographer
import android.view.SurfaceView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.osrs.careerguild.domain.model.GuildBuilding
import java.nio.ByteBuffer
import java.nio.ByteOrder

// OSRS Color Palette
private val OsrsGold       = Color(0xFFFFD700)
private val OsrsStone      = Color(0xFF3D2B1F)
private val OsrsDarkStone  = Color(0xFF2B1D14)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsTextDim    = Color(0xFF9E8A6A)

/**
 * Left pane of [GuildScaffold]: 120Hz Filament 3D Virtual Playground Environment
 * for the OSRS Grand Exchange Career Guild (Node: UI_3D_GUILD_HUB).
 *
 * Features:
 *  1. Header banner for 'Grand Exchange Career Build' lowered safely below
 *     the system status bar / foldable notch with visible OSRS gold borders.
 *  2. Full 3D virtual playground environment powered by Google Filament (glTF/GLB).
 *     Supports 120Hz rendering on Tensor G5, real-time lighting, and touch gestures
 *     (orbit, pan, zoom) to explore the Grand Exchange grounds.
 *  3. Interactive guild building dock with visible stone/gold borders allowing
 *     one-tap navigation to the Bounty Board, Scribe Tower, Skill Altar, or Courier Guild.
 */
@Composable
fun GuildHubPane(
    onBuildingSelected: (GuildBuilding) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 1. Google Filament 3D Virtual Playground Environment
        FilamentVirtualPlayground(
            modifier = Modifier.fillMaxSize(),
        )

        // 2. Overlaid UI Controls (Header + Interactive Building Dock)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
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
        color  = OsrsDarkStone.copy(alpha = 0.92f),
        border = BorderStroke(2.dp, OsrsGold),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OsrsDarkBorder, RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "⚔ Grand Exchange Career Build",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                color = OsrsGold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "3D Virtual Playground • Google Filament 120Hz Engine",
                style = MaterialTheme.typography.bodySmall,
                color = OsrsTextDim,
            )
        }
    }
}

/**
 * 3D Virtual Playground rendered via Google Filament at 120Hz.
 */
@Composable
private fun FilamentVirtualPlayground(
    modifier: Modifier = Modifier,
) {
    var modelViewer by remember { mutableStateOf<ModelViewer?>(null) }
    val choreographer = remember { Choreographer.getInstance() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle-aware frame loop: pauses render loop when app is paused
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

                // Set OSRS dark stone skybox background
                val skybox = Skybox.Builder()
                    .color(0.12f, 0.08f, 0.05f, 1.0f)
                    .build(viewer.engine)
                viewer.scene.skybox = skybox

                // Load low-poly OSRS Grand Exchange 3D model
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
                    android.util.Log.e("FilamentHub", "Failed to load OSRS GLB asset", e)
                }

                // Interactive 3D touch manipulator (orbit, pan, zoom)
                setOnTouchListener { _, event ->
                    viewer.onTouchEvent(event)
                    true
                }

                modelViewer = viewer
            }
        },
    )
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
        color  = OsrsDarkStone.copy(alpha = 0.90f),
        border = BorderStroke(1.5.dp, OsrsGold),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OsrsDarkBorder, RoundedCornerShape(10.dp)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "🏰 Guild Buildings (Tap to Enter)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = OsrsGold,
            )
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
        colors = CardDefaults.cardColors(
            containerColor = OsrsStone,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
