package com.osrs.careerguild.domain.resume

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.osrs.careerguild.BuildConfig
import com.osrs.careerguild.core.github.GitHubRepo
import com.osrs.careerguild.core.resume.BaseResume
import com.osrs.careerguild.core.token.TokenGovernor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Orchestrates truthful resume tailoring using Gemini 1.5 Flash gated by TokenGovernor.
 * Verified with [ResumeVerifier] with single retry upon violation.
 */
class ResumeTailor(
    private val tokenGovernor: TokenGovernor,
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true },
) {

    private val model: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.0f
                responseMimeType = "application/json"
            },
        )
    }

    suspend fun tailor(
        jobDescription: String,
        baseResume: BaseResume,
        gitHubRepos: List<GitHubRepo> = emptyList(),
    ): TailoredResume = withContext(Dispatchers.IO) {
        if (!tokenGovernor.isGeminiAllowed || apiKey.isBlank()) {
            return@withContext TailoredResume.fromBaseResume(
                base = baseResume,
                reason = "Gemini token budget reached or offline mode. Displaying factual base resume.",
            )
        }

        val baseJsonStr = json.encodeToString(baseResume)
        val reposSummary = gitHubRepos.take(10).joinToString("\n") { repo ->
            "- ${repo.name}: ${repo.description ?: "Software project"} (Languages: ${repo.languages.joinToString(", ")}; URL: ${repo.htmlUrl})"
        }

        // Attempt 1
        val initialPrompt = buildPrompt(jobDescription, baseJsonStr, reposSummary, violations = emptyList())
        val firstResponse = runCatching {
            model.generateContent(initialPrompt).text
        }.getOrNull()

        if (firstResponse != null) {
            tokenGovernor.recordScoringBatch(1)
            val candidate = parseCandidate(firstResponse)
            if (candidate != null) {
                val verification = ResumeVerifier.verify(candidate, baseResume, gitHubRepos)
                if (verification.isValid) {
                    return@withContext candidate.copy(isTailored = true, fallbackReason = null)
                }

                android.util.Log.w("ResumeTailor", "Tailoring attempt 1 failed verification: ${verification.violations}")

                // Attempt 2: Retry once with feedback
                val retryPrompt = buildPrompt(jobDescription, baseJsonStr, reposSummary, violations = verification.violations)
                val secondResponse = runCatching {
                    model.generateContent(retryPrompt).text
                }.getOrNull()

                if (secondResponse != null) {
                    tokenGovernor.recordScoringBatch(1)
                    val retryCandidate = parseCandidate(secondResponse)
                    if (retryCandidate != null) {
                        val retryVerification = ResumeVerifier.verify(retryCandidate, baseResume, gitHubRepos)
                        if (retryVerification.isValid) {
                            return@withContext retryCandidate.copy(isTailored = true, fallbackReason = null)
                        }
                        android.util.Log.w("ResumeTailor", "Tailoring attempt 2 failed verification: ${retryVerification.violations}")
                    }
                }
            }
        }

        // Fail-safe: fall back to the untailored base resume with explicit reason
        TailoredResume.fromBaseResume(
            base = baseResume,
            reason = "Strict fact-checking rejected hallucinated details. Reverted safely to original base resume.",
        )
    }

    private fun buildPrompt(
        jobDescription: String,
        baseJsonStr: String,
        reposSummary: String,
        violations: List<String>,
    ): String {
        val violationsBlock = if (violations.isNotEmpty()) {
            """
            ATTENTION: Your previous output was REJECTED by our deterministic verifier due to the following factual violations:
            ${violations.joinToString("\n") { "- $it" }}

            Strictly correct these violations. Do not include any new numbers, employers, degrees, certs, or tools!
            """.trimIndent()
        } else ""

        return """
            You are a strict, factual ATS Resume Tailor.
            Your task is to tailor the candidate's verified BaseResume to the provided Job Description.

            ABSOLUTE INVARIANTS:
            1. No new employers, titles, dates, degrees, certifications, metrics, numbers, tools, or scope claims.
            2. A bullet under an employer may ONLY select, reorder, or lightly shorten that employer's OWN base bullets.
            3. Summary: strictly a 2-line summary built only from base facts.
            4. Skills: reorder base skills by relevance to the JD, including languages found in the candidate's GitHub repos.
            5. Projects: up to 3 projects taken strictly from the user's base projects or verified GitHub repos list.
            6. All dates, employer names, school names, degrees, and certificate titles must match the BaseResume exactly.

            $violationsBlock

            Job Description:
            $jobDescription

            Base Resume (Ground Truth JSON):
            $baseJsonStr

            Candidate Public GitHub Repositories:
            $reposSummary

            Output strictly JSON matching the TailoredResume schema (contact, summary, skills, experience, projects, education, certifications).
        """.trimIndent()
    }

    private fun parseCandidate(responseText: String): TailoredResume? {
        return runCatching {
            val clean = responseText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            json.decodeFromString<TailoredResume>(clean)
        }.getOrNull()
    }
}
