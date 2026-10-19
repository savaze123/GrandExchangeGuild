package com.osrs.careerguild.domain.scorer

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.osrs.careerguild.domain.model.JobListing
import com.osrs.careerguild.domain.model.Skill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * TRAJECTORY_SCORER — Gemini 1.5 Flash implementation.
 *
 * Scores a job listing 1–100 based on:
 *  - 5-year salary & career trajectory (40 pts)
 *  - Skill match against DYNAMIC_SKILL_MATRIX (40 pts)
 *  - Staffing agency / body-shop detection penalty (−30 pts or flagged)
 *
 * Primary model: `gemini-1.5-flash` (fast, cost-efficient, sufficient for JSON scoring).
 * Planned fallback: Gemini Nano via Android AICore (Phase 3, on-device offline).
 *
 * Security: the API key is read from BuildConfig, injected at build time from
 * `local.properties` — never hard-coded or logged.
 *
 * Node: TRAJECTORY_SCORER
 */
class GeminiTrajectoryScorer(
    private val apiKey: String,
    private val json  : Json = Json { ignoreUnknownKeys = true },
) : TrajectoryScorer {

    private val model: GenerativeModel by lazy {
        GenerativeModel(
            modelName       = MODEL_NAME,
            apiKey          = apiKey,
            generationConfig = generationConfig {
                temperature     = 0.1f   // low temp for deterministic scoring
                maxOutputTokens = 256
            },
        )
    }

    override suspend fun score(
        job          : JobListing,
        activeSkills : List<Skill>,
    ): Result<ScoredJob> = withContext(Dispatchers.IO) {
        runCatching {
            val skillList = activeSkills.joinToString(", ") { it.name }

            val prompt = buildScoringPrompt(job, skillList)
            val responseText = model.generateContent(prompt).text
                ?: return@runCatching fallbackScore(job)

            val parsed = parseResponse(responseText)

            val matchedSkills = activeSkills
                .filter { skill ->
                    val jdText = "${job.title} ${job.company}".lowercase()
                    jdText.contains(skill.name.lowercase())
                }
                .map { it.name }

            val enrichedListing = job.copy(
                score         = parsed.score.coerceIn(0, 100),
                matchedSkills = matchedSkills,
            )

            ScoredJob(
                listing    = enrichedListing,
                isStaffing = parsed.isStaffing,
                reasoning  = parsed.reasoning,
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Prompt builder
    // ---------------------------------------------------------------------------

    private fun buildScoringPrompt(job: JobListing, skillList: String): String = """
        You are a career advisor for a CS graduate with these active skills: $skillList.
        
        Evaluate this job listing and respond with ONLY a valid JSON object — no markdown, no explanation outside the JSON.
        
        Job Title: ${job.title}
        Company: ${job.company}
        Source: ${job.source}
        
        Scoring criteria (total 100 pts):
        • 5-year salary trajectory (0-40 pts): Big Tech / well-funded startup / principal-track roles score higher. 
          Staff augmentation, outsourcing, or "consultant" roles score lower.
        • Skill match (0-40 pts): How well the role's likely tech stack overlaps with the user's active skills.
        • Role legitimacy (0-20 pts): Deduct if the company is a staffing agency, body shop, or the role is 
          "help desk", "IT support", or non-coding.
        
        Also detect if this looks like a predatory staffing agency (Cognizant, Infosys, Wipro, HCL, TCS,
        Capgemini, iGate, Mastech, Insight Global, Staffmark, or similar body shops).
        
        Respond ONLY with this JSON schema:
        {"score": <int 0-100>, "is_staffing": <bool>, "reasoning": "<one sentence>"}
    """.trimIndent()

    // ---------------------------------------------------------------------------
    // JSON parsing — extracts first JSON object from the model response
    // ---------------------------------------------------------------------------

    private fun parseResponse(text: String): ScoreResponse {
        val jsonStart = text.indexOf('{')
        val jsonEnd   = text.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd == -1 || jsonStart >= jsonEnd) {
            return ScoreResponse(score = 50, isStaffing = false, reasoning = "Parse error — default score.")
        }
        return runCatching {
            json.decodeFromString<ScoreResponse>(text.substring(jsonStart, jsonEnd + 1))
        }.getOrElse {
            ScoreResponse(score = 50, isStaffing = false, reasoning = "JSON decode error — default score.")
        }
    }

    private fun fallbackScore(job: JobListing) = ScoredJob(
        listing    = job.copy(score = 50),
        isStaffing = false,
        reasoning  = "Gemini returned no text — assigned default score 50.",
    )

    // ---------------------------------------------------------------------------
    // Internal DTO for model response
    // ---------------------------------------------------------------------------

    @Serializable
    private data class ScoreResponse(
        @SerialName("score")       val score      : Int,
        @SerialName("is_staffing") val isStaffing : Boolean,
        @SerialName("reasoning")   val reasoning  : String,
    )

    companion object {
        private const val MODEL_NAME = "gemini-1.5-flash"
        const val MAX_JOBS_PER_SESSION = 25  // cap Gemini API calls per scrape run
    }
}
