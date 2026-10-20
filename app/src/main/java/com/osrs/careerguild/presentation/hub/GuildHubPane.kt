package com.osrs.careerguild.presentation.hub

import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import com.osrs.careerguild.core.profile.UserProfile
import com.osrs.careerguild.core.profile.UserProfileManager
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.ModelViewer
import com.osrs.careerguild.domain.model.GuildBuilding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// OSRS Color Palette
private val OsrsGold       = Color(0xFFFFD700)
private val OsrsStone      = Color(0xFF3D2B1F)
private val OsrsDarkStone  = Color(0xFF2B1D14)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsTextDim    = Color(0xFF9E8A6A)
private val OsrsCyan       = Color(0xFF00E5FF)
private val DbzOrange      = Color(0xFFFF6D00)

/**
 * Left pane of [GuildScaffold]: 120Hz Filament 3D Living Town Hub (Node: UI_3D_GUILD_HUB).
 *
 * Implements Tasks 1 & 2:
 *  1. Mr. Popo's Lookout Sky Citadel: Cylindrical white marble platform deck, spherical half-bottom,
 *     central spire descending into the sky, surrounded by static blue & white clouds.
 *  2. Locked-Perspective 360° Camera: User horizontal swipe smoothly orbits around the Lookout while
 *     elevation pitch is locked at ~38°, permanently eliminating erratic swiveling.
 *  3. In-World Direct 3D Navigation: Tapping on 3D structures navigates directly to their modules.
 *  4. Clean UI: Flat 2D redundant button overlays removed; in-world 3D signs identify buildings.
 *  5. Animated Flying Nimbus & Easter Egg: Nimbus circles the town at 120Hz; tapping it triggers
 *     the hidden Easter egg.
 */
/**
 * Character speakers in the sky diorama.
 */
enum class CharacterSpeaker {
    GOKU,
    VEGETA,
    BEERUS,
}

/**
 * Lightweight 3D in-world dialogue bubble positioned above a character.
 */
data class FloatingCharacterDialogue(
    val character: CharacterSpeaker,
    val speakerName: String,
    val icon: String,
    val text: String,
    val screenX: Float,
    val screenY: Float,
    val borderColor: Color,
    val titleColor: Color,
)

/**
 * Interactive Camera Controller managing 360° orbit yaw, elevation perspective pitch, and dynamic zoom distance.
 */
class CameraController(
    initialYaw: Float = (Math.PI * 0.25).toFloat(),
    initialPitch: Float = Math.toRadians(37.0).toFloat(),
    initialDistance: Float = 1.36f,
) {
    var yaw by mutableFloatStateOf(initialYaw)
    var pitch by mutableFloatStateOf(initialPitch)
    var distance by mutableFloatStateOf(initialDistance)

    var manipulator: Manipulator? = null
    var viewportWidth: Int = 1080
    var viewportHeight: Int = 1080

    /**
     * Dial notch zoom step.
     * When isZoomIn is true, scrolls forward to zoom in.
     * When isZoomIn is false, scrolls backward to zoom out.
     */
    fun dialZoomStep(isZoomIn: Boolean) {
        val w = viewportWidth.coerceAtLeast(100)
        val h = viewportHeight.coerceAtLeast(100)
        // Dial notch step delta
        val delta = if (isZoomIn) 18.0f else -18.0f
        android.util.Log.i("FilamentHub", "dialZoomStep: isZoomIn=$isZoomIn, delta=$delta, manip=${manipulator != null}")
        manipulator?.scroll(w / 2, h / 2, delta)
    }

    fun zoomIn() {
        val w = viewportWidth.coerceAtLeast(100)
        val h = viewportHeight.coerceAtLeast(100)
        manipulator?.scroll(w / 2, h / 2, 20.0f)
    }

    fun zoomOut() {
        val w = viewportWidth.coerceAtLeast(100)
        val h = viewportHeight.coerceAtLeast(100)
        manipulator?.scroll(w / 2, h / 2, -20.0f)
    }

    fun reset() {
        manipulator?.homeBookmark?.let {
            manipulator?.jumpToBookmark(it)
        }
    }

    fun cyclePerspective() {
        val manip = manipulator ?: return
        val w = viewportWidth.coerceAtLeast(100)
        val h = viewportHeight.coerceAtLeast(100)
        // Orbit rotate perspective by 90 degrees horizontally and slight pitch elevation
        manip.grabBegin(w / 2, h / 2, false)
        manip.grabUpdate(w / 2 + (w * 0.25f).toInt(), h / 2 - (h * 0.08f).toInt())
        manip.grabEnd()
    }
}

@Composable
fun GuildHubPane(
    onBuildingSelected: (GuildBuilding) -> Unit,
    showBottomDock: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cameraController = remember { CameraController() }
    var showSettingsModal by remember { mutableStateOf(false) }
    var billboardPositions by remember { mutableStateOf<Map<GuildBuilding, Pair<Float, Float>>>(emptyMap()) }
    var nimbusPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var characterDialogues by remember { mutableStateOf<List<FloatingCharacterDialogue>>(emptyList()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 1. Google Filament 3D Virtual Town Environment (120Hz native diorama)
        FilamentLivingTown(
            cameraController = cameraController,
            onBuildingSelected = onBuildingSelected,
            onNimbusTapped = { showSettingsModal = true },
            onUpdateBuildingBillboards = { billboardPositions = it },
            onUpdateNimbusBillboard = { nimbusPosition = it },
            onUpdateCharacterDialogues = { characterDialogues = it },
            modifier = Modifier.fillMaxSize(),
        )

        // 2. 2D Floating In-World Billboards (Always facing camera, crisp OSRS labels above 3D buildings)
        FloatingBuildingBillboards(
            billboardPositions = billboardPositions,
            nimbusPosition = nimbusPosition,
            onBuildingSelected = onBuildingSelected,
            onNimbusTapped = { showSettingsModal = true },
            modifier = Modifier.fillMaxSize(),
        )

        // 2b. 3D Floating Character Dialogue Bubbles (Manga speech bubbles tracking Goku, Vegeta & Beerus)
        FloatingCharacterDialogues(
            dialogues = characterDialogues,
            modifier = Modifier.fillMaxSize(),
        )

        // 3. Overlaid Header Banner (Clean, without redundant zoom indicator)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            GuildHeaderBanner()
        }

        // 4. Floating Quick Zoom & Perspective Toolbar (Bottom-Right, above drawer peek)
        CameraControlsToolbar(
            cameraController = cameraController,
            onOpenSettings = { showSettingsModal = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp),
        )

        // 5. Main Settings / Configuration Tab (Triggered by Kid Gohan riding the Flying Nimbus)
        if (showSettingsModal) {
            GuildSettingsDialog(
                onDismiss = { showSettingsModal = false }
            )
        }
    }
}

/**
 * 2D Floating In-World Billboards positioned directly above the 3D structures.
 * Tracks 3D rooftop points at 120Hz; always 100% perpendicular to the camera
 * and crystal-clear to read from any perspective angle as the world rotates.
 */
@Composable
private fun FloatingBuildingBillboards(
    billboardPositions: Map<GuildBuilding, Pair<Float, Float>>,
    nimbusPosition: Pair<Float, Float>? = null,
    onBuildingSelected: (GuildBuilding) -> Unit,
    onNimbusTapped: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        for ((building, pos) in billboardPositions) {
            val (screenX, screenY) = pos
            Surface(
                onClick = { onBuildingSelected(building) },
                shape = RoundedCornerShape(8.dp),
                color = OsrsDarkStone.copy(alpha = 0.94f),
                border = BorderStroke(1.5.dp, OsrsGold),
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            val posX = (screenX - placeable.width / 2f).roundToInt()
                            val posY = (screenY - placeable.height - 8.dp.toPx()).roundToInt()
                            placeable.place(posX, posY)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val (icon, name) = when (building) {
                        GuildBuilding.BOUNTY_BOARD  -> "📜" to "Bounty Board"
                        GuildBuilding.SCRIBE_TOWER  -> "🏛️" to "Scribe Tower"
                        GuildBuilding.SKILL_ALTAR   -> "✨" to "Skill Altar"
                        GuildBuilding.COURIER_GUILD -> "📦" to "Courier Guild"
                    }
                    Text(
                        text = icon,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp,
                        ),
                        color = OsrsGold,
                    )
                }
            }
        }

        // Floating interactive billboard tracking Kid Gohan riding the Flying Nimbus
        nimbusPosition?.let { (screenX, screenY) ->
            Surface(
                onClick = onNimbusTapped,
                shape = RoundedCornerShape(8.dp),
                color = OsrsDarkStone.copy(alpha = 0.94f),
                border = BorderStroke(1.5.dp, DbzOrange),
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            val posX = (screenX - placeable.width / 2f).roundToInt()
                                .coerceIn(12, (constraints.maxWidth - placeable.width - 12).coerceAtLeast(12))
                            val posY = (screenY - placeable.height - 8.dp.toPx()).roundToInt()
                                .coerceIn(180, (constraints.maxHeight - placeable.height - 100).coerceAtLeast(180))
                            placeable.place(posX, posY)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "☁️",
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "Profile Settings",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp,
                        ),
                        color = DbzOrange,
                    )
                }
            }
        }
    }
}

/**
 * 3D Floating Character Dialogue Bubbles.
 * Lightweight manga speech bubble system tracking Goku, Vegeta, and Beerus as they spar and observe.
 */
@Composable
private fun FloatingCharacterDialogues(
    dialogues: List<FloatingCharacterDialogue>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        for (dialogue in dialogues) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = OsrsDarkStone.copy(alpha = 0.95f),
                border = BorderStroke(1.5.dp, dialogue.borderColor),
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            val posX = (dialogue.screenX - placeable.width / 2f).roundToInt()
                                .coerceIn(12, (constraints.maxWidth - placeable.width - 12).coerceAtLeast(12))
                            val posY = (dialogue.screenY - placeable.height - 10.dp.toPx()).roundToInt()
                                .coerceIn(340, (constraints.maxHeight - placeable.height - 100).coerceAtLeast(340))
                            placeable.place(posX, posY)
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 210.dp)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = dialogue.icon,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = dialogue.speakerName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            ),
                            color = dialogue.titleColor,
                        )
                    }
                    Text(
                        text = dialogue.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        ),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Floating Camera Perspective & Zoom Toolbar.
 * Directive: Camera zoom speed acts like a dial (such that holding down slow-zooms),
 * where the '+' button zooms out and the '-' button zooms in.
 */
@Composable
private fun CameraControlsToolbar(
    cameraController: CameraController,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = OsrsDarkStone.copy(alpha = 0.94f),
        border = BorderStroke(2.dp, OsrsGold),
        modifier = modifier
            .border(1.dp, OsrsDarkBorder, RoundedCornerShape(14.dp)),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Directive: '+' zooms out and '-' zooms in, holding down slow-zooms like a dial
            CameraDialZoomButton(
                text = "+",
                contentDescription = "Zoom Out (Dial Slow-Zoom)",
                onDialZoomStep = { cameraController.dialZoomStep(isZoomIn = false) },
            )
            CameraDialZoomButton(
                text = "−",
                contentDescription = "Zoom In (Dial Slow-Zoom)",
                onDialZoomStep = { cameraController.dialZoomStep(isZoomIn = true) },
            )
            CameraControlButton(
                text = "🔄",
                contentDescription = "Reset Perspective",
                onClick = { cameraController.reset() },
            )
            CameraControlButton(
                text = "👁",
                contentDescription = "Cycle Perspective",
                onClick = { cameraController.cyclePerspective() },
            )
            CameraControlButton(
                text = "☁️",
                contentDescription = "Profile & Resume Settings (Kid Gohan)",
                onClick = onOpenSettings,
            )
        }
    }
}

/**
 * Dial zoom button supporting tap and press-and-hold continuous slow-zoom.
 * Directive: Camera zoom speed acts like a dial (such that holding down slow-zooms),
 * where the '+' button zooms out and the '-' button zooms in.
 */
@Composable
private fun CameraDialZoomButton(
    text: String,
    contentDescription: String,
    onDialZoomStep: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        android.util.Log.i("FilamentHub", "CameraDialZoomButton $text isPressed=$isPressed")
        if (isPressed) {
            // First notch immediately upon contact
            onDialZoomStep()
            // Delay before continuous dial slow-zoom rotation
            kotlinx.coroutines.delay(180)
            while (isPressed) {
                onDialZoomStep()
                kotlinx.coroutines.delay(60)
            }
        }
    }

    Surface(
        onClick = { /* Processed by interactionSource pressed loop */ },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10.dp),
        color = OsrsStone,
        border = BorderStroke(1.5.dp, OsrsGold.copy(alpha = 0.8f)),
        modifier = Modifier.size(46.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = text,
                color = OsrsGold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CameraControlButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = OsrsStone,
        border = BorderStroke(1.5.dp, OsrsGold.copy(alpha = 0.8f)),
        modifier = Modifier
            .size(46.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = text,
                color = OsrsGold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Header banner displaying DBZ x RuneScape Career Guild & Google OAuth2 status,
 * lowered below status bar with dual-layer Super Saiyan gold and Toriyama dark borders.
 */
@Composable
private fun GuildHeaderBanner() {
    Surface(
        shape  = RoundedCornerShape(10.dp),
        color  = OsrsDarkStone.copy(alpha = 0.94f),
        border = BorderStroke(2.dp, OsrsGold),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OsrsDarkBorder, RoundedCornerShape(10.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "⚔ Grand Exchange Career Guild",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
                    color = OsrsGold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Popo's Lookout · 120Hz Living Sky Citadel",
                    style = MaterialTheme.typography.labelSmall,
                    color = OsrsCyan,
                )
            }
            com.osrs.careerguild.presentation.auth.GoogleAuthBadge()
        }
    }
}

/**
 * 3D Living Town Environment rendered via Google Filament at 120Hz.
 *
 * Implements:
 *  - Native 120Hz continuous Choreographer frame render loop.
 *  - Cel-shaded skybox with soft cel-shaded DBZ atmosphere.
 *  - User horizontal rotation with locked elevation pitch (~38°) to prevent erratic swivels.
 *  - Direct 3D tap raycasting mapping screen coordinates to the 4 Guild structures & Flying Nimbus.
 */
@Composable
private fun FilamentLivingTown(
    cameraController: CameraController,
    onBuildingSelected: (GuildBuilding) -> Unit,
    onNimbusTapped: () -> Unit,
    onUpdateBuildingBillboards: (Map<GuildBuilding, Pair<Float, Float>>) -> Unit,
    onUpdateNimbusBillboard: (Pair<Float, Float>?) -> Unit = {},
    onUpdateCharacterDialogues: (List<FloatingCharacterDialogue>) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                val engine = Engine.create()
                val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
                // Calibrated 2.4x closer default zoom (0.85f, 0.65f, 0.85f) framing Popo's Lookout with prominent, clickable buildings
                val manipulator = Manipulator.Builder()
                    .targetPosition(0.0f, 0.0f, 0.0f)
                    .orbitHomePosition(0.85f, 0.65f, 0.85f)
                    .zoomSpeed(0.12f)
                    .orbitSpeed(0.005f, 0.005f)
                    .build(Manipulator.Mode.ORBIT)

                cameraController.manipulator = manipulator

                val viewer = ModelViewer(
                    surfaceView = this,
                    engine = engine,
                    uiHelper = uiHelper,
                    manipulator = manipulator,
                )
                tag = viewer

                // Celestial Dragon Ball Skybox (Sky Blue with warm sunlight)
                val skybox = Skybox.Builder()
                    .color(0.40f, 0.68f, 0.95f, 1.0f)
                    .build(engine)
                viewer.scene.skybox = skybox

                // Warm golden directional sunlight illuminating the Lookout
                try {
                    val sunlight = EntityManager.get().create()
                    LightManager.Builder(LightManager.Type.DIRECTIONAL)
                        .color(1.0f, 0.96f, 0.88f)
                        .intensity(120_000.0f)
                        .direction(-0.55f, -1.0f, -0.65f)
                        .castShadows(true)
                        .build(engine, sunlight)
                    viewer.scene.addEntity(sunlight)
                } catch (e: Exception) {
                    android.util.Log.w("FilamentHub", "Could not build sunlight: ${e.message}")
                }

                // Load upgraded OSRS living town GLB centered directly at origin
                try {
                    context.assets.open("models/osrs_guild.glb").use { input ->
                        val bytes = input.readBytes()
                        android.util.Log.i("FilamentHub", "Loaded models/osrs_guild.glb (${bytes.size} bytes)")
                        val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                            order(ByteOrder.nativeOrder())
                            put(bytes)
                            rewind()
                        }
                        viewer.loadModelGlb(buffer)
                        viewer.transformToUnitCube(Float3(0.0f, 0.0f, 0.0f))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FilamentHub", "Failed to load OSRS town GLB", e)
                }

                // Matrix buffers for screen projection and hit detection
                val viewMatrix = FloatArray(16)
                val projMatrix = DoubleArray(16)
                val rootTransform = FloatArray(16)

                // Animated positions for Nimbus (Kid Gohan) and fighting Saiyans / Beerus
                var lastNimbusX = 0f
                var lastNimbusY = 10.5f
                var lastNimbusZ = -13.5f

                // Function performing direct 3D structure and Kid Gohan / Nimbus hit testing
                fun handle3dTap(tapX: Float, tapY: Float): Boolean {
                    val w = width.toFloat()
                    val h = height.toFloat()
                    if (w <= 0f || h <= 0f) return false
                    // Query current camera matrices and asset root transform
                    try {
                        viewer.camera.getViewMatrix(viewMatrix)
                        viewer.camera.getProjectionMatrix(projMatrix)
                    } catch (e: Throwable) {
                        android.util.Log.e("FilamentHub", "Matrix query failed", e)
                        return false
                    }

                    val rootEntity = viewer.asset?.root ?: 0
                    val tm = engine.transformManager
                    val rootInst = if (rootEntity != 0) tm.getInstance(rootEntity) else 0
                    if (rootInst != 0) {
                        tm.getTransform(rootInst, rootTransform)
                    } else {
                        for (i in 0..15) rootTransform[i] = if (i % 5 == 0) 1.0f else 0.0f
                    }

                    val currentDist = cameraController.distance

                    // 1. Check if user tapped Kid Gohan & Flying Nimbus (Checked first with generous hit radius)
                    val nimbusScreen = projectWorldToScreen(
                        lastNimbusX, lastNimbusY, lastNimbusZ,
                        rootTransform, viewMatrix, projMatrix, w, h
                    )
                    val nimbusRadiusPx = (400f * (0.85f / currentDist.coerceAtLeast(0.2f))).coerceIn(280f, 600f)
                    if (nimbusScreen != null) {
                        val nimbusDist = kotlin.math.hypot(tapX - nimbusScreen.first, tapY - nimbusScreen.second)
                        android.util.Log.i("FilamentHub", "Kid Gohan & Nimbus check: tap=($tapX, $tapY), nimbus=(${nimbusScreen.first}, ${nimbusScreen.second}), dist=$nimbusDist px, radius=$nimbusRadiusPx px")
                        if (nimbusDist <= nimbusRadiusPx) {
                            android.util.Log.i("FilamentHub", "Kid Gohan on Nimbus tapped! Triggering Settings.")
                            onNimbusTapped()
                            return true
                        }
                    }

                    // Passthrough taps on the Authorize Google SSO badge to Compose overlays
                    if (tapX >= 1700f && tapY <= 300f) return false

                    // 2. Direct 3D Structure Hit Testing:
                    val buildingPositions = listOf(
                        Triple(GuildBuilding.BOUNTY_BOARD,  -7.2f, -7.2f), // NW
                        Triple(GuildBuilding.SCRIBE_TOWER,   7.2f, -7.2f), // NE
                        Triple(GuildBuilding.SKILL_ALTAR,   -7.2f,  7.2f), // SW
                        Triple(GuildBuilding.COURIER_GUILD,  7.2f,  7.2f), // SE
                    )

                    val buildingHeights = mapOf(
                        GuildBuilding.BOUNTY_BOARD  to 2.4f,
                        GuildBuilding.SCRIBE_TOWER  to 3.8f,
                        GuildBuilding.SKILL_ALTAR   to 2.0f,
                        GuildBuilding.COURIER_GUILD to 2.6f,
                    )

                    var hitBuilding: GuildBuilding? = null
                    var minDistance = Float.MAX_VALUE
                    val hitRadiusPx = (200f * (0.85f / currentDist.coerceAtLeast(0.2f))).coerceIn(160f, 360f)

                    for ((building, bx, bz) in buildingPositions) {
                        val by = buildingHeights[building] ?: 2.0f
                        val screenPt = projectWorldToScreen(
                            bx, by, bz,
                            rootTransform, viewMatrix, projMatrix, w, h
                        ) ?: continue

                        val dist = kotlin.math.hypot(tapX - screenPt.first, tapY - screenPt.second)
                        if (dist < minDistance) {
                            minDistance = dist
                            if (dist <= hitRadiusPx) {
                                hitBuilding = building
                            } else {
                                hitBuilding = null
                            }
                        }
                    }

                    if (hitBuilding != null) {
                        android.util.Log.i("FilamentHub", "Direct 3D object clicked: $hitBuilding (dist=$minDistance px <= $hitRadiusPx px). Navigating.")
                        onBuildingSelected(hitBuilding)
                        return true
                    }

                    // Taps outside the 3D structures (on the ground, towers, Popo's house, or sky) are rejected
                    android.util.Log.i("FilamentHub", "Tap at ($tapX, $tapY) did not hit any building (closest=$minDistance px > $hitRadiusPx px). Ignored.")
                    return false
                }

                var downX = 0f
                var downY = 0f
                var isTapCandidate = false

                setOnTouchListener { _, event ->
                    android.util.Log.i("FilamentHub", "SurfaceView onTouch: action=${event.actionMasked}, x=${event.x}, y=${event.y}")
                    cameraController.viewportWidth = width
                    cameraController.viewportHeight = height

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            isTapCandidate = true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dist = kotlin.math.hypot(event.x - downX, event.y - downY)
                            if (dist > 25f) {
                                isTapCandidate = false
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isTapCandidate) {
                                val dist = kotlin.math.hypot(event.x - downX, event.y - downY)
                                if (dist <= 25f) {
                                    handle3dTap(event.x, event.y)
                                }
                            }
                        }
                    }

                    // Forward drag-to-rotate and pinch-to-zoom to ModelViewer's native manipulator
                    viewer.onTouchEvent(event)
                    true
                }

                // Billboard and dialogue position caches and cached Filament entity transform instances
                var lastBillboardMap = emptyMap<GuildBuilding, Pair<Float, Float>>()
                var lastNimbusScreenPt: Pair<Float, Float>? = null
                var lastDialogues = emptyList<FloatingCharacterDialogue>()
                var entitiesInitialized = false
                var nimbusInst = 0
                var gokuInst = 0
                var vegetaInst = 0
                var beerusInst = 0
                var kameInst = 0
                var hakaiInst = 0
                var finalFlashInst = 0
                var clashInst = 0
                var spiritBombInst = 0

                // Continuous 120Hz render loop with dynamic multi-perspective camera & living DBZ battle animations
                val choreographer = Choreographer.getInstance()
                var isRendering = true
                val frameCallback = object : Choreographer.FrameCallback {
                    val eyePosBuf = DoubleArray(3)
                    val targetBuf = DoubleArray(3)
                    val upwardBuf = DoubleArray(3)

                    override fun doFrame(frameTimeNanos: Long) {
                        if (isRendering) {
                            choreographer.postFrameCallback(this)

                            val tm = engine.transformManager
                            val timeSec = (System.currentTimeMillis() % 600000L) / 1000.0f
                            val w = width.toFloat()
                            val h = height.toFloat()

                            // Read live camera state from manipulator
                            try {
                                manipulator.getLookAt(eyePosBuf, targetBuf, upwardBuf)
                                val dx = eyePosBuf[0] - targetBuf[0]
                                val dy = eyePosBuf[1] - targetBuf[1]
                                val dz = eyePosBuf[2] - targetBuf[2]
                                val dist = sqrt(dx * dx + dy * dy + dz * dz).toFloat()
                                val pitch = asin((dy / dist).coerceIn(-1.0, 1.0)).toFloat()
                                val yaw = atan2(dx, dz).toFloat()
                                cameraController.distance = dist
                                cameraController.pitch = pitch
                                cameraController.yaw = yaw
                            } catch (_: Throwable) {}

                            // Query camera view and projection matrices
                            if (w > 0f && h > 0f) {
                                try {
                                    viewer.camera.getViewMatrix(viewMatrix)
                                    viewer.camera.getProjectionMatrix(projMatrix)
                                    val rootEntity = viewer.asset?.root ?: 0
                                    val rootInst = if (rootEntity != 0) tm.getInstance(rootEntity) else 0
                                    if (rootInst != 0) {
                                        tm.getTransform(rootInst, rootTransform)
                                    } else {
                                        for (i in 0..15) rootTransform[i] = if (i % 5 == 0) 1.0f else 0.0f
                                    }

                                    // 1. Real-time 3D Rooftop Projection for Floating Building Billboards
                                    val bountyPt  = projectWorldToScreen(-7.2f, 4.2f, -7.2f, rootTransform, viewMatrix, projMatrix, w, h)
                                    val scribePt  = projectWorldToScreen( 7.2f, 6.2f, -7.2f, rootTransform, viewMatrix, projMatrix, w, h)
                                    val altarPt   = projectWorldToScreen(-7.2f, 4.2f,  7.2f, rootTransform, viewMatrix, projMatrix, w, h)
                                    val courierPt = projectWorldToScreen( 7.2f, 5.8f,  7.2f, rootTransform, viewMatrix, projMatrix, w, h)

                                    val newBillboardMap = mutableMapOf<GuildBuilding, Pair<Float, Float>>()
                                    if (bountyPt != null)  newBillboardMap[GuildBuilding.BOUNTY_BOARD]  = bountyPt
                                    if (scribePt != null)  newBillboardMap[GuildBuilding.SCRIBE_TOWER]  = scribePt
                                    if (altarPt != null)   newBillboardMap[GuildBuilding.SKILL_ALTAR]   = altarPt
                                    if (courierPt != null) newBillboardMap[GuildBuilding.COURIER_GUILD] = courierPt

                                    if (hasSignificantBillboardMovement(lastBillboardMap, newBillboardMap)) {
                                        lastBillboardMap = newBillboardMap
                                        android.util.Log.i("FilamentHub", "Updated building billboards: $newBillboardMap")
                                        onUpdateBuildingBillboards(newBillboardMap)
                                    }
                                } catch (_: Throwable) {}
                            }

                            // Initialize Filament transform instances once model is ready
                            if (!entitiesInitialized && viewer.asset != null) {
                                val asset = viewer.asset!!
                                val n = asset.getFirstEntityByName("Nimbus")
                                if (n != 0) nimbusInst = tm.getInstance(n)
                                val g = asset.getFirstEntityByName("SaiyanGoku")
                                if (g != 0) gokuInst = tm.getInstance(g)
                                val v = asset.getFirstEntityByName("SaiyanVegeta")
                                if (v != 0) vegetaInst = tm.getInstance(v)
                                val b = asset.getFirstEntityByName("Beerus")
                                if (b != 0) beerusInst = tm.getInstance(b)
                                val k = asset.getFirstEntityByName("KamehamehaBeam")
                                if (k != 0) kameInst = tm.getInstance(k)
                                val h = asset.getFirstEntityByName("HakaiBeam")
                                if (h != 0) hakaiInst = tm.getInstance(h)
                                val ff = asset.getFirstEntityByName("FinalFlashBeam")
                                if (ff != 0) finalFlashInst = tm.getInstance(ff)
                                val c = asset.getFirstEntityByName("KiClashImpact")
                                if (c != 0) clashInst = tm.getInstance(c)
                                val sb = asset.getFirstEntityByName("SpiritBomb")
                                if (sb != 0) spiritBombInst = tm.getInstance(sb)
                                entitiesInitialized = true
                                android.util.Log.i("FilamentHub", "Entities initialized: goku=$gokuInst, vegeta=$vegetaInst, beerus=$beerusInst, nimbus=$nimbusInst, spiritBomb=$spiritBombInst")
                            }

                            // 2. Flying Nimbus & Kid Gohan zooming randomly high above the world with sentient-like movement
                            if (nimbusInst != 0) {
                                val nPhase1 = timeSec * 0.52f
                                val nPhase2 = timeSec * 0.22f
                                val nPhase3 = timeSec * 0.76f

                                val nRadius = 14.2f + 4.8f * sin(nPhase2) + 2.5f * cos(nPhase1 * 0.6f)
                                val nAzimuth = nPhase1 + 0.60f * sin(nPhase2 * 1.6f) + 0.30f * cos(nPhase3)
                                val nAltitude = 10.8f + 3.4f * sin(timeSec * 0.70f) + 1.5f * cos(nPhase2 * 2.2f)

                                lastNimbusX = nRadius * cos(nAzimuth)
                                lastNimbusZ = nRadius * sin(nAzimuth)
                                lastNimbusY = nAltitude

                                // Forward direction vector for sentient banking and steering
                                val dt = 0.05f
                                val nextPhase1 = (timeSec + dt) * 0.52f
                                val nextPhase2 = (timeSec + dt) * 0.22f
                                val nextPhase3 = (timeSec + dt) * 0.76f
                                val nextRadius = 14.2f + 4.8f * sin(nextPhase2) + 2.5f * cos(nextPhase1 * 0.6f)
                                val nextAzimuth = nextPhase1 + 0.60f * sin(nextPhase2 * 1.6f) + 0.30f * cos(nextPhase3)
                                val nextAltitude = 10.8f + 3.4f * sin((timeSec + dt) * 0.70f) + 1.5f * cos(nextPhase2 * 2.2f)
                                val nextX = nextRadius * cos(nextAzimuth)
                                val nextZ = nextRadius * sin(nextAzimuth)
                                val nextY = nextAltitude

                                tm.setTransform(
                                    nimbusInst,
                                    makeOrientedTransformMatrix(lastNimbusX, lastNimbusY, lastNimbusZ, nextX, nextY, nextZ)
                                )

                                if (w > 0f && h > 0f) {
                                    val nPt = projectWorldToScreen(lastNimbusX, lastNimbusY + 1.2f, lastNimbusZ, rootTransform, viewMatrix, projMatrix, w, h)
                                    val prevPt = lastNimbusScreenPt
                                    if (nPt != null && (prevPt == null ||
                                            kotlin.math.hypot(nPt.first - prevPt.first, nPt.second - prevPt.second) > 2.0f)) {
                                        lastNimbusScreenPt = nPt
                                        onUpdateNimbusBillboard(nPt)
                                    }
                                }
                            }

                            // 3. Goku and Vegeta (in normal attires) sparring in random 360-degree trajectories all around the 3D world
                            val sparRivalInst = vegetaInst
                            if (gokuInst != 0 && sparRivalInst != 0) {
                                // Dynamic 360-degree combat arena center moving unpredictably around the sky
                                val combatAzimuth = timeSec * 0.26f + 0.45f * sin(timeSec * 0.16f) + 0.25f * cos(timeSec * 0.39f)
                                val combatRadius = 16.8f + 3.5f * sin(timeSec * 0.22f) + 1.8f * cos(timeSec * 0.48f)
                                val combatAltitude = 5.6f + 2.2f * sin(timeSec * 0.38f) + 1.0f * cos(timeSec * 0.61f)

                                val cx = combatRadius * cos(combatAzimuth)
                                val cz = combatRadius * sin(combatAzimuth)
                                val cy = combatAltitude

                                // 360-degree tangent and radial vectors
                                val tx = -sin(combatAzimuth)
                                val tz = cos(combatAzimuth)
                                val nx = cos(combatAzimuth)
                                val nz = sin(combatAzimuth)

                                // 24-second anime combat cycle: 6 randomized/procedural fighting moves (4 seconds each)
                                val cycleT = timeSec % 24.0f
                                val moveIdx = (cycleT / 4.0f).toInt() % 6
                                val moveT = (cycleT % 4.0f) / 4.0f

                                var gx = cx
                                var gy = cy
                                var gz = cz
                                var rx = cx
                                var ry = cy
                                var rz = cz

                                when (moveIdx) {
                                    0 -> {
                                        // Move 0: Sonic Melee Clash Flurry (Anime Fast-Paced Fist & Kick Clash)
                                        val jitter = 0.14f * sin(34f * timeSec)
                                        val jitterY = 0.10f * cos(34f * timeSec)
                                        gx = cx - tx * 0.72f + nx * jitter
                                        gz = cz - tz * 0.72f + nz * jitter
                                        gy = cy + jitterY

                                        rx = cx + tx * 0.72f - nx * jitter
                                        rz = cz + tz * 0.72f - nz * jitter
                                        ry = cy - jitterY

                                        // Rapid martial arts impact sparks at clash point
                                        if (clashInst != 0) {
                                            val sparkScale = 0.45f + 0.20f * sin(32f * timeSec)
                                            tm.setTransform(clashInst, makeScaledTransformMatrix(cx, cy, cz, sparkScale))
                                        }
                                        if (kameInst != 0) tm.setTransform(kameInst, INACTIVE_MATRIX)
                                        if (hakaiInst != 0) tm.setTransform(hakaiInst, INACTIVE_MATRIX)
                                        if (finalFlashInst != 0) tm.setTransform(finalFlashInst, INACTIVE_MATRIX)
                                        if (spiritBombInst != 0) tm.setTransform(spiritBombInst, INACTIVE_MATRIX)
                                    }
                                    1 -> {
                                        // Move 1: Goku Kamehameha Beam Attack vs Rival Guard (Distanced ~7m across sky)
                                        val sep = 7.0f
                                        gx = cx - tx * sep
                                        gz = cz - tz * sep
                                        gy = cy + 0.2f * sin(timeSec * 2f)

                                        val pushBack = if (moveT >= 0.22f) 0.6f * sin((moveT - 0.22f) * Math.PI.toFloat()) else 0f
                                        rx = cx + tx * (sep + pushBack)
                                        rz = cz + tz * (sep + pushBack)
                                        ry = cy + 0.3f * cos(timeSec * 2f)

                                        if (moveT >= 0.22f) {
                                            // Kamehameha beam actively surging from Goku's hands to Rival
                                            val beamDist = sqrt((rx - gx) * (rx - gx) + (ry - gy) * (ry - gy) + (gz - rz) * (gz - rz))
                                            val pulse = 1.0f + 0.16f * sin(28f * timeSec)
                                            if (kameInst != 0) {
                                                tm.setTransform(
                                                    kameInst,
                                                    makeOrientedTransformMatrix(gx, gy, gz, rx, ry, rz, pulse, pulse, beamDist / 12.0f)
                                                )
                                            }
                                            if (clashInst != 0) {
                                                val impScale = 0.85f + 0.22f * sin(28f * timeSec)
                                                tm.setTransform(clashInst, makeScaledTransformMatrix(rx, ry, rz, impScale))
                                            }
                                        } else {
                                            if (kameInst != 0) tm.setTransform(kameInst, INACTIVE_MATRIX)
                                            if (clashInst != 0) tm.setTransform(clashInst, INACTIVE_MATRIX)
                                        }
                                        if (hakaiInst != 0) tm.setTransform(hakaiInst, INACTIVE_MATRIX)
                                        if (finalFlashInst != 0) tm.setTransform(finalFlashInst, INACTIVE_MATRIX)
                                        if (spiritBombInst != 0) tm.setTransform(spiritBombInst, INACTIVE_MATRIX)
                                    }
                                    2 -> {
                                        // Move 2: Instant Transmission & Z-Vanish Flank Strike
                                        if (moveT < 0.35f) {
                                            // Rapid high-speed circular vortex
                                            val vortexAng = timeSec * 14.0f
                                            val vR = 1.2f
                                            gx = cx + tx * (vR * cos(vortexAng)) + nx * (vR * sin(vortexAng))
                                            gz = cz + tz * (vR * cos(vortexAng)) + nz * (vR * sin(vortexAng))
                                            gy = cy + 0.4f * sin(vortexAng)

                                            rx = cx - tx * (vR * cos(vortexAng)) - nx * (vR * sin(vortexAng))
                                            rz = cz - tz * (vR * cos(vortexAng)) - nz * (vR * sin(vortexAng))
                                            ry = cy - 0.4f * sin(vortexAng)
                                            if (clashInst != 0) tm.setTransform(clashInst, INACTIVE_MATRIX)
                                        } else if (moveT < 0.70f) {
                                            // Instant Transmission: Rival teleports behind Goku
                                            gx = cx - tx * 0.8f
                                            gz = cz - tz * 0.8f
                                            gy = cy

                                            rx = gx - tx * 1.1f
                                            rz = gz - tz * 1.1f
                                            ry = gy + 0.25f
                                            if (clashInst != 0) tm.setTransform(clashInst, INACTIVE_MATRIX)
                                        } else {
                                            // Heavy flank strike clash!
                                            gx = cx - tx * 0.7f
                                            gz = cz - tz * 0.7f
                                            gy = cy

                                            rx = gx - tx * 0.9f
                                            rz = gz - tz * 0.9f
                                            ry = gy + 0.1f
                                            if (clashInst != 0) {
                                                val hitX = (gx + rx) * 0.5f
                                                val hitY = (gy + ry) * 0.5f
                                                val hitZ = (gz + rz) * 0.5f
                                                tm.setTransform(clashInst, makeScaledTransformMatrix(hitX, hitY, hitZ, 0.65f))
                                            }
                                        }
                                        if (kameInst != 0) tm.setTransform(kameInst, INACTIVE_MATRIX)
                                        if (hakaiInst != 0) tm.setTransform(hakaiInst, INACTIVE_MATRIX)
                                        if (finalFlashInst != 0) tm.setTransform(finalFlashInst, INACTIVE_MATRIX)
                                        if (spiritBombInst != 0) tm.setTransform(spiritBombInst, INACTIVE_MATRIX)
                                    }
                                    3 -> {
                                        // Move 3: Vegeta Purple Galick Gun Beam Attack (Distanced ~7m across sky)
                                        val sep = 7.0f
                                        rx = cx + tx * sep
                                        rz = cz + tz * sep
                                        ry = cy + 0.3f * sin(timeSec * 2f)

                                        val pushBack = if (moveT >= 0.22f) 0.6f * sin((moveT - 0.22f) * Math.PI.toFloat()) else 0f
                                        gx = cx - tx * (sep + pushBack)
                                        gz = cz - tz * (sep + pushBack)
                                        gy = cy + 0.2f * cos(timeSec * 2f)

                                        if (moveT >= 0.22f) {
                                            // Purple Galick Gun surging from Vegeta across to Goku
                                            val beamDist = sqrt((gx - rx) * (gx - rx) + (gy - ry) * (gy - ry) + (gz - rz) * (gz - rz))
                                            val pulse = 1.10f + 0.20f * sin(26f * timeSec)
                                            if (hakaiInst != 0) {
                                                tm.setTransform(
                                                    hakaiInst,
                                                    makeOrientedTransformMatrix(rx, ry, rz, gx, gy, gz, pulse, pulse, beamDist / 12.0f)
                                                )
                                            }
                                            if (finalFlashInst != 0) tm.setTransform(finalFlashInst, INACTIVE_MATRIX)
                                            if (clashInst != 0) {
                                                val impScale = 1.0f + 0.25f * sin(26f * timeSec)
                                                tm.setTransform(clashInst, makeScaledTransformMatrix(gx, gy, gz, impScale))
                                            }
                                        } else {
                                            if (hakaiInst != 0) tm.setTransform(hakaiInst, INACTIVE_MATRIX)
                                            if (finalFlashInst != 0) tm.setTransform(finalFlashInst, INACTIVE_MATRIX)
                                            if (clashInst != 0) tm.setTransform(clashInst, INACTIVE_MATRIX)
                                        }
                                        if (kameInst != 0) tm.setTransform(kameInst, INACTIVE_MATRIX)
                                        if (spiritBombInst != 0) tm.setTransform(spiritBombInst, INACTIVE_MATRIX)
                                    }
                                    4 -> {
                                        // Move 4: Ultimate Clash — Vegeta Purple Galick Gun vs Goku Celestial Spirit Bomb (~15m Sky Clash)
                                        val sep = 7.5f
                                        val recoilG = if (moveT >= 0.16f) 0.08f * sin(48f * timeSec) else 0f
                                        val recoilV = if (moveT >= 0.16f) 0.08f * cos(48f * timeSec) else 0f

                                        gx = cx - tx * sep + nx * recoilG
                                        gz = cz - tz * sep + nz * recoilG
                                        gy = cy

                                        rx = cx + tx * sep - nx * recoilV
                                        rz = cz + tz * sep - nz * recoilV
                                        ry = cy

                                        if (moveT >= 0.16f) {
                                            // High-intensity collision struggle midpoint with dynamic vibration
                                            val midShift = 0.55f * sin(6.5f * timeSec)
                                            val mx = cx + tx * midShift
                                            val my = cy + 0.20f * cos(12.0f * timeSec)
                                            val mz = cz + tz * midShift

                                            val distR = sqrt((mx - rx) * (mx - rx) + (my - ry) * (my - ry) + (mz - rz) * (mz - rz))
                                            val pulse = 1.35f + 0.30f * sin(36f * timeSec)

                                            // Vegeta: Purple Galick Gun Beam clashing into midpoint
                                            if (hakaiInst != 0) {
                                                tm.setTransform(
                                                    hakaiInst,
                                                    makeOrientedTransformMatrix(rx, ry, rz, mx, my, mz, pulse, pulse, distR / 12.0f)
                                                )
                                            }
                                            if (finalFlashInst != 0) tm.setTransform(finalFlashInst, INACTIVE_MATRIX)
                                            if (kameInst != 0) tm.setTransform(kameInst, INACTIVE_MATRIX)

                                            // Goku: Celestial Spirit Bomb (Genki Dama)
                                            if (spiritBombInst != 0) {
                                                if (moveT < 0.38f) {
                                                    // Goku raises arms and gathers energy; Spirit Bomb grows above his head
                                                    val chargeProgress = (moveT - 0.16f) / 0.22f
                                                    val sbScale = 0.7f + chargeProgress * 1.8f + 0.15f * sin(24f * timeSec)
                                                    tm.setTransform(
                                                        spiritBombInst,
                                                        makeScaledTransformMatrix(gx, gy + 3.2f, gz, sbScale)
                                                    )
                                                } else {
                                                    // Goku hurls the giant Spirit Bomb into the midpoint clash against Galick Gun!
                                                    val hurlT = minOf(1.0f, (moveT - 0.38f) / 0.14f)
                                                    val currentSbX = gx + (mx - gx) * hurlT
                                                    val currentSbY = (gy + 3.2f) + (my - (gy + 3.2f)) * hurlT
                                                    val currentSbZ = gz + (mz - gz) * hurlT
                                                    val sbScale = 2.4f + 0.30f * sin(32f * timeSec)
                                                    tm.setTransform(
                                                        spiritBombInst,
                                                        makeScaledTransformMatrix(currentSbX, currentSbY, currentSbZ, sbScale)
                                                    )
                                                }
                                            }

                                            // Colossal collision shockwave explosion and radial energy spikes
                                            if (clashInst != 0) {
                                                val clashScale = 2.8f + 0.50f * sin(38f * timeSec)
                                                tm.setTransform(clashInst, makeScaledTransformMatrix(mx, my, mz, clashScale))
                                            }
                                        } else {
                                            if (kameInst != 0) tm.setTransform(kameInst, INACTIVE_MATRIX)
                                            if (hakaiInst != 0) tm.setTransform(hakaiInst, INACTIVE_MATRIX)
                                            if (finalFlashInst != 0) tm.setTransform(finalFlashInst, INACTIVE_MATRIX)
                                            if (spiritBombInst != 0) tm.setTransform(spiritBombInst, INACTIVE_MATRIX)
                                            if (clashInst != 0) tm.setTransform(clashInst, INACTIVE_MATRIX)
                                        }
                                    }
                                    else -> {
                                        // Move 5: Supersonic Double Helix Dogfight Weaving Around Sky Clouds
                                        val helixT = timeSec * 5.0f
                                        val helixR = 1.6f
                                        gx = cx - tx * 1.6f + nx * (helixR * cos(helixT))
                                        gz = cz - tz * 1.6f + nz * (helixR * cos(helixT))
                                        gy = cy + helixR * sin(helixT)

                                        rx = cx + tx * 1.6f + nx * (helixR * cos(helixT + Math.PI.toFloat()))
                                        rz = cz + tz * 1.6f + nz * (helixR * cos(helixT + Math.PI.toFloat()))
                                        ry = cy + helixR * sin(helixT + Math.PI.toFloat())

                                        val shootPulse = (timeSec * 3.5f).toInt() % 3 == 0
                                        if (shootPulse) {
                                            val bDist = sqrt((rx - gx) * (rx - gx) + (ry - gy) * (ry - gy) + (rz - gz) * (rz - gz))
                                            if (kameInst != 0) {
                                                tm.setTransform(
                                                    kameInst,
                                                    makeOrientedTransformMatrix(gx, gy, gz, rx, ry, rz, 0.7f, 0.7f, bDist / 12.0f)
                                                )
                                            }
                                        } else {
                                            if (kameInst != 0) tm.setTransform(kameInst, INACTIVE_MATRIX)
                                        }
                                        if (hakaiInst != 0) tm.setTransform(hakaiInst, INACTIVE_MATRIX)
                                        if (finalFlashInst != 0) tm.setTransform(finalFlashInst, INACTIVE_MATRIX)
                                        if (spiritBombInst != 0) tm.setTransform(spiritBombInst, INACTIVE_MATRIX)
                                        if (clashInst != 0) tm.setTransform(clashInst, INACTIVE_MATRIX)
                                    }
                                }

                                // Goku and Vegeta orient to face each other in dynamic combat
                                tm.setTransform(gokuInst, makeOrientedTransformMatrix(gx, gy, gz, rx, ry, rz))
                                tm.setTransform(sparRivalInst, makeOrientedTransformMatrix(rx, ry, rz, gx, gy, gz))

                                // 4. Beerus flies leisurely around the perimeter of the city, calmly observing Goku & Vegeta
                                var bx = 0f
                                var by = 0f
                                var bz = 0f
                                if (beerusInst != 0) {
                                    // Leisurely flight along perimeter of Popo's Lookout sky citadel (platform radius 18.0)
                                    val beerusRadius = 17.2f + 1.2f * sin(timeSec * 0.14f)
                                    val beerusAzimuth = timeSec * 0.18f + 0.15f * sin(timeSec * 0.08f)
                                    val beerusAltitude = 6.4f + 1.2f * sin(timeSec * 0.32f)

                                    bx = beerusRadius * cos(beerusAzimuth)
                                    bz = beerusRadius * sin(beerusAzimuth)
                                    by = beerusAltitude

                                    // Facing the sparring battle center (cx, cy, cz) to observe Goku and Vegeta
                                    tm.setTransform(beerusInst, makeOrientedTransformMatrix(bx, by, bz, cx, cy, cz))
                                }

                                // 5. Project 3D speech bubble anchors and update character dialogues
                                val gokuSpeech = projectWorldToScreen(gx, gy + 0.95f, gz, rootTransform, viewMatrix, projMatrix, w, h)
                                val vegetaSpeech = projectWorldToScreen(rx, ry + 0.95f, rz, rootTransform, viewMatrix, projMatrix, w, h)
                                val beerusSpeech = if (beerusInst != 0) projectWorldToScreen(bx, by + 0.95f, bz, rootTransform, viewMatrix, projMatrix, w, h) else null

                                val gokuVisible = gokuSpeech != null && gokuSpeech.first in 10f..(w - 10f) && gokuSpeech.second in 100f..(h - 60f)
                                val vegetaVisible = vegetaSpeech != null && vegetaSpeech.first in 10f..(w - 10f) && vegetaSpeech.second in 100f..(h - 60f)
                                val beerusVisible = beerusSpeech != null && beerusSpeech.first in 10f..(w - 10f) && beerusSpeech.second in 100f..(h - 60f)

                                val newDialogues = mutableListOf<FloatingCharacterDialogue>()

                                // Beerus observer dialogue system: cycles through multiple unique, condescending voice lines
                                val beerusVoiceLines = listOf(
                                    "Come on guys, you're making me yawn...",
                                    "Is that all the power a Saiyan has?",
                                    "Wake me up when someone actually lands a punch.",
                                    "Whis, remind me why we didn't just stay asleep...",
                                    "Mortals... always shouting before they throw a punch."
                                )
                                val beerusLineIdx = ((timeSec / 8.0f).toInt()) % beerusVoiceLines.size
                                val isBeerusSpeaking = (cycleT in 1.8f..5.2f) || (cycleT in 9.5f..13.0f) || (cycleT in 17.5f..21.0f)
                                if (isBeerusSpeaking && beerusVisible && beerusSpeech != null) {
                                    newDialogues.add(
                                        FloatingCharacterDialogue(
                                            character = CharacterSpeaker.BEERUS,
                                            speakerName = "Lord Beerus",
                                            icon = "🐱",
                                            text = beerusVoiceLines[beerusLineIdx],
                                            screenX = beerusSpeech.first,
                                            screenY = beerusSpeech.second,
                                            borderColor = Color(0xFFD500F9),
                                            titleColor = Color(0xFFEA80FC),
                                        )
                                    )
                                }

                                // Goku & Vegeta competitive battle dialogue as they clash
                                when (moveIdx) {
                                    0 -> {
                                        // Move 0: Sonic Melee Clash Flurry
                                        if (moveT in 0.10f..0.85f) {
                                            if (gokuVisible && gokuSpeech != null) {
                                                newDialogues.add(
                                                    FloatingCharacterDialogue(
                                                        character = CharacterSpeaker.GOKU,
                                                        speakerName = "Goku (SSJ3)",
                                                        icon = "🥋",
                                                        text = "Heh, not bad Vegeta! Let's take it up a notch!",
                                                        screenX = gokuSpeech.first,
                                                        screenY = gokuSpeech.second,
                                                        borderColor = Color(0xFFFF6D00),
                                                        titleColor = Color(0xFFFFD700),
                                                    )
                                                )
                                            }
                                            if (vegetaVisible && vegetaSpeech != null) {
                                                newDialogues.add(
                                                    FloatingCharacterDialogue(
                                                        character = CharacterSpeaker.VEGETA,
                                                        speakerName = "Vegeta",
                                                        icon = "👑",
                                                        text = "Hmph! Don't get cocky, Kakarot!",
                                                        screenX = vegetaSpeech.first,
                                                        screenY = vegetaSpeech.second,
                                                        borderColor = Color(0xFF2979FF),
                                                        titleColor = Color(0xFFFFD700),
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    1 -> {
                                        // Move 1: Goku Kamehameha vs Vegeta Guard
                                        if (moveT in 0.22f..0.90f) {
                                            if (gokuVisible && gokuSpeech != null) {
                                                newDialogues.add(
                                                    FloatingCharacterDialogue(
                                                        character = CharacterSpeaker.GOKU,
                                                        speakerName = "Goku (SSJ3)",
                                                        icon = "🥋",
                                                        text = "KAMEHAMEHA--!",
                                                        screenX = gokuSpeech.first,
                                                        screenY = gokuSpeech.second,
                                                        borderColor = Color(0xFFFF6D00),
                                                        titleColor = Color(0xFFFFD700),
                                                    )
                                                )
                                            }
                                            if (vegetaVisible && vegetaSpeech != null) {
                                                newDialogues.add(
                                                    FloatingCharacterDialogue(
                                                        character = CharacterSpeaker.VEGETA,
                                                        speakerName = "Vegeta",
                                                        icon = "👑",
                                                        text = "Think that blast can stop me?!",
                                                        screenX = vegetaSpeech.first,
                                                        screenY = vegetaSpeech.second,
                                                        borderColor = Color(0xFF2979FF),
                                                        titleColor = Color(0xFFFFD700),
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    2 -> {
                                        // Move 2: Instant Transmission & Z-Vanish Flank Strike
                                        if (moveT in 0.55f..0.95f) {
                                            if (vegetaVisible && vegetaSpeech != null) {
                                                newDialogues.add(
                                                    FloatingCharacterDialogue(
                                                        character = CharacterSpeaker.VEGETA,
                                                        speakerName = "Vegeta",
                                                        icon = "👑",
                                                        text = "Too slow, Kakarot!",
                                                        screenX = vegetaSpeech.first,
                                                        screenY = vegetaSpeech.second,
                                                        borderColor = Color(0xFF2979FF),
                                                        titleColor = Color(0xFFFFD700),
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    3 -> {
                                        // Move 3: Vegeta Purple Galick Gun Beam Attack vs Goku Guard
                                        if (moveT in 0.22f..0.90f) {
                                            if (vegetaVisible && vegetaSpeech != null) {
                                                newDialogues.add(
                                                    FloatingCharacterDialogue(
                                                        character = CharacterSpeaker.VEGETA,
                                                        speakerName = "Vegeta",
                                                        icon = "👑",
                                                        text = "GALICK GUN-- FIREEEEE!!!",
                                                        screenX = vegetaSpeech.first,
                                                        screenY = vegetaSpeech.second,
                                                        borderColor = Color(0xFFD500F9),
                                                        titleColor = Color(0xFFFFD700),
                                                    )
                                                )
                                            }
                                            if (gokuVisible && gokuSpeech != null) {
                                                newDialogues.add(
                                                    FloatingCharacterDialogue(
                                                        character = CharacterSpeaker.GOKU,
                                                        speakerName = "Goku (SSJ3)",
                                                        icon = "🥋",
                                                        text = "Whoa! What terrifying energy...!",
                                                        screenX = gokuSpeech.first,
                                                        screenY = gokuSpeech.second,
                                                        borderColor = Color(0xFFFF6D00),
                                                        titleColor = Color(0xFFFFD700),
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    4 -> {
                                        // Move 4: Ultimate Clash Dialogue (Vegeta Galick Gun vs Goku Celestial Spirit Bomb)
                                        if (moveT in 0.16f..0.92f) {
                                            if (vegetaVisible && vegetaSpeech != null) {
                                                newDialogues.add(
                                                    FloatingCharacterDialogue(
                                                        character = CharacterSpeaker.VEGETA,
                                                        speakerName = "Vegeta",
                                                        icon = "👑",
                                                        text = "I'LL BLOW YOU TO ASHES! GALICK GUN--!!!",
                                                        screenX = vegetaSpeech.first,
                                                        screenY = vegetaSpeech.second,
                                                        borderColor = Color(0xFFD500F9),
                                                        titleColor = Color(0xFFFFD700),
                                                    )
                                                )
                                            }
                                            if (gokuVisible && gokuSpeech != null) {
                                                newDialogues.add(
                                                    FloatingCharacterDialogue(
                                                        character = CharacterSpeaker.GOKU,
                                                        speakerName = "Goku (SSJ3)",
                                                        icon = "🥋",
                                                        text = "Lend me your energy...! SPIRIT BOMB--!!!",
                                                        screenX = gokuSpeech.first,
                                                        screenY = gokuSpeech.second,
                                                        borderColor = Color(0xFF00E5FF),
                                                        titleColor = Color(0xFFFFD700),
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    else -> {}
                                }

                                if (hasSignificantDialogueMovement(lastDialogues, newDialogues)) {
                                    lastDialogues = newDialogues
                                    android.util.Log.i("FilamentHub", "Updated character dialogues: ${newDialogues.map { "${it.speakerName}: ${it.text} at (${it.screenX}, ${it.screenY})" }}")
                                    onUpdateCharacterDialogues(newDialogues)
                                }
                            }

                            viewer.render(frameTimeNanos)
                        }
                    }
                }

                addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: android.view.View) {
                        if (!isRendering) {
                            isRendering = true
                            choreographer.postFrameCallback(frameCallback)
                        }
                    }

                    override fun onViewDetachedFromWindow(v: android.view.View) {
                        isRendering = false
                        choreographer.removeFrameCallback(frameCallback)
                    }
                })

                choreographer.postFrameCallback(frameCallback)
            }
        },
        onRelease = { surfaceView ->
            val viewer = surfaceView.tag as? ModelViewer
            try {
                viewer?.destroyModel()
            } catch (_: Throwable) {}
        },
    )
}

/**
 * Profile & Resume Configuration Modal.
 *
 * Directive (GEMINI.md Part 2):
 *  - Triggered by Kid Gohan riding the Flying Nimbus.
 *  - Replaces generic settings with interactive input fields for Name, Email,
 *    Address, Location, GitHub, and SSO preferences.
 *  - Serves as the definitive fallback source of truth for Scribe Tower resume prefill.
 */
@Composable
private fun GuildSettingsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val profileManager = remember { UserProfileManager.getInstance(context) }
    val currentProfile = remember { profileManager.profile.value }

    var name by remember { mutableStateOf(currentProfile.name) }
    var email by remember { mutableStateOf(currentProfile.email) }
    var address by remember { mutableStateOf(currentProfile.address) }
    var location by remember { mutableStateOf(currentProfile.location) }
    var github by remember { mutableStateOf(currentProfile.github) }
    var ssoPreferred by remember { mutableStateOf(currentProfile.ssoPreferred) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OsrsDarkStone,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .border(2.dp, OsrsGold, RoundedCornerShape(14.dp)),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "☁️", fontSize = 22.sp)
                        Column {
                            Text(
                                text = "Profile & Resume Configuration",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = OsrsGold,
                            )
                            Text(
                                text = "Flying Nimbus Hub · Scribe Tower Source of Truth",
                                style = MaterialTheme.typography.labelSmall,
                                color = OsrsCyan,
                            )
                        }
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = "Enter your candidate details below. Scribe Tower pulls directly from these settings to prefill resume headers and contact info.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = Color(0xFFD0C4B0),
                    )
                }

                item {
                    ProfileInputField(
                        label = "Full Name",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "e.g. John Doe",
                        icon = "👤",
                    )
                }

                item {
                    ProfileInputField(
                        label = "Email Address",
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "e.g. candidate@example.com",
                        icon = "✉️",
                    )
                }

                item {
                    ProfileInputField(
                        label = "Street Address",
                        value = address,
                        onValueChange = { address = it },
                        placeholder = "e.g. 123 Main St, Apt 4B",
                        icon = "🏠",
                    )
                }

                item {
                    ProfileInputField(
                        label = "Location (City, ST or ZIP)",
                        value = location,
                        onValueChange = { location = it },
                        placeholder = "e.g. New York, NY or 10001",
                        icon = "📍",
                    )
                }

                item {
                    ProfileInputField(
                        label = "GitHub Handle or URL",
                        value = github,
                        onValueChange = { github = it },
                        placeholder = "e.g. johndoe or github.com/johndoe",
                        icon = "🐙",
                    )
                }

                item {
                    Surface(
                        color = OsrsStone.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, OsrsDarkBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(text = "🔑", fontSize = 18.sp)
                                Column {
                                    Text(
                                        text = "Google SSO & Inbox Sync",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = OsrsGold,
                                    )
                                    Text(
                                        text = "Authorize OAuth2 inbox sync for automatic application status updates",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = OsrsTextDim,
                                    )
                                }
                            }
                            Switch(
                                checked = ssoPreferred,
                                onCheckedChange = { ssoPreferred = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OsrsGold,
                                    checkedTrackColor = DbzOrange,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = OsrsDarkBorder,
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = UserProfile(
                        name = name.trim(),
                        email = email.trim(),
                        address = address.trim(),
                        location = location.trim(),
                        github = github.trim(),
                        ssoPreferred = ssoPreferred,
                    )
                    profileManager.saveProfile(updated)
                    Toast.makeText(context, "Profile & Resume settings saved!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = OsrsStone),
                border = BorderStroke(1.5.dp, OsrsGold),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "💾 Save Profile",
                    color = OsrsGold,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, OsrsTextDim),
            ) {
                Text(
                    text = "Cancel",
                    color = OsrsTextDim,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    )
}

@Composable
private fun ProfileInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: String,
) {
    Surface(
        color = OsrsStone.copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, OsrsDarkBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = icon, fontSize = 14.sp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = OsrsGold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(text = placeholder, color = OsrsTextDim.copy(alpha = 0.6f), fontSize = 12.sp)
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFF4EDE2),
                    focusedBorderColor = OsrsGold,
                    unfocusedBorderColor = OsrsDarkBorder,
                    cursorColor = OsrsGold,
                    focusedContainerColor = OsrsDarkStone,
                    unfocusedContainerColor = OsrsDarkStone.copy(alpha = 0.6f),
                ),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Projects a 3D model point to 2D screen coordinates using the camera's view & projection matrices
 * and the asset root transformation. Returns null if the point is behind the camera.
 */
private fun projectWorldToScreen(
    wx: Float,
    wy: Float,
    wz: Float,
    rootTransform: FloatArray,
    viewMatrix: FloatArray,
    projMatrix: DoubleArray,
    screenWidth: Float,
    screenHeight: Float,
): Pair<Float, Float>? {
    // 1. Multiply by asset root transform (column-major)
    val rx = rootTransform[0]*wx + rootTransform[4]*wy + rootTransform[8]*wz + rootTransform[12]
    val ry = rootTransform[1]*wx + rootTransform[5]*wy + rootTransform[9]*wz + rootTransform[13]
    val rz = rootTransform[2]*wx + rootTransform[6]*wy + rootTransform[10]*wz + rootTransform[14]
    val rw = rootTransform[3]*wx + rootTransform[7]*wy + rootTransform[11]*wz + rootTransform[15]

    // 2. Multiply by camera view matrix (column-major)
    val vx = viewMatrix[0]*rx + viewMatrix[4]*ry + viewMatrix[8]*rz + viewMatrix[12]*rw
    val vy = viewMatrix[1]*rx + viewMatrix[5]*ry + viewMatrix[9]*rz + viewMatrix[13]*rw
    val vz = viewMatrix[2]*rx + viewMatrix[6]*ry + viewMatrix[10]*rz + viewMatrix[14]*rw
    val vw = viewMatrix[3]*rx + viewMatrix[7]*ry + viewMatrix[11]*rz + viewMatrix[15]*rw

    // 3. Multiply by projection matrix (double[16] column-major)
    val cx = projMatrix[0]*vx + projMatrix[4]*vy + projMatrix[8]*vz + projMatrix[12]*vw
    val cy = projMatrix[1]*vx + projMatrix[5]*vy + projMatrix[9]*vz + projMatrix[13]*vw
    val cz = projMatrix[2]*vx + projMatrix[6]*vy + projMatrix[10]*vz + projMatrix[14]*vw
    val cw = projMatrix[3]*vx + projMatrix[7]*vy + projMatrix[11]*vz + projMatrix[15]*vw

    if (cw <= 0.001) {
        return null
    }

    val ndcX = (cx / cw).toFloat()
    val ndcY = (cy / cw).toFloat()

    val screenX = (ndcX + 1.0f) * 0.5f * screenWidth
    val screenY = (1.0f - ndcY) * 0.5f * screenHeight

    return Pair(screenX, screenY)
}

/**
 * Inactive entity transform placing beam/spark underground and scaled to near-zero.
 */
private val INACTIVE_MATRIX = floatArrayOf(
    0.0001f, 0f, 0f, 0f,
    0f, 0.0001f, 0f, 0f,
    0f, 0f, 0.0001f, 0f,
    0f, -500f, 0f, 1f
)

/**
 * Checks if any billboard position has moved by more than 0.75 pixels.
 */
private fun hasSignificantBillboardMovement(
    oldMap: Map<GuildBuilding, Pair<Float, Float>>,
    newMap: Map<GuildBuilding, Pair<Float, Float>>
): Boolean {
    if (oldMap.size != newMap.size) return true
    for ((k, newPt) in newMap) {
        val oldPt = oldMap[k] ?: return true
        if (kotlin.math.abs(newPt.first - oldPt.first) > 0.75f ||
            kotlin.math.abs(newPt.second - oldPt.second) > 0.75f) {
            return true
        }
    }
    return false
}

/**
 * Checks if active character dialogues or their screen positions have changed significantly.
 */
private fun hasSignificantDialogueMovement(
    oldList: List<FloatingCharacterDialogue>,
    newList: List<FloatingCharacterDialogue>,
): Boolean {
    if (oldList.size != newList.size) return true
    for (i in oldList.indices) {
        val o = oldList[i]
        val n = newList[i]
        if (o.character != n.character || o.text != n.text) return true
        if (kotlin.math.abs(o.screenX - n.screenX) > 1.5f ||
            kotlin.math.abs(o.screenY - n.screenY) > 1.5f) {
            return true
        }
    }
    return false
}

/**
 * Creates a uniform scale + translation 4x4 column-major matrix.
 */
private fun makeScaledTransformMatrix(tx: Float, ty: Float, tz: Float, scale: Float): FloatArray {
    return floatArrayOf(
        scale, 0.0f,  0.0f,  0.0f,
        0.0f,  scale, 0.0f,  0.0f,
        0.0f,  0.0f,  scale, 0.0f,
        tx,    ty,    tz,    1.0f
    )
}

/**
 * Constructs a 4x4 column-major transformation matrix that positions an object at (fromX, fromY, fromZ),
 * scales it by (scaleX, scaleY, scaleZ), and orients its local +Z axis toward (toX, toY, toZ).
 */
private fun makeOrientedTransformMatrix(
    fromX: Float, fromY: Float, fromZ: Float,
    toX: Float, toY: Float, toZ: Float,
    scaleX: Float = 1.0f,
    scaleY: Float = 1.0f,
    scaleZ: Float = 1.0f,
): FloatArray {
    val dx = toX - fromX
    val dy = toY - fromY
    val dz = toZ - fromZ
    val len = sqrt(dx * dx + dy * dy + dz * dz)
    if (len < 0.001f) {
        return makeTransformMatrix(fromX, fromY, fromZ, 0f)
    }

    val fzX = dx / len
    val fzY = dy / len
    val fzZ = dz / len

    var upX = 0f
    var upY = 1f
    var upZ = 0f
    if (kotlin.math.abs(fzY) > 0.95f) {
        upX = 1f
        upY = 0f
        upZ = 0f
    }

    var rx = upY * fzZ - upZ * fzY
    var ry = upZ * fzX - upX * fzZ
    var rz = upX * fzY - upY * fzX
    val rLen = sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(0.0001f)
    rx /= rLen
    ry /= rLen
    rz /= rLen

    val ux = fzY * rz - fzZ * ry
    val uy = fzZ * rx - fzX * rz
    val uz = fzX * ry - fzY * rx

    return floatArrayOf(
        rx * scaleX,  ry * scaleX,  rz * scaleX,  0.0f,
        ux * scaleY,  uy * scaleY,  uz * scaleY,  0.0f,
        fzX * scaleZ, fzY * scaleZ, fzZ * scaleZ, 0.0f,
        fromX,        fromY,        fromZ,        1.0f
    )
}

/**
 * Creates a 4x4 column-major affine transform matrix (translation + yaw rotation around Y).
 */
private fun makeTransformMatrix(tx: Float, ty: Float, tz: Float, yaw: Float): FloatArray {
    val c = cos(yaw)
    val s = sin(yaw)
    return floatArrayOf(
        c,    0.0f,  s,    0.0f, // col 0
        0.0f, 1.0f,  0.0f, 0.0f, // col 1
        -s,   0.0f,  c,    0.0f, // col 2
        tx,   ty,    tz,   1.0f  // col 3
    )
}

