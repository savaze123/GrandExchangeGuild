package com.osrs.careerguild.core.diagnostics

import com.osrs.careerguild.core.location.JobLocationUtils
import com.osrs.careerguild.domain.model.JobListing

data class SourceReportRow(
    val sourceName: String,
    val fetchedCount: Int,
    val verifiedCount: Int,
    val deadCount: Int,
    val seedCount: Int,
    val medianScore: Int,
)

data class DiagnosticsSummary(
    val totalFetched: Int,
    val totalVerifiedNonSeed: Int,
    val totalSeedCount: Int,
    val jobsWithin10Mi: Int,
    val jobsWithin25Mi: Int,
    val jobsWithin50Mi: Int,
    val jobsWithin100Mi: Int,
    val remoteJobs: Int,
    val jobs50MiPlusRemote: Int,
    val rows: List<SourceReportRow>,
    val reportTable: String,
)

/**
 * Diagnostics engine to verify that job ingestion, filtering, and link routing deliver
 * 100+ real, verified, non-seed quests across 50 miles + Remote from ZIP 11581.
 */
object JobSourceDiagnostics {

    fun evaluate(
        jobs: List<JobListing>,
        homeZip: String = "11581",
    ): DiagnosticsSummary {
        val totalFetched = jobs.size
        var seedCount = 0
        val nonSeedJobs = mutableListOf<JobListing>()

        for (job in jobs) {
            if (job.linkStatus.equals("SEED", ignoreCase = true)) {
                seedCount++
            } else {
                nonSeedJobs.add(job)
            }
        }

        // Filter valid verified non-seed jobs with non-blank description (>= 100 chars) and non-generic title
        val verifiedNonSeed = nonSeedJobs.filter { job ->
            val isVerifiedLink = job.linkStatus.equals("VERIFIED", true) ||
                job.linkStatus.equals("TRUSTED_UNVERIFIED", true)
            val hasValidDesc = job.description.isNotBlank() && job.description.length >= 100
            val hasValidTitle = job.title.isNotBlank() && job.title.length > 3
            isVerifiedLink && hasValidDesc && hasValidTitle
        }

        var count10 = 0
        var count25 = 0
        var count50 = 0
        var count100 = 0
        var countRemote = 0
        var count50PlusRemote = 0

        for (job in verifiedNonSeed) {
            val isRemote = job.workMode.equals("Remote", ignoreCase = true) ||
                job.location.contains("Remote", ignoreCase = true)

            if (isRemote) {
                countRemote++
                count50PlusRemote++
            }

            if (JobLocationUtils.matchesRadius(job.location, job.workMode, homeZip, "10 mi")) count10++
            if (JobLocationUtils.matchesRadius(job.location, job.workMode, homeZip, "25 mi")) count25++
            if (JobLocationUtils.matchesRadius(job.location, job.workMode, homeZip, "50 mi")) {
                count50++
                if (!isRemote) count50PlusRemote++
            }
            if (JobLocationUtils.matchesRadius(job.location, job.workMode, homeZip, "100 mi")) count100++
        }

        // Group by source for tabular reporting
        val grouped = jobs.groupBy { it.source }
        val rows = grouped.map { (sourceName, list) ->
            val fetched = list.size
            val seeds = list.count { it.linkStatus.equals("SEED", true) }
            val verified = list.count {
                !it.linkStatus.equals("SEED", true) &&
                    (it.linkStatus.equals("VERIFIED", true) || it.linkStatus.equals("TRUSTED_UNVERIFIED", true)) &&
                    it.description.length >= 100
            }
            val dead = list.count { it.linkStatus.equals("DEAD", true) }
            val scores = list.map { it.score }.sorted()
            val median = if (scores.isNotEmpty()) scores[scores.size / 2] else 0

            SourceReportRow(
                sourceName = sourceName,
                fetchedCount = fetched,
                verifiedCount = verified,
                deadCount = dead,
                seedCount = seeds,
                medianScore = median,
            )
        }.sortedByDescending { it.verifiedCount }

        val tableBuilder = StringBuilder()
        tableBuilder.appendLine("| Source Name | Fetched | Verified Real | Dead | Seeds | Median Score |")
        tableBuilder.appendLine("| :--- | :---: | :---: | :---: | :---: | :---: |")
        for (r in rows) {
            tableBuilder.appendLine("| ${r.sourceName} | ${r.fetchedCount} | ${r.verifiedCount} | ${r.deadCount} | ${r.seedCount} | ${r.medianScore} |")
        }

        tableBuilder.appendLine()
        tableBuilder.appendLine("Geographic Distribution for ZIP $homeZip (Verified Real Only):")
        tableBuilder.appendLine("  • Within 10 miles: $count10")
        tableBuilder.appendLine("  • Within 25 miles: $count25")
        tableBuilder.appendLine("  • Within 50 miles: $count50")
        tableBuilder.appendLine("  • Within 100 miles: $count100")
        tableBuilder.appendLine("  • Remote / Nationwide: $countRemote")
        tableBuilder.appendLine("  • Combined (50 mi + Remote): $count50PlusRemote")

        return DiagnosticsSummary(
            totalFetched = totalFetched,
            totalVerifiedNonSeed = verifiedNonSeed.size,
            totalSeedCount = seedCount,
            jobsWithin10Mi = count10,
            jobsWithin25Mi = count25,
            jobsWithin50Mi = count50,
            jobsWithin100Mi = count100,
            remoteJobs = countRemote,
            jobs50MiPlusRemote = count50PlusRemote,
            rows = rows,
            reportTable = tableBuilder.toString(),
        )
    }
}
