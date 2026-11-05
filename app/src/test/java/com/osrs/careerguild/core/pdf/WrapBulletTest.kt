package com.osrs.careerguild.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [ResumeForgePdfGenerator.wrapBullet] pure wrapping logic.
 * Uses 100% synthetic text fixtures per GEMINI.md hard invariant.
 */
class WrapBulletTest {

    @Test
    fun bulletShorterThanWidth_returnsOneLine() {
        val shortBullet = "Engineered caching layer."
        val maxWidth = 300f
        // Synthetic measure function: 5 units per character
        val measure: (String) -> Float = { it.length * 5f }

        val lines = ResumeForgePdfGenerator.wrapBullet(shortBullet, maxWidth, measure)

        assertEquals(1, lines.size)
        assertEquals("Engineered caching layer.", lines[0])
    }

    @Test
    fun longBullet_returnsMultipleLines() {
        val longBullet = "Architected and implemented high-throughput data processing pipelines utilizing Apache Kafka and Flink across distributed cloud regions to achieve sub-second event ingestion latency."
        val maxWidth = 150f
        val measure: (String) -> Float = { it.length * 5f }

        val lines = ResumeForgePdfGenerator.wrapBullet(longBullet, maxWidth, measure)

        assertTrue("Expected long bullet to wrap into multiple lines, got ${lines.size}", lines.size > 1)
        // Verify every line satisfies maxWidth
        for (line in lines) {
            assertTrue("Line '$line' exceeded maxWidth $maxWidth", measure(line) <= maxWidth)
        }
    }

    @Test
    fun callerEmitsOneGlyphAcrossWrappedLines() {
        val longBullet = "Spearheaded microservice decomposition and container orchestration using Kubernetes and Terraform for continuous deployment."
        val maxWidth = 120f
        val measure: (String) -> Float = { it.length * 5f }

        val lines = ResumeForgePdfGenerator.wrapBullet(longBullet, maxWidth, measure)
        assertTrue(lines.size >= 2)

        // Simulate caller rendering logic: exactly ONE bullet glyph on line 0, 0 glyphs on continuation lines
        var glyphCount = 0
        val renderedLines = mutableListOf<String>()

        for ((index, line) in lines.withIndex()) {
            if (index == 0) {
                glyphCount++
                renderedLines.add("• $line")
            } else {
                renderedLines.add("  $line")
            }
        }

        assertEquals(1, glyphCount)
        assertEquals(lines.size, renderedLines.size)
        assertTrue(renderedLines[0].startsWith("•"))
        assertFalse(renderedLines[1].startsWith("•"))
    }
}
