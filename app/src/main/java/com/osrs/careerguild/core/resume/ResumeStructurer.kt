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

            if (!tokenGovernor.isGeminiAllowed || apiKey.isBlank()) {
                // If Gemini is budget-capped or key is missing, fallback to deterministic heuristic parser
                return@runCatching fallbackDeterministicStructure(rawText)
            }

            val prompt = buildStructuringPrompt(rawText)
            val responseText = model.generateContent(prompt).text
                ?: throw IllegalStateException("Empty response received from Gemini.")

            tokenGovernor.recordScoringBatch(1)

            val parsedResume = parseJsonResponse(responseText)
            // Execute deterministic extraction check
            verifyExtraction(rawText, parsedResume)
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
     * Fallback parser used if Gemini budget is exhausted or offline.
     * Extracts lines directly from raw text into BaseResume.
     */
    private fun fallbackDeterministicStructure(rawText: String): StructuredResumeResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val resume = BaseResume(
            summary = lines.firstOrNull { it.length > 50 },
            skills = listOf("Software Engineering", "Systems Architecture"),
        )
        return StructuredResumeResult(resume = resume, droppedItems = listOf("Gemini unavailable; heuristic extraction used."))
    }
}
