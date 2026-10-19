package com.osrs.careerguild.domain.usecase.bountyboard

import com.osrs.careerguild.domain.repository.JobRepository
import java.time.Instant

/**
 * Marks a job as applied with the current timestamp.
 *
 * Edge: AUTO_APPLY_AGENT → LOCAL_VAULT (injects_tailored_pdf_into_ats_form)
 * Also callable manually when the user applies outside the app.
 */
class MarkAppliedUseCase(
    private val jobRepository: JobRepository,
) {
    suspend operator fun invoke(jobId: String): Result<Unit> =
        jobRepository.markApplied(jobId, appliedAt = Instant.now().toString())
}
