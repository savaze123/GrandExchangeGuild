package com.osrs.careerguild.presentation.hub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.osrs.careerguild.domain.model.GuildBuilding
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Avatar-centric navigation for Popo's Lookout.
 *
 * The virtual joystick drives a physical avatar (not the world): stick X turns the avatar,
 * stick Y walks it forward/back along its heading. The chase camera eases in behind the avatar.
 * Square plinth colliders prevent clipping into buildings; touching one triggers module entry.
 *
 * All coordinates are in the town GLB's local space (same space as the building positions
 * used by the hub's tap hit-testing). Heading 0 faces local +Z; forward = (sin h, cos h).
 */

/** Axis-aligned collider footprint for a guild building plinth (local space). */
data class BuildingCollider(
    val building: GuildBuilding,
    val centerX: Float,
    val centerZ: Float,
    val halfX: Float,
    val halfZ: Float,
)

/** Non-triggering solid scenery (fountain, palace, ...). Circle when [halfZ] <= 0 (radius = halfX). */
data class SolidCollider(val centerX: Float, val centerZ: Float, val halfX: Float, val halfZ: Float)

object AvatarWorld {
    /** Walkable disc; keeps the avatar inside the canal / dragon-ball ring (radius 13.8). */
    const val DECK_RADIUS = 12.6f
    const val AVATAR_RADIUS = 0.45f
    const val GROUND_Y = 0.05f
    const val SPAWN_X = 0.0f
    const val SPAWN_Z = 6.5f // south plaza, outside the fountain, facing it
    const val SPAWN_HEADING = Math.PI.toFloat() // face -Z toward the fountain & Popo's palace

    /** Central fountain (r 3.6) and Popo's palace terrace (7.0 x 5.0 at z -7.5). */
    val solids: List<SolidCollider> = listOf(
        SolidCollider(0f, 0f, 3.7f, 0f),
        SolidCollider(0f, -7.5f, 3.5f, 2.5f),
    )

    /** Plinth footprints from generate_osrs_guild_glb.py (5.5-6.0 wide at +/-7.2). */
    val colliders: List<BuildingCollider> = listOf(
        BuildingCollider(GuildBuilding.BOUNTY_BOARD, -7.2f, -7.2f, 3.0f, 3.0f),
        BuildingCollider(GuildBuilding.SCRIBE_TOWER, 7.2f, -7.2f, 3.5f, 3.5f),
        BuildingCollider(GuildBuilding.SKILL_ALTAR, -7.2f, 7.2f, 3.0f, 3.0f),
        BuildingCollider(GuildBuilding.COURIER_GUILD, 7.2f, 7.2f, 3.0f, 3.0f),
    )
}

/** Immutable result of one avatar simulation step. */
data class AvatarStep(
    val x: Float,
    val z: Float,
    val heading: Float,
    /** Non-null the instant the avatar physically touches a building collider. */
    val triggered: GuildBuilding?,
)

object AvatarPhysics {
    const val WALK_SPEED = 4.2f // local units per second at full (curved) stick
    const val TURN_SPEED = 1.9f // radians per second at full (curved) stick
    const val JOYSTICK_DEADZONE = 0.14f
    const val JOYSTICK_GAIN = 0.60f // reduced sensitivity
    const val JOYSTICK_CURVE = 1.7f // >1 = fine control near centre

    /** Maps raw stick deflection (-1..1) to a damped, deadzoned value for smoother control. */
    fun shapeAxis(raw: Float): Float {
        val a = abs(raw)
        if (a < JOYSTICK_DEADZONE) return 0f
        val norm = ((a - JOYSTICK_DEADZONE) / (1f - JOYSTICK_DEADZONE)).coerceIn(0f, 1f)
        return sign(raw) * norm.pow(JOYSTICK_CURVE) * JOYSTICK_GAIN
    }

    fun step(
        x: Float, z: Float, heading: Float,
        stickX: Float, stickY: Float, dt: Float,
    ): AvatarStep {
        val dtc = dt.coerceIn(0f, 0.05f)
        val turn = shapeAxis(stickX)
        val walk = shapeAxis(stickY) // stick up (+) = forward
        val newHeading = wrapAngle(heading + turn * TURN_SPEED * dtc)
        val nx = x + sin(newHeading) * walk * WALK_SPEED * dtc
        val nz = z + cos(newHeading) * walk * WALK_SPEED * dtc
        val resolved = resolveCollisions(nx, nz)
        return AvatarStep(resolved.first, resolved.second, newHeading, null)
    }

    /**
     * Roblox-style camera-relative movement:
     * Pushing the joystick moves the avatar relative to [cameraYaw].
     * In Third-Person, the avatar's body smoothly turns to face the movement vector.
     * In First-Person, the avatar's heading locks synchronously to [cameraYaw].
     */
    fun stepRelative(
        x: Float, z: Float, heading: Float,
        stickX: Float, stickY: Float, dt: Float,
        cameraYaw: Float,
        isFirstPerson: Boolean = false,
    ): AvatarStep {
        val dtc = dt.coerceIn(0f, 0.05f)
        val shapedX = shapeAxis(stickX)
        val shapedY = shapeAxis(stickY)
        val mag = hypot(shapedX, shapedY)

        var newHeading = heading
        var nx = x
        var nz = z

        if (mag > 0.001f) {
            val stickAngle = atan2(shapedX, shapedY)
            val moveHeading = wrapAngle(cameraYaw + stickAngle)

            if (isFirstPerson) {
                newHeading = cameraYaw
            } else {
                // Smoothly rotate avatar heading to face movement direction (responsive 18 rad/s)
                val turnK = 1f - kotlin.math.exp(-18.0f * dtc)
                newHeading = wrapAngle(heading + angleDelta(heading, moveHeading) * turnK)
            }

            val walkDist = (mag / JOYSTICK_GAIN).coerceIn(0f, 1f) * WALK_SPEED * dtc
            nx += sin(moveHeading) * walkDist
            nz += cos(moveHeading) * walkDist
        } else if (isFirstPerson) {
            newHeading = cameraYaw
        }

        val resolved = resolveCollisions(nx, nz)
        return AvatarStep(resolved.first, resolved.second, newHeading, null)
    }

    /** Resolves collisions against walkable deck perimeter, solid scenery, and buildings. */
    fun resolveCollisions(nx0: Float, nz0: Float): Pair<Float, Float> {
        var nx = nx0
        var nz = nz0
        // Stay on the deck.
        val maxR = AvatarWorld.DECK_RADIUS
        val r = hypot(nx, nz)
        if (r > maxR) {
            nx *= maxR / r
            nz *= maxR / r
        }

        // Solid scenery: slide out without triggering anything.
        for (c in AvatarWorld.solids) {
            if (c.halfZ <= 0f) {
                val rr = c.halfX + AvatarWorld.AVATAR_RADIUS
                val dx = nx - c.centerX
                val dz = nz - c.centerZ
                val d = hypot(dx, dz)
                if (d < rr) {
                    val k = if (d < 0.0001f) 1f else rr / d
                    nx = c.centerX + (if (d < 0.0001f) rr else dx * k)
                    nz = c.centerZ + (if (d < 0.0001f) 0f else dz * k)
                }
            } else {
                val ex = c.halfX + AvatarWorld.AVATAR_RADIUS
                val ez = c.halfZ + AvatarWorld.AVATAR_RADIUS
                val dx = nx - c.centerX
                val dz = nz - c.centerZ
                if (abs(dx) < ex && abs(dz) < ez) {
                    if (ex - abs(dx) < ez - abs(dz)) nx = c.centerX + (if (dx >= 0f) ex else -ex)
                    else nz = c.centerZ + (if (dz >= 0f) ez else -ez)
                }
            }
        }

        // Building collision: buildings are solid obstacles (no entry trigger on contact).
        for (c in AvatarWorld.colliders) {
            val ex = c.halfX + AvatarWorld.AVATAR_RADIUS
            val ez = c.halfZ + AvatarWorld.AVATAR_RADIUS
            val dx = nx - c.centerX
            val dz = nz - c.centerZ
            if (abs(dx) < ex + CONTACT_EPS && abs(dz) < ez + CONTACT_EPS) {
                // Push out along the axis of least penetration.
                val penX = ex - abs(dx)
                val penZ = ez - abs(dz)
                if (penX < penZ) nx = c.centerX + (if (dx >= 0f) ex else -ex)
                else nz = c.centerZ + (if (dz >= 0f) ez else -ez)
                break
            }
        }
        return Pair(nx, nz)
    }

    private const val CONTACT_EPS = 0.03f

    fun wrapAngle(a: Float): Float {
        var r = a
        val twoPi = (2.0 * PI).toFloat()
        while (r > PI) r -= twoPi
        while (r < -PI) r += twoPi
        return r
    }

    fun angleDelta(from: Float, to: Float): Float = wrapAngle(to - from)
}

/**
 * Drives the avatar's "Walk" clip from joystick input.
 *
 * Phase advances (looping) while the stick is outside the deadzone, at a rate scaled by stick
 * intensity. When the stick is released the cycle keeps running only until the next neutral pose
 * (both legs vertical, every [HALF_CYCLE] seconds), then holds exactly at the rest/idle pose.
 */
class WalkCycle(private val duration: Float = WALK_CLIP_SECONDS) {
    /** Current clip time in [0, duration). 0 is the neutral/idle pose. */
    var time: Float = 0f
        private set
    var isPlaying: Boolean = false
        private set

    private val half get() = duration / 2f

    /** @param intensity 0 when inside the deadzone, otherwise 0..1 shaped stick magnitude. */
    fun update(dt: Float, intensity: Float): Float {
        val dtc = dt.coerceIn(0f, 0.05f)
        if (intensity > 0f) {
            isPlaying = true
            val rate = 0.7f + 0.9f * intensity.coerceIn(0f, 1f)
            time = (time + dtc * rate) % duration
        } else if (isPlaying) {
            val prev = time
            val next = prev + dtc * 1.2f
            // Crossed a neutral pose (0 or half-cycle)? Snap to it and stop.
            if (kotlin.math.floor(next / half) > kotlin.math.floor(prev / half) || next >= duration) {
                time = 0f
                isPlaying = false
            } else {
                time = next
            }
        }
        return time
    }

    companion object {
        /** Must match WALK_DURATION in generate_osrs_guild_glb.py. */
        const val WALK_CLIP_SECONDS = 0.8f
        const val WALK_ANIMATION_NAME = "Walk"
    }
}

/**
 * Smooth Roblox-style chase camera with full 360° orbit yaw and full vertical look (pitch).
 * Operates in both Third-Person (orbit around avatar) and First-Person (avatar eye level).
 */
class ChaseCamera {
    var yaw: Float = 0f // direction camera looks along
    var pitch: Float = DEFAULT_THIRD_PERSON_PITCH // vertical look elevation angle in radians
    var distance: Float = 5.2f // local units behind avatar
    var height: Float = 2.4f // local units above ground
    var userYawOffset: Float = 0f
    var isFirstPerson: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    targetPitch = targetPitch.coerceIn(-1.2f, 1.2f)
                } else {
                    targetPitch = targetPitch.coerceIn(MIN_PITCH, MAX_PITCH)
                }
            }
        }
    private var initialised = false

    var targetYaw: Float = 0f
    var targetPitch: Float = DEFAULT_THIRD_PERSON_PITCH
    var targetDistance: Float = 5.2f

    companion object {
        /** Safe pitch limits (~ +/-85 degrees) preventing gimbal lock or camera flipping. */
        const val MIN_PITCH = -1.4835f // ~ -85 degrees (looking down)
        const val MAX_PITCH = 1.4835f  // ~ +85 degrees (looking up)
        const val DEFAULT_THIRD_PERSON_PITCH = -0.38f // ~ -22 degrees looking down at avatar
        const val MIN_DISTANCE = 2.0f
        const val MAX_DISTANCE = 11.0f
    }

    /**
     * Handles touch drag delta: maps X-axis movement to yaw and Y-axis movement to pitch.
     * Operates smoothly across both 1st and 3rd-person modes.
     */
    fun onDrag(dxPx: Float, dyPx: Float) {
        val sensX = if (isFirstPerson) 0.0035f else 0.0040f
        val sensY = if (isFirstPerson) 0.0035f else 0.0040f
        targetYaw = AvatarPhysics.wrapAngle(targetYaw - dxPx * sensX)
        targetPitch = (targetPitch - dyPx * sensY).coerceIn(MIN_PITCH, MAX_PITCH)
    }

    fun update(avatarHeading: Float, dt: Float) {
        if (!initialised) {
            yaw = avatarHeading
            targetYaw = avatarHeading
            pitch = if (isFirstPerson) 0f else DEFAULT_THIRD_PERSON_PITCH
            targetPitch = pitch
            initialised = true
        }
        val dtc = dt.coerceIn(0f, 0.05f)
        // Responsive 120Hz-tuned damping factor for smooth, jitter-free rotation and zoom
        val rotK = 1f - kotlin.math.exp(-18.0f * dtc)
        val zoomK = 1f - kotlin.math.exp(-12.0f * dtc)

        yaw = AvatarPhysics.wrapAngle(yaw + AvatarPhysics.angleDelta(yaw, targetYaw) * rotK)
        pitch = (pitch + (targetPitch - pitch) * rotK).coerceIn(MIN_PITCH, MAX_PITCH)
        distance = (distance + (targetDistance - distance) * zoomK).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
        height = (distance * 0.46f).coerceIn(1.6f, 5.0f)
    }

    fun adjustDistance(delta: Float) {
        if (isFirstPerson) return
        targetDistance = (targetDistance + delta).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    fun reset() {
        targetDistance = 5.2f
        targetPitch = if (isFirstPerson) 0f else DEFAULT_THIRD_PERSON_PITCH
        userYawOffset = 0f
    }

    /** Returns eye (x,y,z) and target (x,y,z) in local space. */
    fun pose(ax: Float, az: Float, out: FloatArray) {
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)
        val fx = sin(yaw) * cosPitch
        val fy = sinPitch
        val fz = cos(yaw) * cosPitch

        if (isFirstPerson) {
            // First Person: Camera placed at avatar's eyes looking forward along (yaw, pitch)
            val eyeX = ax
            val eyeY = AvatarWorld.GROUND_Y + 1.50f
            val eyeZ = az
            out[0] = eyeX
            out[1] = eyeY
            out[2] = eyeZ
            out[3] = eyeX + fx * 5.0f
            out[4] = eyeY + fy * 5.0f
            out[5] = eyeZ + fz * 5.0f
        } else {
            // Third Person: Camera orbits at [distance] around avatar chest at (yaw, pitch)
            val targetX = ax
            val targetY = AvatarWorld.GROUND_Y + 1.35f
            val targetZ = az

            val eyeX = targetX - fx * distance
            var eyeY = targetY - fy * distance
            val eyeZ = targetZ - fz * distance

            // Keep 3rd-person eye safely above the walkable deck surface
            eyeY = eyeY.coerceAtLeast(AvatarWorld.GROUND_Y + 0.25f)

            out[0] = eyeX
            out[1] = eyeY
            out[2] = eyeZ
            out[3] = targetX
            out[4] = targetY
            out[5] = targetZ
        }
    }
}

/** Shared mutable state between the joystick UI and the render loop. */
class AvatarController {
    @Volatile var stickX: Float = 0f
    @Volatile var stickY: Float = 0f
    var x: Float = AvatarWorld.SPAWN_X
    var z: Float = AvatarWorld.SPAWN_Z
    var heading: Float = AvatarWorld.SPAWN_HEADING
    val camera = ChaseCamera()
    val walkCycle = WalkCycle()

    /** Stick magnitude after deadzone/curve (0 = at rest). Drives the walk animation. */
    val stickIntensity: Float
        get() = maxOf(
            abs(AvatarPhysics.shapeAxis(stickX)),
            abs(AvatarPhysics.shapeAxis(stickY)),
        ) / AvatarPhysics.JOYSTICK_GAIN

    /** Debounce so one contact does not re-fire while the avatar is pressed against a wall. */
    private var lastTriggered: GuildBuilding? = null
    private var cooldownSec = 0f

    /** Advance the simulation. Returns a building when contact should open its module. */
    fun update(dt: Float): GuildBuilding? {
        val s = AvatarPhysics.stepRelative(
            x = x, z = z, heading = heading,
            stickX = stickX, stickY = stickY, dt = dt,
            cameraYaw = camera.yaw,
            isFirstPerson = camera.isFirstPerson,
        )
        x = s.x; z = s.z; heading = s.heading
        camera.update(heading, dt)
        walkCycle.update(dt, stickIntensity)
        cooldownSec = (cooldownSec - dt).coerceAtLeast(0f)
        val t = s.triggered
        if (t == null) {
            lastTriggered = null
            return null
        }
        if (cooldownSec > 0f && lastTriggered == t) return null
        lastTriggered = t
        cooldownSec = 1.5f
        return t
    }

    /** Back the avatar off the doorstep after entering a module so returning does not re-trigger. */
    fun retreatFromContact(building: GuildBuilding?) {
        val c = AvatarWorld.colliders.firstOrNull { it.building == building } ?: return
        val dx = x - c.centerX
        val dz = z - c.centerZ
        val len = hypot(dx, dz).coerceAtLeast(0.001f)
        val away = 1.2f
        x += dx / len * away
        z += dz / len * away
    }
}

/** Builds a column-major transform placing the avatar at (x,y,z) with the given heading (+Z forward). */
fun avatarTransform(x: Float, y: Float, z: Float, heading: Float): FloatArray {
    val c = cos(heading)
    val s = sin(heading)
    return floatArrayOf(
        c, 0f, -s, 0f,
        0f, 1f, 0f, 0f,
        s, 0f, c, 0f,
        x, y, z, 1f,
    )
}

/** Virtual joystick: horizontal = turn, vertical = walk. Reports raw -1..1 (up = +1 on Y). */
@Composable
fun AvatarJoystick(
    onChange: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                val radius = this.size.width / 2f
                detectDragGestures(
                    onDragStart = { knob = Offset.Zero },
                    onDragEnd = { knob = Offset.Zero; onChange(0f, 0f) },
                    onDragCancel = { knob = Offset.Zero; onChange(0f, 0f) },
                ) { change, drag ->
                    change.consume()
                    var next = knob + drag
                    val len = sqrt(next.x * next.x + next.y * next.y)
                    val maxLen = radius * 0.8f
                    if (len > maxLen) next = next * (maxLen / len)
                    knob = next
                    onChange(next.x / maxLen, -next.y / maxLen)
                }
            },
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            drawCircle(Color(0x66000000), radius = r)
            drawCircle(Color(0xFFFFD700), radius = r, style = Stroke(width = 3f))
            drawCircle(Color(0xCCFFD700), radius = r * 0.32f, center = center + knob)
        }
    }
}
