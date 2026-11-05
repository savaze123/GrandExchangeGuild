package com.osrs.careerguild.core.resume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ResumeBulletUtils] verifying fragmented bullet merging and continuation logic.
 * Uses 100% synthetic text fixtures per GEMINI.md hard invariant.
 */
class ResumeBulletUtilsTest {

    @Test
    fun sentenceSplitAcrossThreeLines_becomesOneBullet() {
        val lines = listOf(
            "Engineered high-concurrency microservice handling 10k requests/sec,",
            "reducing tail latency by 35%",
            "and decreasing memory consumption.",
        )

        val result = ResumeBulletUtils.mergeFragments(lines)

        assertEquals(1, result.size)
        assertEquals(
            "Engineered high-concurrency microservice handling 10k requests/sec, reducing tail latency by 35% and decreasing memory consumption.",
            result[0]
        )
    }

    @Test
    fun bulletEndingInDanglingWord_mergesWithNext() {
        val lines = listOf(
            "Led migration of legacy monolithic backend to",
            "Kubernetes clusters across AWS multi-region infrastructure.",
        )

        val result = ResumeBulletUtils.mergeFragments(lines)

        assertEquals(1, result.size)
        assertEquals(
            "Led migration of legacy monolithic backend to Kubernetes clusters across AWS multi-region infrastructure.",
            result[0]
        )
    }

    @Test
    fun twoGenuineSeparateBullets_staySeparate() {
        val lines = listOf(
            "Developed data pipeline with Apache Spark and Kafka.",
            "Architected distributed key-value store using RocksDB and gRPC.",
        )

        val result = ResumeBulletUtils.mergeFragments(lines)

        assertEquals(2, result.size)
        assertEquals("Developed data pipeline with Apache Spark and Kafka.", result[0])
        assertEquals("Architected distributed key-value store using RocksDB and gRPC.", result[1])
    }

    @Test
    fun hyphenatedLineBreak_isRejoinedWithoutExtraSpace() {
        val lines = listOf(
            "Implemented automated test-driven develop-",
            "ment workflow for continuous integration.",
        )

        val result = ResumeBulletUtils.mergeFragments(lines)

        assertEquals(1, result.size)
        assertEquals(
            "Implemented automated test-driven development workflow for continuous integration.",
            result[0]
        )
    }

    @Test
    fun isFragment_identifiesFragmentsCorrectly() {
        // Lowercase start is a fragment
        assertTrue(ResumeBulletUtils.isFragment("reducing latency by 20%"))
        // Short text without leading verb is a fragment
        assertTrue(ResumeBulletUtils.isFragment("with high accuracy"))
        // Genuine full bullet with leading verb is NOT a fragment
        assertFalse(ResumeBulletUtils.isFragment("Designed real-time metrics telemetry dashboard."))
    }
}
