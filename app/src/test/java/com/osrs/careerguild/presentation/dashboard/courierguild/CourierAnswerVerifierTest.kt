package com.osrs.careerguild.presentation.dashboard.courierguild

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourierAnswerVerifierTest {

    private val syntheticFacts = listOf(
        FactItem(
            id = "F1",
            source = "Target Role",
            text = "Applying for Backend Software Engineer at SyntheticCorp.",
        ),
        FactItem(
            id = "F2",
            source = "BaseResume:Experience(AcmeCloud)",
            text = "Worked as Software Engineer at AcmeCloud (2023 - Present): Built Kotlin and PostgreSQL microservices handling 1200 requests per second with Docker and GitHub Actions.",
        ),
        FactItem(
            id = "F3",
            source = "BaseResume:Project(QueryFlow)",
            text = "Project QueryFlow (Kotlin, SQLite, Coroutines): Designed an offline-first local query engine with 98% unit test coverage.",
        ),
        FactItem(
            id = "F4",
            source = "BaseResume:Education(StateTechUniversity)",
            text = "Degree: B.S. in Computer Science from StateTechUniversity.",
        ),
    )

    @Test
    fun `accepts humble, truthful, grounded answer citing valid facts`() {
        val candidate = GroundedSynthesisResult(
            answer = "I am excited to apply for the Backend Software Engineer role at SyntheticCorp. " +
                "In my work as Software Engineer at AcmeCloud, I built Kotlin and PostgreSQL microservices handling 1200 requests per second. " +
                "I also built QueryFlow using Coroutines, and I would be grateful to bring this experience to your team.",
            usedFactIds = listOf("F1", "F2", "F3"),
            unsupported = false,
        )

        val result = CourierAnswerVerifier.verify(
            synthesis = candidate,
            factSheet = syntheticFacts,
            maxLength = 500,
        )

        assertTrue("Expected valid grounded answer, but got violations: ${result.violations}", result.isValid)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun `rejects invented numbers and metrics not present in cited facts`() {
        val candidate = GroundedSynthesisResult(
            answer = "At AcmeCloud, I built Kotlin microservices handling 50000 requests per second and reduced latency by 45%. " +
                "I am eager to contribute to SyntheticCorp.",
            usedFactIds = listOf("F1", "F2"),
            unsupported = false,
        )

        val result = CourierAnswerVerifier.verify(
            synthesis = candidate,
            factSheet = syntheticFacts,
            maxLength = 500,
        )

        assertFalse(result.isValid)
        assertTrue(result.violations.any { it.contains("50000") || it.contains("45%") })
    }

    @Test
    fun `rejects ungrounded technology terms not in cited facts`() {
        val candidate = GroundedSynthesisResult(
            answer = "In my role at AcmeCloud, I architected Kubernetes clusters with Terraform and Rust. " +
                "I would love to join SyntheticCorp as a Backend Software Engineer.",
            usedFactIds = listOf("F1", "F2"),
            unsupported = false,
        )

        val result = CourierAnswerVerifier.verify(
            synthesis = candidate,
            factSheet = syntheticFacts,
            maxLength = 500,
        )

        assertFalse(result.isValid)
        assertTrue(result.violations.any { it.contains("Ungrounded technology", ignoreCase = true) })
    }

    @Test
    fun `rejects ungrounded proper nouns such as invented employers`() {
        val candidate = GroundedSynthesisResult(
            answer = "During my time at MegaCorpGlobal, I built Kotlin and PostgreSQL services. " +
                "I am excited to apply to SyntheticCorp.",
            usedFactIds = listOf("F1", "F2"),
            unsupported = false,
        )

        val result = CourierAnswerVerifier.verify(
            synthesis = candidate,
            factSheet = syntheticFacts,
            maxLength = 500,
        )

        assertFalse(result.isValid)
        assertTrue(result.violations.any { it.contains("MegaCorpGlobal") })
    }

    @Test
    fun `rejects AI self-reference and fabrication patterns`() {
        val candidate = GroundedSynthesisResult(
            answer = "As an AI language model, I led a team of 12 engineers in Kotlin and PostgreSQL at AcmeCloud. " +
                "I am excited about SyntheticCorp.",
            usedFactIds = listOf("F1", "F2"),
            unsupported = false,
        )

        val result = CourierAnswerVerifier.verify(
            synthesis = candidate,
            factSheet = syntheticFacts,
            maxLength = 500,
        )

        assertFalse(result.isValid)
        assertTrue(result.violations.any { it.contains("Disallowed AI", ignoreCase = true) || it.contains("Fabricated team size", ignoreCase = true) })
    }

    @Test
    fun `rejects answer exceeding field maxlength`() {
        val candidate = GroundedSynthesisResult(
            answer = "I am excited to apply for the Backend Software Engineer role at SyntheticCorp. " +
                "At AcmeCloud, I built Kotlin and PostgreSQL microservices with Docker.",
            usedFactIds = listOf("F1", "F2"),
            unsupported = false,
        )

        val result = CourierAnswerVerifier.verify(
            synthesis = candidate,
            factSheet = syntheticFacts,
            maxLength = 60,
        )

        assertFalse(result.isValid)
        assertTrue(result.violations.any { it.contains("exceeds maxlength") })
    }
}
