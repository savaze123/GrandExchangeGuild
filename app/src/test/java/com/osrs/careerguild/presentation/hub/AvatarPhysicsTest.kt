package com.osrs.careerguild.presentation.hub

import com.osrs.careerguild.domain.model.GuildBuilding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class AvatarPhysicsTest {
    private fun walkToward(tx: Float, tz: Float): Pair<AvatarStep, Boolean> {
        var x = AvatarWorld.SPAWN_X; var z = AvatarWorld.SPAWN_Z; var h = AvatarWorld.SPAWN_HEADING
        var last = AvatarStep(x, z, h, null)
        repeat(3000) {
            val want = atan2(tx - x, tz - z)
            val d = AvatarPhysics.angleDelta(h, want)
            // heading increases with +stickX; pick sign accordingly
            val sx = if (abs(d) < 0.05f) 0f else if (d > 0) 1f else -1f
            last = AvatarPhysics.step(x, z, h, sx, if (abs(d) < 0.3f) 1f else 0f, 0.016f)
            x = last.x; z = last.z; h = last.heading
            if (last.triggered != null) return last to true
        }
        return last to false
    }

    @Test fun buildingsAreSolidObstaclesWithoutBumpEntry() {
        for (c in AvatarWorld.colliders) {
            val (step, _) = walkToward(c.centerX, c.centerZ)
            assertNull("Bump entry must never trigger", step.triggered)
        }
        // Walking directly toward Courier Guild stops outside its solid perimeter
        var x = AvatarWorld.SPAWN_X; var z = AvatarWorld.SPAWN_Z; var h = AvatarWorld.SPAWN_HEADING
        repeat(500) {
            val want = atan2(7.2f - x, 7.2f - z)
            val d = AvatarPhysics.angleDelta(h, want)
            val sx = if (abs(d) < 0.05f) 0f else if (d > 0) 1f else -1f
            val step = AvatarPhysics.step(x, z, h, sx, if (abs(d) < 0.3f) 1f else 0f, 0.016f)
            x = step.x; z = step.z; h = step.heading
            assertNull("Bump entry must never trigger", step.triggered)
        }
        // Must remain outside the 3.0f half-extents
        assertTrue(abs(x - 7.2f) >= 3.0f + AvatarWorld.AVATAR_RADIUS - 0.01f || abs(z - 7.2f) >= 3.0f + AvatarWorld.AVATAR_RADIUS - 0.01f)
    }

    @Test fun neverClipsInsideBuildingOrFountain() {
        val s = AvatarPhysics.step(7.2f, 7.2f, 0f, 0f, 0f, 0.016f)
        assertTrue(abs(s.x - 7.2f) >= 3.0f + AvatarWorld.AVATAR_RADIUS - 0.001f || abs(s.z - 7.2f) >= 3.0f + AvatarWorld.AVATAR_RADIUS - 0.001f)
        val f = AvatarPhysics.step(0.5f, 0.5f, 0f, 0f, 0f, 0.016f)
        assertTrue(hypot(f.x, f.z) >= 3.7f + AvatarWorld.AVATAR_RADIUS - 0.001f)
    }

    @Test fun deadzoneAndReducedSensitivity() {
        assertEquals(0f, AvatarPhysics.shapeAxis(0.1f), 0f)
        assertTrue(AvatarPhysics.shapeAxis(1f) <= AvatarPhysics.JOYSTICK_GAIN + 1e-6f)
        assertTrue(AvatarPhysics.shapeAxis(0.5f) < 0.5f * AvatarPhysics.JOYSTICK_GAIN)
    }

    @Test fun staysOnDeck() {
        val s = AvatarPhysics.step(0f, 12.6f, 0f, 0f, 1f, 0.05f)
        assertTrue(hypot(s.x, s.z) <= AvatarWorld.DECK_RADIUS + 0.001f)
        assertNull(AvatarPhysics.step(0f, 6.5f, 0f, 0f, 0f, 0.016f).triggered)
    }
}

class WalkCycleTest {
    @Test fun loopsWhileActiveAndSettlesToIdleOnRelease() {
        val w = WalkCycle()
        assertEquals(0f, w.time, 0f)
        repeat(100) { w.update(0.016f, 1f) }
        assertTrue(w.isPlaying)
        assertTrue(w.time in 0f..WalkCycle.WALK_CLIP_SECONDS)
        repeat(200) { w.update(0.016f, 0f) }
        assertTrue(!w.isPlaying)
        assertEquals(0f, w.time, 0f)
    }

    @Test fun staysIdleWithoutInput() {
        val w = WalkCycle()
        repeat(50) { w.update(0.016f, 0f) }
        assertEquals(0f, w.time, 0f)
        assertTrue(!w.isPlaying)
    }

    @Test fun firstPersonPosePlacesCameraAtEyeLevel() {
        val cam = ChaseCamera()
        cam.isFirstPerson = true
        cam.update(0f, 0.016f)
        val pose = FloatArray(6)
        cam.pose(0f, 0f, pose)
        // Eye height must match avatar eye level (ground + 1.50f)
        assertEquals(AvatarWorld.GROUND_Y + 1.50f, pose[1], 0.01f)
        // Looking forward along +Z when heading is 0
        assertTrue(pose[5] > pose[2])
    }
}
