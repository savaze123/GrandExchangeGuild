package com.osrs.careerguild.core.resume

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.osrs.careerguild.BuildConfig
import com.osrs.careerguild.core.token.TokenGovernor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Result of structured resume parsing and deterministic verification.
 */
data class StructuredResumeResult(
    val resume: BaseResume,
    val droppedItems: List<String> = emptyList(),
)

/**
 * Transcribes raw extracted resume text into a strongly-typed [BaseResume] using Gemini 1.5 Flash,
 * followed by a deterministic, non-LLM ground-truth verification check.
 *
 * Hard Verification Invariant:
 * Every employer, title, date string, school, and certification in the structured output MUST
 * appear (after case and whitespace normalization) in the raw text.
 * Anything failing this verification is dropped and flagged.
 */
class ResumeStructurer(
    private val tokenGovernor: TokenGovernor,
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true },
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

    suspend fun structure(rawText: String): Result<StructuredResumeResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (rawText.isBlank()) {
                throw IllegalArgumentException("Raw resume text is empty.")
            }

            val heuristic = fallbackDeterministicStructure(rawText)

            if (!tokenGovernor.isGeminiAllowed || apiKey.isBlank()) {
                return@runCatching heuristic
            }

            try {
                val prompt = buildStructuringPrompt(rawText)
                val responseText = model.generateContent(prompt).text
                    ?: throw IllegalStateException("Empty response received from Gemini.")

                tokenGovernor.recordScoringBatch(1)

                val rawParsed = parseJsonResponse(responseText)
                val parsedResume = rawParsed.copy(
                    experience = rawParsed.experience.map { it.copy(bullets = ResumeBulletUtils.mergeFragments(it.bullets)) },
                    projects = rawParsed.projects.map { it.copy(bullets = ResumeBulletUtils.mergeFragments(it.bullets)) },
                )
                val verified = verifyExtraction(rawText, parsedResume)
                val vResume = verified.resume
                val hResume = heuristic.resume

                val mergedSummary = vResume.summary?.takeIf {
                    it.isNotBlank() && !it.contains("@") && !it.contains("linkedin.com", ignoreCase = true)
                } ?: hResume.summary

                val mergedSkills = if (vResume.skills.size >= hResume.skills.size && vResume.skills.size > 2) {
                    vResume.skills
                } else {
                    (hResume.skills + vResume.skills).distinct()
                }

                val mergedExperience = (if (vResume.experience.isNotEmpty()) vResume.experience else hResume.experience)
                    .map { it.copy(bullets = ResumeBulletUtils.mergeFragments(it.bullets)) }
                val mergedEducation = if (vResume.education.isNotEmpty()) vResume.education else hResume.education
                val mergedProjects = (if (vResume.projects.isNotEmpty()) vResume.projects else hResume.projects)
                    .map { it.copy(bullets = ResumeBulletUtils.mergeFragments(it.bullets)) }
                val mergedCerts = if (vResume.certifications.isNotEmpty()) vResume.certifications else hResume.certifications

                StructuredResumeResult(
                    resume = vResume.copy(
                        contact = if (!vResume.contact.name.isNullOrBlank()) vResume.contact else hResume.contact,
                        summary = mergedSummary,
                        skills = mergedSkills,
                        experience = mergedExperience,
                        education = mergedEducation,
                        projects = mergedProjects,
                        certifications = mergedCerts,
                    ),
                    droppedItems = verified.droppedItems,
                )
            } catch (e: Exception) {
                android.util.Log.w("ResumeStructurer", "Gemini structuring unavailable, using offline heuristic parser: ${e.message}")
                heuristic
            }
        }
    }

    private fun buildStructuringPrompt(rawText: String): String {
        return """
            Extract verbatim. Do not infer, summarize, correct, or add anything. Copy employer names, titles, dates, school, degree, certifications, projects, and bullets exactly as written. Use null for anything missing.

            Return JSON matching this schema:
            {
              "contact": {
                "name": string or null,
                "email": string or null,
                "phone": string or null,
                "location": string or null,
                "github": string or null,
                "linkedin": string or null,
                "website": string or null
              },
              "summary": string or null,
              "experience": [
                {
                  "employer": string,
                  "title": string,
                  "location": string or null,
                  "startDate": string or null,
                  "endDate": string or null,
                  "isCurrent": boolean,
                  "bullets": [string]
                }
              ],
              "education": [
                {
                  "school": string,
                  "degree": string,
                  "fieldOfStudy": string or null,
                  "graduationDate": string or null,
                  "gpa": string or null,
                  "details": [string]
                }
              ],
              "certifications": [
                {
                  "name": string,
                  "issuer": string or null,
                  "issueDate": string or null,
                  "credentialId": string or null,
                  "details": [string]
                }
              ],
              "projects": [
                {
                  "name": string,
                  "role": string or null,
                  "technologies": [string],
                  "url": string or null,
                  "bullets": [string]
                }
              ],
              "skills": [string]
            }

            Raw resume text:
            \"\"\"
            $rawText
            \"\"\"
        """.trimIndent()
    }

    private fun parseJsonResponse(jsonStr: String): BaseResume {
        val cleanJson = jsonStr.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return json.decodeFromString<BaseResume>(cleanJson)
    }

    /**
     * Deterministic code check (NOT LLM):
     * Every employer, title, date string, school, and certification in the structured output
     * must appear, after whitespace and case normalization, in the raw text.
     * Anything that fails is dropped and flagged.
     */
    fun verifyExtraction(rawText: String, structured: BaseResume): StructuredResumeResult {
        val normRaw = normalize(rawText)
        val dropped = mutableListOf<String>()

        // 1. Verify Experience entries
        val verifiedExperience = structured.experience.mapNotNull { exp ->
            val normEmployer = normalize(exp.employer)
            val normTitle = normalize(exp.title)

            if (normEmployer.isNotBlank() && !normRaw.contains(normEmployer)) {
                dropped.add("Employer dropped (not in raw text): ${exp.employer}")
                return@mapNotNull null
            }
            if (normTitle.isNotBlank() && !normRaw.contains(normTitle)) {
                dropped.add("Title dropped (not in raw text): ${exp.title} at ${exp.employer}")
                return@mapNotNull null
            }

            val validStartDate = exp.startDate?.takeIf { normRaw.contains(normalize(it)) } ?: run {
                if (!exp.startDate.isNullOrBlank()) dropped.add("Start date dropped: ${exp.startDate}")
                null
            }
            val validEndDate = exp.endDate?.takeIf { normRaw.contains(normalize(it)) } ?: run {
                if (!exp.endDate.isNullOrBlank()) dropped.add("End date dropped: ${exp.endDate}")
                null
            }

            exp.copy(
                startDate = validStartDate,
                endDate = validEndDate,
            )
        }

        // 2. Verify Education entries
        val verifiedEducation = structured.education.mapNotNull { edu ->
            val normSchool = normalize(edu.school)
            val normDegree = normalize(edu.degree)

            if (normSchool.isNotBlank() && !normRaw.contains(normSchool)) {
                dropped.add("School dropped (not in raw text): ${edu.school}")
                return@mapNotNull null
            }
            if (normDegree.isNotBlank() && !normRaw.contains(normDegree)) {
                dropped.add("Degree dropped (not in raw text): ${edu.degree} at ${edu.school}")
                return@mapNotNull null
            }
            edu
        }

        // 3. Verify Certification entries
        val verifiedCertifications = structured.certifications.mapNotNull { cert ->
            val normName = normalize(cert.name)
            if (normName.isNotBlank() && !normRaw.contains(normName)) {
                dropped.add("Certification dropped (not in raw text): ${cert.name}")
                return@mapNotNull null
            }
            cert
        }

        val verifiedResume = structured.copy(
            experience = verifiedExperience,
            education = verifiedEducation,
            certifications = verifiedCertifications,
        )

        return StructuredResumeResult(
            resume = verifiedResume,
            droppedItems = dropped,
        )
    }

    private fun normalize(str: String): String {
        return str.lowercase().replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Deterministic offline section-based heuristic parser.
     * Splits raw resume text by standard uppercase headers and extracts:
     * - Contact header (excluding it from summary)
     * - Professional Summary
     * - Technical Skills (comma- and colon-separated lines)
     * - Work Experience (Role | Company | Date or Company + bullet blocks)
     * - Projects, Education, and Certifications
     */
    private fun fallbackDeterministicStructure(rawText: String): StructuredResumeResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val summaryHeaders = setOf(
            "PROFESSIONAL SUMMARY", "SUMMARY", "EXECUTIVE SUMMARY", "PROFILE",
            "CAREER SUMMARY", "OBJECTIVE", "ABOUT ME"
        )
        val skillsHeaders = setOf(
            "TECHNICAL SKILLS", "SKILLS", "CORE COMPETENCIES", "TECHNOLOGIES",
            "SKILLS & TECHNOLOGIES", "TECHNICAL PROFICIENCIES", "PROGRAMMING LANGUAGES"
        )
        val experienceHeaders = setOf(
            "WORK EXPERIENCE", "EXPERIENCE", "EMPLOYMENT HISTORY",
            "PROFESSIONAL EXPERIENCE", "RELEVANT EXPERIENCE", "CAREER HISTORY"
        )
        val projectHeaders = setOf(
            "PROJECTS", "TECHNICAL PROJECTS", "KEY PROJECTS", "ACADEMIC PROJECTS",
            "PERSONAL PROJECTS", "SELECTED PROJECTS", "SOFTWARE PROJECTS"
        )
        val educationHeaders = setOf(
            "EDUCATION", "ACADEMIC BACKGROUND", "ACADEMICS"
        )
        val certHeaders = setOf(
            "CERTIFICATIONS", "LICENSES & CERTIFICATIONS", "CERTIFICATES",
            "AWARDS & CERTIFICATIONS", "HONORS & AWARDS"
        )

        fun detectSection(line: String): String? {
            val cleaned = line.trim().trimStart('•', '-', '*', '#').trim().trimEnd(':').uppercase()
            return when {
                cleaned in summaryHeaders -> "SUMMARY"
                cleaned in skillsHeaders -> "SKILLS"
                cleaned in experienceHeaders -> "EXPERIENCE"
                cleaned in projectHeaders -> "PROJECTS"
                cleaned in educationHeaders -> "EDUCATION"
                cleaned in certHeaders -> "CERTIFICATIONS"
                else -> null
            }
        }

        val sections = linkedMapOf<String, MutableList<String>>()
        var currentSection = "HEADER"
        sections[currentSection] = mutableListOf()

        for (line in lines) {
            val detected = detectSection(line)
            if (detected != null) {
                currentSection = detected
                sections.getOrPut(currentSection) { mutableListOf() }
            } else {
                sections.getOrPut(currentSection) { mutableListOf() }.add(line)
            }
        }

        val emailRegex = Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")
        val phoneRegex = Regex("(?:\\+?1[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}")
        val linkedinRegex = Regex("(?i)(?:https?://)?(?:www\\.)?linkedin\\.com/in/[^\\s|•,]+")
        val githubRegex = Regex("(?i)(?:https?://)?(?:www\\.)?github\\.com/[^\\s|•,]+")

        fun isContactLine(line: String): Boolean {
            val lower = line.lowercase()
            return emailRegex.containsMatchIn(line) ||
                phoneRegex.containsMatchIn(line) ||
                lower.contains("linkedin.com") ||
                lower.contains("github.com")
        }

        // 1. Parse Contact Info from HEADER
        val headerLines = sections["HEADER"].orEmpty()
        val fullHeaderText = headerLines.joinToString(" ")
        val contactName = headerLines.firstOrNull { !isContactLine(it) && it.split(Regex("\\s+")).size in 1..5 }
        val contactEmail = emailRegex.find(fullHeaderText)?.value
        val contactPhone = phoneRegex.find(fullHeaderText)?.value
        val contactLinkedin = linkedinRegex.find(fullHeaderText)?.value
        val contactGithub = githubRegex.find(fullHeaderText)?.value
        val contact = ResumeContact(
            name = contactName,
            email = contactEmail,
            phone = contactPhone,
            linkedin = contactLinkedin,
            github = contactGithub,
        )

        // 2. Parse Professional Summary (strictly ignoring contact lines with @ or linkedin.com)
        val summaryLines = sections["SUMMARY"].orEmpty().filter { !isContactLine(it) }
        val parsedSummary = if (summaryLines.isNotEmpty()) {
            summaryLines.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        } else {
            headerLines
                .drop(1)
                .filter { !isContactLine(it) && it.length > 50 }
                .joinToString(" ")
                .trim()
                .takeIf { it.isNotBlank() }
        }

        // 3. Parse Technical Skills (supports colon-prefixed categories and comma/pipe/bullet lists)
        val parsedSkills = mutableListOf<String>()
        for (rawLine in sections["SKILLS"].orEmpty()) {
            val strippedBullet = rawLine.trimStart('•', '-', '*', '▪', '◦', '·').trim()
            if (strippedBullet.isBlank()) continue
            val contentAfterColon = if (strippedBullet.contains(":")) {
                strippedBullet.substringAfter(":").trim()
            } else {
                strippedBullet
            }
            contentAfterColon
                .split(Regex("[,;|•]"))
                .map { it.trim().trimEnd('.') }
                .filter { it.isNotBlank() && it.length <= 45 }
                .forEach { skill ->
                    if (parsedSkills.none { it.equals(skill, ignoreCase = true) }) {
                        parsedSkills.add(skill)
                    }
                }
        }

        // 4. Parse Work Experience
        val gluedMonthRegex = Regex(
            """(?<=[a-z.])(?=(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?|Spring|Summer|Fall|Autumn|Winter)\s+\d{4}\b)"""
        )
        val trailingMonthRegex = Regex(
            """^(.*?)(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)$"""
        )
        val dateRangeRegex = Regex(
            """(?i)\b((?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?|Spring|Summer|Fall|Autumn|Winter)?\s*(?:\d{1,2}/)?\d{4})\s*(?:[-–—~]+|to)\s*((?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?|Spring|Summer|Fall|Autumn|Winter)?\s*(?:\d{1,2}/)?\d{4}|Present|Current|Now)\b"""
        )
        val roleKeywords = listOf(
            "engineer", "developer", "intern", "analyst", "specialist", "architect",
            "lead", "manager", "assistant", "researcher", "consultant", "technician",
            "administrator", "coordinator", "director", "scientist", "designer",
            "instructor", "tutor", "fellow", "associate", "programmer", "member", "officer"
        )
        fun looksLikeRoleTitle(text: String): Boolean {
            val l = text.lowercase()
            return roleKeywords.any { l.contains(it) }
        }
        fun sanitizeExpEntry(exp: ResumeExperience): ResumeExperience {
            var cleanEmployer = exp.employer.trim()
            var cleanTitle = exp.title.trim()
            var cleanStart = exp.startDate?.trim()
            trailingMonthRegex.find(cleanEmployer)?.let { m ->
                val prefix = m.groupValues[1].trim()
                val month = m.groupValues[2].trim()
                if (prefix.isNotEmpty()) {
                    cleanEmployer = prefix
                    if (!cleanStart.isNullOrBlank() && !cleanStart!!.contains(month, ignoreCase = true)) {
                        cleanStart = "$month $cleanStart"
                    }
                }
            }
            trailingMonthRegex.find(cleanTitle)?.let { m ->
                val prefix = m.groupValues[1].trim()
                val month = m.groupValues[2].trim()
                if (prefix.isNotEmpty()) {
                    cleanTitle = prefix
                    if (!cleanStart.isNullOrBlank() && !cleanStart!!.contains(month, ignoreCase = true)) {
                        cleanStart = "$month $cleanStart"
                    }
                }
            }
            return exp.copy(
                employer = cleanEmployer,
                title = cleanTitle,
                startDate = cleanStart,
            )
        }

        val parsedExperiences = mutableListOf<ResumeExperience>()
        var currentExp: ResumeExperience? = null

        for (rawLine in sections["EXPERIENCE"].orEmpty()) {
            val trimmed = rawLine.trim().replace(gluedMonthRegex, " ")
            if (trimmed.isEmpty()) continue

            val startsWithBullet = trimmed.startsWith("•") ||
                trimmed.startsWith("-") ||
                trimmed.startsWith("*") ||
                trimmed.startsWith("▪") ||
                trimmed.startsWith("◦") ||
                trimmed.startsWith("·")

            val dateMatch = dateRangeRegex.find(trimmed)
            val hasPipe = trimmed.contains("|")

            val isBulletLine = when {
                startsWithBullet -> true
                currentExp != null && dateMatch == null && !hasPipe && (trimmed.length > 65 || trimmed.endsWith(".")) -> true
                else -> false
            }

            if (isBulletLine) {
                val cleanBullet = trimmed.trimStart('•', '-', '*', '▪', '◦', '·').trim()
                if (cleanBullet.isNotEmpty()) {
                    val active = currentExp ?: ResumeExperience(employer = "Experience", title = "Role")
                    currentExp = active.copy(bullets = active.bullets + cleanBullet)
                }
            } else {
                // Header line (single-line or two-line header before bullets)
                val startStr = dateMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                val endStr = dateMatch?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
                val isCurrentRole = endStr?.let {
                    it.equals("Present", ignoreCase = true) ||
                        it.equals("Current", ignoreCase = true) ||
                        it.equals("Now", ignoreCase = true)
                } ?: false

                val withoutDate = if (dateMatch != null) {
                    trimmed.removeRange(dateMatch.range).trim().trimEnd('|', '-', '–', '—', ',', '·', '•').trim()
                } else {
                    trimmed
                }

                val parts = withoutDate
                    .split(Regex("\\s*\\|\\s*|\\s+[—–]\\s+|\\s+-\\s+"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                val existing = currentExp
                if (existing != null && existing.bullets.isEmpty() && existing.startDate == null && (dateMatch != null || existing.title.isBlank())) {
                    // Merge 2nd header line into the existing un-bulleted role entry
                    val mergedTitle = if (existing.title.isBlank()) {
                        parts.firstOrNull().orEmpty()
                    } else if (looksLikeRoleTitle(parts.firstOrNull().orEmpty()) && !looksLikeRoleTitle(existing.title)) {
                        parts.firstOrNull().orEmpty()
                    } else {
                        existing.title
                    }
                    val mergedEmployer = if (mergedTitle != existing.title && existing.employer.isNotBlank()) {
                        existing.employer
                    } else if (parts.size >= 2) {
                        parts[1]
                    } else {
                        existing.employer
                    }
                    val mergedLocation = existing.location ?: parts.getOrNull(2)
                    currentExp = existing.copy(
                        employer = mergedEmployer.ifBlank { existing.employer },
                        title = mergedTitle.ifBlank { existing.title },
                        location = mergedLocation,
                        startDate = startStr ?: existing.startDate,
                        endDate = endStr ?: existing.endDate,
                        isCurrent = isCurrentRole || existing.isCurrent,
                    )
                } else {
                    if (existing != null && (existing.employer.isNotBlank() || existing.title.isNotBlank() || existing.bullets.isNotEmpty())) {
                        parsedExperiences.add(existing)
                    }
                    var employer = ""
                    var title = ""
                    var location: String? = null

                    when {
                        parts.size >= 2 -> {
                            val p0 = parts[0]
                            val p1 = parts[1]
                            if (looksLikeRoleTitle(p0) && !looksLikeRoleTitle(p1)) {
                                title = p0
                                employer = p1
                            } else {
                                employer = p0
                                title = p1
                            }
                            if (parts.size >= 3) {
                                location = parts.drop(2).joinToString(", ")
                            }
                        }
                        parts.size == 1 -> {
                            val single = parts[0]
                            if ( single.contains(" at ", ignoreCase = true)) {
                                title = single.substringBefore(" at ").trim()
                                employer = single.substringAfter(" at ").trim()
                            } else if (single.contains(",")) {
                                val commaParts = single.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                if (commaParts.size >= 2 && looksLikeRoleTitle(commaParts[0])) {
                                    title = commaParts[0]
                                    employer = commaParts[1]
                                    location = commaParts.drop(2).joinToString(", ").takeIf { it.isNotBlank() }
                                } else {
                                    employer = single
                                }
                            } else {
                                employer = single
                            }
                        }
                    }

                    currentExp = ResumeExperience(
                        employer = employer.ifBlank { title },
                        title = title.ifBlank { employer },
                        location = location,
                        startDate = startStr,
                        endDate = endStr,
                        isCurrent = isCurrentRole,
                        bullets = emptyList(),
                    )
                }
            }
        }
        currentExp?.let { last ->
            if (last.employer.isNotBlank() || last.title.isNotBlank() || last.bullets.isNotEmpty()) {
                parsedExperiences.add(last)
            }
        }

        // 5. Parse Projects
        val parsedProjects = mutableListOf<ResumeProject>()
        var currentProj: ResumeProject? = null
        for (rawLine in sections["PROJECTS"].orEmpty()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            val isBullet = trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*") ||
                (currentProj != null && !trimmed.contains("|") && (trimmed.length > 65 || trimmed.endsWith(".")))
            if (isBullet) {
                val b = trimmed.trimStart('•', '-', '*', '▪', '◦', '·').trim()
                if (b.isNotEmpty()) {
                    val active = currentProj ?: ResumeProject(name = "Project")
                    currentProj = active.copy(bullets = active.bullets + b)
                }
            } else {
                currentProj?.let { parsedProjects.add(it) }
                val withoutDate = dateRangeRegex.find(trimmed)?.let { trimmed.removeRange(it.range).trim() } ?: trimmed
                val parts = withoutDate.split("|").map { it.trim().trimEnd('-', '–', '—') }.filter { it.isNotBlank() }
                val projName = parts.firstOrNull() ?: trimmed
                val techs = parts.getOrNull(1)?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
                currentProj = ResumeProject(
                    name = projName,
                    technologies = techs,
                    bullets = emptyList(),
                )
            }
        }
        currentProj?.let { parsedProjects.add(it) }

        // 6. Parse Education
        val parsedEducation = mutableListOf<ResumeEducation>()
        val eduLines = sections["EDUCATION"].orEmpty()
        var currentEdu: ResumeEducation? = null
        for (rawLine in eduLines) {
            val trimmed = rawLine.trim().trimStart('•', '-', '*').trim()
            if (trimmed.isEmpty()) continue
            val isDegreeLine = trimmed.contains("Bachelor", ignoreCase = true) ||
                trimmed.contains("Master", ignoreCase = true) ||
                trimmed.contains("B.S", ignoreCase = true) ||
                trimmed.contains("M.S", ignoreCase = true) ||
                trimmed.contains("B.A", ignoreCase = true) ||
                trimmed.contains("Ph.D", ignoreCase = true) ||
                trimmed.contains("Associate", ignoreCase = true) ||
                trimmed.contains("Degree", ignoreCase = true) ||
                trimmed.contains("Computer Science", ignoreCase = true) ||
                trimmed.contains("Engineering", ignoreCase = true)
            val isSchoolLine = trimmed.contains("University", ignoreCase = true) ||
                trimmed.contains("College", ignoreCase = true) ||
                trimmed.contains("Institute", ignoreCase = true) ||
                trimmed.contains("School", ignoreCase = true) ||
                trimmed.contains("Academy", ignoreCase = true)

            if (isSchoolLine && (currentEdu == null || currentEdu.school.isNotBlank())) {
                currentEdu?.let { parsedEducation.add(it) }
                val parts = trimmed.split("|").map { it.trim() }.filter { it.isNotBlank() }
                currentEdu = ResumeEducation(
                    school = parts.firstOrNull() ?: trimmed,
                    degree = parts.getOrNull(1) ?: "",
                    graduationDate = parts.getOrNull(2),
                )
            } else if (isDegreeLine && currentEdu != null && currentEdu.degree.isBlank()) {
                val parts = trimmed.split("|").map { it.trim() }.filter { it.isNotBlank() }
                currentEdu = currentEdu.copy(
                    degree = parts.firstOrNull() ?: trimmed,
                    graduationDate = currentEdu.graduationDate ?: parts.getOrNull(1),
                )
            } else if (currentEdu != null) {
                currentEdu = currentEdu.copy(details = currentEdu.details + trimmed)
            } else {
                currentEdu = ResumeEducation(school = trimmed, degree = "")
            }
        }
        currentEdu?.let { parsedEducation.add(it) }

        // 7. Parse Certifications
        val parsedCerts = sections["CERTIFICATIONS"].orEmpty()
            .map { it.trim().trimStart('•', '-', '*').trim() }
            .filter { it.isNotBlank() }
            .map { ResumeCertification(name = it) }

        val resume = BaseResume(
            contact = contact,
            summary = parsedSummary,
            skills = parsedSkills,
            experience = parsedExperiences.map { sanitizeExpEntry(it).copy(bullets = ResumeBulletUtils.mergeFragments(it.bullets)) },
            projects = parsedProjects.map { it.copy(bullets = ResumeBulletUtils.mergeFragments(it.bullets)) },
            education = parsedEducation,
            certifications = parsedCerts,
        )
        return StructuredResumeResult(resume = resume, droppedItems = emptyList())
    }
}
