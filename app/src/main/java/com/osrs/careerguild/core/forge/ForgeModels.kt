package com.osrs.careerguild.core.forge

import com.osrs.careerguild.domain.resume.TailoredResume
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForgeTaskFields(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val location: String? = null,
    @SerialName("linkedin_url") val linkedinUrl: String? = null,
    @SerialName("github_url") val githubUrl: String? = null,
    val website: String? = null,
)

@Serializable
data class ForgeAnswer(
    @SerialName("question_pattern") val questionPattern: String,
    val answer: String,
)

@Serializable
data class ForgeTask(
    val schema: String = SCHEMA_V1,
    @SerialName("task_id") val taskId: String,
    @SerialName("job_id") val jobId: String,
    @SerialName("job_url") val jobUrl: String,
    val ats: String, // greenhouse | lever | ashby | other
    val title: String,
    val company: String,
    @SerialName("auto_submit_approved") val autoSubmitApproved: Boolean = false,
    @SerialName("resume_pdf") val resumePdf: String, // relative path e.g. "resume.pdf"
    val fields: ForgeTaskFields,
    val answers: List<ForgeAnswer> = emptyList(),
    @SerialName("tailored_resume") val tailoredResume: TailoredResume,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SCHEMA_V1 = "forge_task.v1"
    }
}

@Serializable
enum class ForgeResultStatus {
    @SerialName("filled_awaiting_review") FILLED_AWAITING_REVIEW,
    @SerialName("submitted") SUBMITTED,
    @SerialName("needs_input") NEEDS_INPUT,
    @SerialName("failed") FAILED,
    @SerialName("submit_unconfirmed") SUBMIT_UNCONFIRMED;

    companion object {
        fun fromSerializedName(name: String): ForgeResultStatus? {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: when (name.lowercase().trim()) {
                    "filled_awaiting_review" -> FILLED_AWAITING_REVIEW
                    "submitted" -> SUBMITTED
                    "needs_input" -> NEEDS_INPUT
                    "failed" -> FAILED
                    "submit_unconfirmed" -> SUBMIT_UNCONFIRMED
                    else -> null
                }
        }
    }
}

@Serializable
data class ForgeResult(
    val schema: String = SCHEMA_V1,
    @SerialName("task_id") val taskId: String,
    val status: ForgeResultStatus,
    @SerialName("missing_fields") val missingFields: List<String> = emptyList(),
    @SerialName("filled_field_names") val filledFieldNames: List<String> = emptyList(),
    @SerialName("confirmation_text") val confirmationText: String? = null,
    val screenshot: String? = null,
    @SerialName("finished_at") val finishedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SCHEMA_V1 = "forge_result.v1"
    }
}
