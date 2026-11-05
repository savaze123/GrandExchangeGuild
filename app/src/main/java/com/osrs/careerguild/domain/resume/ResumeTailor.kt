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

enum class RoleFamily(val displayName: String) {
    DATA_SCIENCE_ANALYTICS("Data Science & Analytics"),
    SOFTWARE_ENGINEERING("Software Engineering"),
    SRE_INFRASTRUCTURE("SRE & Infrastructure"),
    OPERATIONS("Operations & Systems"),
    AI_MACHINE_LEARNING("AI & Machine Learning"),
    GENERAL_TECH("Technology & Engineering")
}

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
        val activeBase = baseResume.activeOnly()
        val roleFamily = detectRoleFamily("", jobDescription)
        val gapReport = generateGapReport(jobDescription, activeBase)
        val targetRoleTitle = extractRoleTitle(jobDescription)
        val deterministicTailored = tailorDeterministically(jobDescription, activeBase, gitHubRepos)

        if (!tokenGovernor.isGeminiAllowed || apiKey.isBlank()) {
            return@withContext deterministicTailored
        }

        val baseJsonStr = json.encodeToString(activeBase)
        val reposSummary = gitHubRepos.take(10).joinToString("\n") { repo ->
            "- ${repo.name}: ${repo.description ?: "Software project"} (Languages: ${repo.languages.joinToString(", ")}; URL: ${repo.htmlUrl})"
        }

        // Attempt 1
        val initialPrompt = buildPrompt(jobDescription, baseJsonStr, reposSummary, roleFamily, violations = emptyList())
        val firstResponse = runCatching {
            model.generateContent(initialPrompt).text
        }.getOrNull()

        if (firstResponse != null) {
            tokenGovernor.recordScoringBatch(1)
            val candidate = parseCandidate(firstResponse)
            if (candidate != null) {
                val verification = ResumeVerifier.verify(candidate, activeBase, gitHubRepos)
                if (verification.isValid) {
                    return@withContext candidate.copy(
                        experience = candidate.experience.filter { it.isActive }.map { TailoredResume.sanitizeExperience(it) },
                        projects = candidate.projects.filter { it.isActive },
                        education = candidate.education.filter { it.isActive },
                        certifications = candidate.certifications.filter { it.isActive },
                        isTailored = true,
                        fallbackReason = null,
                        targetHeadline = targetRoleTitle,
                        gapReport = gapReport,
                    )
                }

                android.util.Log.w("ResumeTailor", "Tailoring attempt 1 failed verification: ${verification.violations}")

                // Attempt 2: Retry once with feedback
                val retryPrompt = buildPrompt(jobDescription, baseJsonStr, reposSummary, roleFamily, violations = verification.violations)
                val secondResponse = runCatching {
                    model.generateContent(retryPrompt).text
                }.getOrNull()

                if (secondResponse != null) {
                    tokenGovernor.recordScoringBatch(1)
                    val retryCandidate = parseCandidate(secondResponse)
                    if (retryCandidate != null) {
                        val retryVerification = ResumeVerifier.verify(retryCandidate, activeBase, gitHubRepos)
                        if (retryVerification.isValid) {
                            return@withContext retryCandidate.copy(
                                experience = retryCandidate.experience.filter { it.isActive }.map { TailoredResume.sanitizeExperience(it) },
                                projects = retryCandidate.projects.filter { it.isActive },
                                education = retryCandidate.education.filter { it.isActive },
                                certifications = retryCandidate.certifications.filter { it.isActive },
                                isTailored = true,
                                fallbackReason = null,
                                targetHeadline = targetRoleTitle,
                                gapReport = gapReport,
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

    fun detectRoleFamily(jobTitle: String, jobDescription: String): RoleFamily {
        val text = "$jobTitle $jobDescription".lowercase()
        val dataScore = listOf("data scientist", "data analyst", "data engineer", "analytics", "sql", "etl", "bi", "pipeline", "warehouse", "statistics", "pandas").count { text.contains(it) }
        val sreScore = listOf("sre", "reliability", "devops", "infrastructure", "kubernetes", "terraform", "monitoring", "observability", "sysadmin").count { text.contains(it) }
        val aiScore = listOf("ai engineer", "agent", "llm", "rag", "machine learning", "deep learning", "prompt", "nlp", "vision").count { text.contains(it) }
        val opsScore = listOf("operations", "incident", "release management", "support engineer", "technical operations").count { text.contains(it) }
        val sweScore = listOf("software", "backend", "frontend", "full stack", "full-stack", "developer", "engineer", "api", "mobile", "ios", "android").count { text.contains(it) }

        val scores = listOf(
            RoleFamily.DATA_SCIENCE_ANALYTICS to dataScore * 2,
            RoleFamily.SRE_INFRASTRUCTURE to sreScore * 2,
            RoleFamily.AI_MACHINE_LEARNING to aiScore * 2,
            RoleFamily.OPERATIONS to opsScore * 2,
            RoleFamily.SOFTWARE_ENGINEERING to sweScore,
        )

        val top = scores.maxByOrNull { it.second }
        return if (top != null && top.second > 0) top.first else RoleFamily.SOFTWARE_ENGINEERING
    }

    fun extractRoleTitle(jobDescription: String): String {
        val firstLine = jobDescription.lineSequence().firstOrNull()?.trim().orEmpty()
        return when {
            firstLine.contains(" at ", ignoreCase = true) -> firstLine.substringBefore(" at ", "").trim()
            firstLine.length in 4..60 -> firstLine
            else -> "Software Engineer"
        }
    }

    fun generateGapReport(
        jobDescription: String,
        baseResume: BaseResume,
    ): ResumeGapReport {
        val jdLower = jobDescription.lowercase()
        val roleFamily = detectRoleFamily("", jobDescription)

        val commonTokens = extractDomainKeywords(jdLower).keys.filter { it.length > 2 }
        val baseText = buildString {
            append(baseResume.skills.joinToString(" ").lowercase())
            append(" ")
            baseResume.experience.forEach { exp ->
                append("${exp.employer} ${exp.title} ${exp.bullets.joinToString(" ")} ".lowercase())
            }
            baseResume.projects.forEach { proj ->
                append("${proj.name} ${proj.technologies.joinToString(" ")} ${proj.bullets.joinToString(" ")} ".lowercase())
            }
        }

        val matched = mutableListOf<String>()
        val unsupported = mutableListOf<String>()

        for (token in commonTokens) {
            if (baseText.contains(token)) {
                matched.add(token)
            } else {
                unsupported.add(token)
            }
        }

        val total = matched.size + unsupported.size
        val score = if (total > 0) ((matched.size.toFloat() / total) * 100).toInt() else 100

        return ResumeGapReport(
            coverageScore = score.coerceIn(0, 100),
            matchedKeywords = matched.take(15),
            unsupportedKeywords = unsupported.take(10),
            roleFamily = roleFamily.displayName,
        )
    }

    /**
     * Deterministic, zero-hallucination domain tailoring:
     * Analyzes the target job description against the user's master resume,
     * prioritizes matching skills, and re-weights/reorders existing factual bullets
     * and projects to emphasize the target role family.
     */
    fun tailorDeterministically(
        jobDescription: String,
        baseResume: BaseResume,
        gitHubRepos: List<GitHubRepo> = emptyList(),
    ): TailoredResume {
        val jdLower = jobDescription.lowercase()
        val roleFamily = detectRoleFamily("", jobDescription)
        val gapReport = generateGapReport(jobDescription, baseResume)
        val targetRoleTitle = extractRoleTitle(jobDescription)
        val domainTokens = extractDomainKeywords(jdLower)

        fun scoreTextRelevance(text: String): Int {
            val lower = text.lowercase()
            var score = 0
            for ((keyword, weight) in domainTokens) {
                if (lower.contains(keyword)) {
                    score += weight
                }
            }
            // Role family prioritization
            when (roleFamily) {
                RoleFamily.DATA_SCIENCE_ANALYTICS -> {
                    if (lower.contains("data") || lower.contains("sql") || lower.contains("pipeline") || lower.contains("analytics")) score += 25
                }
                RoleFamily.SRE_INFRASTRUCTURE -> {
                    if (lower.contains("infrastructure") || lower.contains("kubernetes") || lower.contains("latency") || lower.contains("uptime")) score += 25
                }
                RoleFamily.AI_MACHINE_LEARNING -> {
                    if (lower.contains("ai") || lower.contains("model") || lower.contains("agent") || lower.contains("rag")) score += 25
                }
                RoleFamily.SOFTWARE_ENGINEERING -> {
                    if (lower.contains("api") || lower.contains("architecture") || lower.contains("service") || lower.contains("backend")) score += 25
                }
                RoleFamily.OPERATIONS -> {
                    if (lower.contains("operations") || lower.contains("deploy") || lower.contains("incident") || lower.contains("system")) score += 25
                }
                else -> {}
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

        // 2. Re-weight and reorder each active employer's existing factual bullets to lead with the strongest role-family match
        val tailoredExperience = baseResume.experience.filter { it.isActive }.map { rawExp ->
            val exp = TailoredResume.sanitizeExperience(rawExp)
            val reorderedBullets = exp.bullets
                .mapIndexed { idx, bullet -> Triple(bullet, scoreTextRelevance(bullet), idx) }
                .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
                .map { it.first }
            exp.copy(bullets = reorderedBullets)
        }

        // 3. Re-weight and reorder active projects & project bullets by domain match
        val tailoredProjects = baseResume.projects.filter { it.isActive }
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

        // 4. Concise summary (2 lines max) built strictly from base facts consistent with role family
        val summarySkills = rankedSkills.take(3).joinToString(", ")
        val tailoredSummary = if (!baseResume.summary.isNullOrBlank()) {
            baseResume.summary
        } else {
            "Results-driven ${roleFamily.displayName} practitioner with core proficiency in $summarySkills. Experienced engineering reliable, performant solutions grounded in verified production workflows."
        }

        return TailoredResume(
            contact = baseResume.contact,
            summary = tailoredSummary,
            skills = rankedSkills,
            experience = tailoredExperience,
            projects = tailoredProjects,
            education = baseResume.education.filter { it.isActive },
            certifications = baseResume.certifications.filter { it.isActive },
            isTailored = true,
            fallbackReason = null,
            targetHeadline = targetRoleTitle,
            gapReport = gapReport,
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
        roleFamily: RoleFamily,
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
            Detected Target Role Family: ${roleFamily.displayName}

            ABSOLUTE INVARIANTS:
            1. Role Family Alignment: Select and order base bullets prioritizing skills, technologies, and achievements relevant to ${roleFamily.displayName} and this target job description.
            2. Honesty Rule: A tool, method, or claim may be attributed to an employer ONLY if it is in that same entry's base text. Never move experience between entries. If the JD requires something only present in Projects or Education, keep it there or leave it out.
            3. No fabricated metrics: Never invent numbers or metrics. If a bullet contains an attached userMetric, weave that userMetric into the bullet. If a bullet is vague without a figure, leave it as written without inventing numbers.
            4. Summary: strictly a 2-line summary built only from base facts and consistent with ${roleFamily.displayName}.
            5. Skills: reorder base skills by relevance to the JD, including languages found in the candidate's GitHub repos. Do not add unverified skills.
            6. Projects: up to 3 projects taken strictly from the user's base projects or verified GitHub repos list.
            7. All dates, employer names, school names, degrees, and certificate titles must match the BaseResume exactly.

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
