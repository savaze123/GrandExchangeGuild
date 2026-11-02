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
        val deterministicTailored = tailorDeterministically(jobDescription, baseResume, gitHubRepos)

        if (!tokenGovernor.isGeminiAllowed || apiKey.isBlank()) {
            return@withContext deterministicTailored
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
                    return@withContext candidate.copy(
                        experience = candidate.experience.map { TailoredResume.sanitizeExperience(it) },
                        isTailored = true,
                        fallbackReason = null,
                    )
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
                            return@withContext retryCandidate.copy(
                                experience = retryCandidate.experience.map { TailoredResume.sanitizeExperience(it) },
                                isTailored = true,
                                fallbackReason = null,
                            )
                        }
                        android.util.Log.w("ResumeTailor", "Tailoring attempt 2 failed verification: ${retryVerification.violations}")
                    }
                }
            }
        }

        // Fail-safe: return deterministically re-weighted factual resume (100% grounded in BaseResume)
        deterministicTailored
    }

    /**
     * Deterministic, zero-hallucination domain tailoring:
     * Analyzes the target job description against the user's master resume,
     * prioritizes matching skills, and re-weights/reorders existing factual bullets
     * and projects to emphasize the target role's domain (Data/SQL, AI/Agents, or Backend/SWE).
     */
    fun tailorDeterministically(
        jobDescription: String,
        baseResume: BaseResume,
        gitHubRepos: List<GitHubRepo> = emptyList(),
    ): TailoredResume {
        val jdLower = jobDescription.lowercase()
        val domainTokens = extractDomainKeywords(jdLower)

        fun scoreTextRelevance(text: String): Int {
            val lower = text.lowercase()
            var score = 0
            for ((keyword, weight) in domainTokens) {
                if (lower.contains(keyword)) {
                    score += weight
                }
            }
            return score
        }

        // 1. Rank & prioritize existing factual skills by relevance to the target JD
        val repoLanguages = gitHubRepos.flatMap { it.languages }.distinct()
        val allCandidateSkills = (baseResume.skills + repoLanguages).distinctBy { it.lowercase().trim() }
        val rankedSkills = allCandidateSkills.sortedByDescending { skill ->
            val sLower = skill.lowercase()
            val directMatch = if (jdLower.contains(sLower)) 50 else 0
            directMatch + scoreTextRelevance(sLower)
        }

        // 2. Re-weight and reorder each employer's existing factual bullets to lead with the strongest domain match
        val tailoredExperience = baseResume.experience.map { rawExp ->
            val exp = TailoredResume.sanitizeExperience(rawExp)
            val reorderedBullets = exp.bullets
                .mapIndexed { idx, bullet -> Triple(bullet, scoreTextRelevance(bullet), idx) }
                .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
                .map { it.first }
            exp.copy(bullets = reorderedBullets)
        }

        // 3. Re-weight and reorder projects & project bullets by domain match
        val tailoredProjects = baseResume.projects
            .mapIndexed { pIdx, proj ->
                val reorderedBullets = proj.bullets
                    .mapIndexed { bIdx, bullet -> Triple(bullet, scoreTextRelevance(bullet), bIdx) }
                    .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
                    .map { it.first }
                val projScore = scoreTextRelevance("${proj.name} ${proj.technologies.joinToString(" ")} ${proj.bullets.joinToString(" ")}")
                Triple(proj.copy(bullets = reorderedBullets), projScore, pIdx)
            }
            .sortedWith(compareByDescending<Triple<com.osrs.careerguild.core.resume.ResumeProject, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }

        return TailoredResume(
            contact = baseResume.contact,
            summary = baseResume.summary ?: "",
            skills = rankedSkills,
            experience = tailoredExperience,
            projects = tailoredProjects,
            education = baseResume.education,
            certifications = baseResume.certifications,
            isTailored = true,
            fallbackReason = null,
        )
    }

    /**
     * Auto-selects the highest-matching skills from the user's Master Resume and Skill Altar
     * for the target job description without requiring manual toggling.
     */
    fun selectTopInfusedSkills(
        jobTitle: String,
        jobDescription: String,
        jobMatchedSkills: List<String>,
        baseResumeSkills: List<String>,
        altarActiveSkills: List<String>,
        maxCount: Int = 10,
    ): List<String> {
        val combinedPool = (jobMatchedSkills + baseResumeSkills + altarActiveSkills)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        val targetText = "$jobTitle $jobDescription ${jobMatchedSkills.joinToString(" ")}".lowercase()
        val domainWeights = extractDomainKeywords(targetText)

        val scored = combinedPool.mapIndexed { index, skill ->
            val sLower = skill.lowercase()
            var score = 0
            if (jobMatchedSkills.any { it.equals(skill, ignoreCase = true) }) score += 100
            if (targetText.contains(sLower)) score += 60
            for ((kw, weight) in domainWeights) {
                if (sLower.contains(kw) || kw.contains(sLower)) {
                    score += weight
                }
            }
            if (baseResumeSkills.any { it.equals(skill, ignoreCase = true) }) score += 15
            if (altarActiveSkills.any { it.equals(skill, ignoreCase = true) }) score += 10
            Triple(skill, score, index)
        }

        return scored
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
            .take(maxCount)
            .map { it.first }
    }

    private fun extractDomainKeywords(jdLower: String): Map<String, Int> {
        val weights = mutableMapOf<String, Int>()
        val isDataRole = jdLower.contains("data") || jdLower.contains("sql") || jdLower.contains("etl") ||
            jdLower.contains("pipeline") || jdLower.contains("database") || jdLower.contains("analytics")
        val isAiRole = jdLower.contains("ai") || jdLower.contains("llm") || jdLower.contains("agent") ||
            jdLower.contains("machine learning") || jdLower.contains("rag") || jdLower.contains("genai")
        val isBackendSweRole = jdLower.contains("software") || jdLower.contains("backend") || jdLower.contains("full-stack") ||
            jdLower.contains("full stack") || jdLower.contains("api") || jdLower.contains("engineer")

        if (isDataRole) {
            listOf("pipeline", "sql", "postgresql", "mysql", "mongodb", "database", "data", "synchronization", "etl", "modeling", "airflow", "python").forEach {
                weights[it] = (weights[it] ?: 0) + 18
            }
        }
        if (isAiRole) {
            listOf("ai", "agent", "agentic", "llm", "rag", "gemini", "prompt", "python", "automation", "copilot", "cursor").forEach {
                weights[it] = (weights[it] ?: 0) + 18
            }
        }
        if (isBackendSweRole) {
            listOf("api", "full stack", "full-stack", "python", "javascript", "kotlin", "modular", "architecture", "scalability", "reliability", "integration", "docker", "git", "ci/cd").forEach {
                weights[it] = (weights[it] ?: 0) + 15
            }
        }
        // Also extract individual significant tokens from the JD
        Regex("[a-z0-9+#./-]{3,}").findAll(jdLower).map { it.value }.forEach { token ->
            if (token !in setOf("the", "and", "for", "with", "you", "will", "are", "our", "that", "this", "from", "have", "work", "team", "role")) {
                weights[token] = (weights[token] ?: 0) + 4
            }
        }
        return weights
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
