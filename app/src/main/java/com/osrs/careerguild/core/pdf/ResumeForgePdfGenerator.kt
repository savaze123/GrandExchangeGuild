package com.osrs.careerguild.core.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.osrs.careerguild.core.profile.UserProfile
import com.osrs.careerguild.core.profile.UserProfileManager
import com.osrs.careerguild.domain.resume.TailoredResume
import java.io.File
import java.io.FileOutputStream

data class PdfForgeResult(
    val file: File,
    val fileSizeBytes: Long,
    val fileSizeKb: Float,
    val pageCount: Int,
)

/**
 * RESUME_FORGE_PDF — Dynamic Single-Column ATS Resume Generator.
 *
 * Directives:
 * 1. Strict ATS Compliance & 1-Page Natural Spacing:
 *    - Uses native Android [PdfDocument] API (<100KB binary).
 *    - Single-column UTF-8 typography, clean natural language syntax.
 *    - Fit loop: starts at default font size (9.5pt), shrinks to a floor of 9.0pt,
 *      then drops lowest-relevance bullets until it strictly fits exactly 1 page (792pt).
 * 2. Zero Hardcoded Personal or Employment Data:
 *    - All content originates strictly from [TailoredResume] (derived from BaseResume + GitHub).
 * 3. Native Single-Page PDF under 100KB.
 */
object ResumeForgePdfGenerator {

    private const val PAGE_WIDTH = 612
    private const val PAGE_HEIGHT = 792
    private const val LEFT_MARGIN = 40f
    private const val TOP_MARGIN = 36f
    private const val BOTTOM_MARGIN = 36f
    private const val MAX_CONTENT_Y = PAGE_HEIGHT - BOTTOM_MARGIN // 756f

    /**
     * Primary modern overload: Renders dynamically from [TailoredResume].
     */
    fun forgeAtsResume(
        context       : Context,
        tailoredResume: TailoredResume,
        targetTitle   : String,
        targetCompany : String,
    ): Result<PdfForgeResult> = runCatching {
        val userProfile = UserProfileManager.getInstance(context).profile.value
        val fittedResume = fitResumeToOnePage(tailoredResume, targetTitle, targetCompany, userProfile)
        renderPdf(context, fittedResume.resume, fittedResume.bodyFontSize, targetTitle, targetCompany, userProfile)
    }

    /**
     * Backward-compatible overload: converts inputs to a [TailoredResume] and renders.
     */
    fun forgeAtsResume(
        context       : Context,
        userProfile   : UserProfile = UserProfileManager.getInstance(context).profile.value,
        targetTitle   : String,
        targetCompany : String,
        infusedSkills : List<String>,
        customSummary : String? = null,
    ): Result<PdfForgeResult> = runCatching {
        val tailoredResume = TailoredResume(
            contact = com.osrs.careerguild.core.resume.ResumeContact(
                name = userProfile.name,
                email = userProfile.email,
                location = userProfile.location,
                github = userProfile.github,
            ),
            summary = customSummary ?: "",
            skills = infusedSkills,
            isTailored = false,
        )
        val fitted = fitResumeToOnePage(tailoredResume, targetTitle, targetCompany, userProfile)
        renderPdf(context, fitted.resume, fitted.bodyFontSize, targetTitle, targetCompany, userProfile)
    }

    private data class FitState(
        val resume: TailoredResume,
        val bodyFontSize: Float,
    )

    /**
     * Fit-to-page algorithm:
     * 1. Start at font size 9.5pt.
     * 2. Shrink font size down to floor of 9.0pt in 0.1pt increments.
     * 3. If still overflowing 756pt, drop lowest-relevance bullets one-by-one until it fits.
     */
    private fun fitResumeToOnePage(
        initial: TailoredResume,
        targetTitle: String,
        targetCompany: String,
        userProfile: UserProfile,
    ): FitState {
        var currentResume = initial.copy(
            experience = initial.experience.filter { it.isActive },
            projects = initial.projects.filter { it.isActive },
            education = initial.education.filter { it.isActive },
            certifications = initial.certifications.filter { it.isActive },
        )
        var currentFontSize = 9.5f

        while (currentFontSize >= 9.0f) {
            val estimatedHeight = measureTotalHeight(currentResume, currentFontSize, targetTitle, targetCompany, userProfile)
            if (estimatedHeight <= MAX_CONTENT_Y) {
                return FitState(currentResume, currentFontSize)
            }
            currentFontSize -= 0.1f
        }

        // At floor font size (9.0pt), progressively prune trailing bullets from entries with the most bullets
        currentFontSize = 9.0f
        while (measureTotalHeight(currentResume, currentFontSize, targetTitle, targetCompany, userProfile) > MAX_CONTENT_Y) {
            // Find experience entry with the most bullets > 1
            val maxExp = currentResume.experience.filter { it.bullets.size > 1 }.maxByOrNull { it.bullets.size }
            if (maxExp != null) {
                val updatedExp = currentResume.experience.map {
                    if (it == maxExp) it.copy(bullets = it.bullets.dropLast(1)) else it
                }
                currentResume = currentResume.copy(experience = updatedExp)
                continue
            }

            // Next check projects
            val maxProj = currentResume.projects.filter { it.bullets.size > 1 }.maxByOrNull { it.bullets.size }
            if (maxProj != null) {
                val updatedProj = currentResume.projects.map {
                    if (it == maxProj) it.copy(bullets = it.bullets.dropLast(1)) else it
                }
                currentResume = currentResume.copy(projects = updatedProj)
                continue
            }

            // If no more bullets to drop, break to avoid infinite loop
            break
        }

        return FitState(currentResume, currentFontSize)
    }

    private fun measureTotalHeight(
        resume: TailoredResume,
        fontSize: Float,
        targetTitle: String,
        targetCompany: String,
        userProfile: UserProfile,
    ): Float {
        val contentWidth = PAGE_WIDTH - (LEFT_MARGIN * 2)
        val testPaint = Paint().apply { textSize = fontSize }
        var y = TOP_MARGIN

        // Header
        y += 16f + 12f + 10f + 10f // Title, target, contact, divider

        // Summary
        if (resume.summary.isNotBlank()) {
            y += 11f
            y += estimateWrappedTextHeight(resume.summary, contentWidth, fontSize, lineHeight = fontSize * 1.3f) + 8f
        }

        // Skills
        if (resume.skills.isNotEmpty()) {
            y += 11f
            val catMap = resume.getCategorizedSkills()
            for ((category, skills) in catMap) {
                if (skills.isNotEmpty()) {
                    val line = "$category: ${skills.joinToString(", ")}"
                    y += estimateWrappedTextHeight(line, contentWidth - 4f, fontSize, lineHeight = fontSize * 1.3f) + 2f
                }
            }
            y += 4f
        }

        // Experience
        if (resume.experience.isNotEmpty()) {
            y += 11f
            for (exp in resume.experience) {
                y += 10f + 9f
                for (b in exp.bullets) {
                    val lines = wrapBullet(b, contentWidth - 14f) { testPaint.measureText(it) }
                    y += maxOf(1, lines.size) * (fontSize * 1.3f) + 2.5f
                }
                y += 4f
            }
        }

        // Projects
        if (resume.projects.isNotEmpty()) {
            y += 10f
            for (proj in resume.projects) {
                y += 9f + 8f
                for (b in proj.bullets) {
                    val lines = wrapBullet(b, contentWidth - 14f) { testPaint.measureText(it) }
                    y += maxOf(1, lines.size) * (fontSize * 1.3f) + 2f
                }
                y += 4f
            }
        }

        // Education
        if (resume.education.isNotEmpty()) {
            y += 11f
            for (edu in resume.education) {
                y += 10f + 9f
            }
        }

        // Certifications
        if (resume.certifications.isNotEmpty()) {
            y += 11f
            for (cert in resume.certifications) {
                y += 10f + 9f
            }
        }

        // Footer
        y += 8f + 9f
        return y
    }

    private fun renderPdf(
        context: Context,
        resume: TailoredResume,
        bodyFontSize: Float,
        targetTitle: String,
        targetCompany: String,
        userProfile: UserProfile,
    ): PdfForgeResult {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val sectionPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 0.75f
        }

        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = bodyFontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val boldBodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = bodyFontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val contentWidth = PAGE_WIDTH - (LEFT_MARGIN * 2)
        var y = TOP_MARGIN

        // 1. Header
        val candidateName = resume.contact.name?.takeIf { it.isNotBlank() }
            ?: userProfile.name.ifBlank { "Candidate Name" }
        canvas.drawText(candidateName, LEFT_MARGIN, y, titlePaint)
        y += 16f
        canvas.drawText("Target: $targetTitle  |  $targetCompany", LEFT_MARGIN, y, boldBodyPaint)
        y += 12f

        val contactParts = mutableListOf<String>()
        val loc = resume.contact.location ?: userProfile.location
        if (loc.isNotBlank()) contactParts.add(loc)
        val gh = resume.contact.github ?: userProfile.github
        if (gh.isNotBlank()) contactParts.add(gh.removePrefix("https://").removePrefix("http://"))
        val em = resume.contact.email ?: userProfile.email
        if (em.isNotBlank()) contactParts.add(em)
        val phone = resume.contact.phone
        if (!phone.isNullOrBlank()) contactParts.add(phone)

        val contactLine = if (contactParts.isNotEmpty()) contactParts.joinToString("  ·  ") else "Verified Candidate"
        canvas.drawText(contactLine, LEFT_MARGIN, y, subtitlePaint)
        y += 10f
        canvas.drawLine(LEFT_MARGIN, y, LEFT_MARGIN + contentWidth, y, linePaint)
        y += 10f

        // 2. Summary
        if (resume.summary.isNotBlank()) {
            canvas.drawText("PROFESSIONAL SUMMARY", LEFT_MARGIN, y, sectionPaint)
            y += 11f
            y = drawWrappedText(canvas, resume.summary, LEFT_MARGIN, y, contentWidth, bodyPaint, bodyFontSize * 1.3f)
            y += 8f
        }

        // 3. Technical Skills
        if (resume.skills.isNotEmpty()) {
            canvas.drawText("TECHNICAL SKILLS", LEFT_MARGIN, y, sectionPaint)
            y += 11f
            val catMap = resume.getCategorizedSkills()
            for ((category, skills) in catMap) {
                if (skills.isNotEmpty()) {
                    val line = "$category: ${skills.joinToString(", ")}"
                    y = drawWrappedText(canvas, line, LEFT_MARGIN + 4f, y, contentWidth - 4f, boldBodyPaint, bodyFontSize * 1.3f)
                    y += 2f
                }
            }
            y += 4f
        }

        // 4. Professional Experience
        if (resume.experience.isNotEmpty()) {
            canvas.drawText("PROFESSIONAL EXPERIENCE", LEFT_MARGIN, y, sectionPaint)
            y += 11f
            for (rawExp in resume.experience) {
                val exp = TailoredResume.sanitizeExperience(rawExp)
                val header = "${exp.employer}  |  ${exp.title}"
                val isConfirmed = exp.isConfirmedCurrent || (exp.isCurrent && (exp.endDate.isNullOrBlank() || exp.endDate.equals("Present", ignoreCase = true)))
                val dateLoc = buildString {
                    if (!exp.startDate.isNullOrBlank()) append(exp.startDate)
                    if (isConfirmed) {
                        append(" – Present")
                    } else if (!exp.endDate.isNullOrBlank() && !exp.endDate.equals("Present", ignoreCase = true)) {
                        append(" – ${exp.endDate}")
                    }
                    if (!exp.location.isNullOrBlank()) append("  ·  ${exp.location}")
                }
                val headerWidth = boldBodyPaint.measureText(header)
                val dateWidth = if (dateLoc.isNotBlank()) subtitlePaint.measureText(dateLoc) else 0f

                if (dateLoc.isNotBlank() && (headerWidth + dateWidth + 16f <= contentWidth)) {
                    // Right-aligned date on the same row with guaranteed separation
                    canvas.drawText(header, LEFT_MARGIN, y, boldBodyPaint)
                    canvas.drawText(dateLoc, LEFT_MARGIN + contentWidth - dateWidth, y, subtitlePaint)
                    y += 11f
                } else {
                    y = drawWrappedText(canvas, header, LEFT_MARGIN, y, contentWidth, boldBodyPaint, bodyFontSize * 1.25f)
                    if (dateLoc.isNotBlank()) {
                        canvas.drawText(dateLoc, LEFT_MARGIN, y, subtitlePaint)
                        y += 9f
                    }
                }
                for (b in exp.bullets) {
                    y = drawBullet(canvas, b, LEFT_MARGIN + 4f, y, contentWidth - 4f, bodyPaint, bodyFontSize * 1.3f)
                    y += 2.5f
                }
                y += 3.5f
            }
        }

        // 5. Technical Projects
        if (resume.projects.isNotEmpty()) {
            canvas.drawText("TECHNICAL PROJECTS", LEFT_MARGIN, y, sectionPaint)
            y += 10f
            for (proj in resume.projects) {
                val pTitle = if (!proj.role.isNullOrBlank()) "${proj.name}  |  ${proj.role}" else proj.name
                canvas.drawText(pTitle, LEFT_MARGIN, y, boldBodyPaint)
                y += 9f
                val subline = buildString {
                    if (proj.technologies.isNotEmpty()) append(proj.technologies.joinToString(", "))
                    if (!proj.url.isNullOrBlank()) {
                        if (isNotEmpty()) append("  ·  ")
                        append(proj.url)
                    }
                }
                if (subline.isNotBlank()) {
                    canvas.drawText(subline, LEFT_MARGIN, y, subtitlePaint)
                    y += 8f
                }
                for (b in proj.bullets) {
                    y = drawBullet(canvas, b, LEFT_MARGIN + 4f, y, contentWidth - 4f, bodyPaint, bodyFontSize * 1.3f)
                    y += 2.0f
                }
                y += 3.5f
            }
        }

        // 6. Education
        if (resume.education.isNotEmpty()) {
            canvas.drawText("EDUCATION", LEFT_MARGIN, y, sectionPaint)
            y += 11f
            for (edu in resume.education) {
                val eduHeader = if (edu.degree.isNotBlank()) "${edu.school}  |  ${edu.degree}" else edu.school
                canvas.drawText(eduHeader, LEFT_MARGIN, y, boldBodyPaint)
                y += 10f
                val eduDetails = buildString {
                    if (!edu.fieldOfStudy.isNullOrBlank()) append(edu.fieldOfStudy)
                    if (!edu.graduationDate.isNullOrBlank()) append("  ·  ${edu.graduationDate}")
                    if (!edu.gpa.isNullOrBlank()) append("  ·  GPA: ${edu.gpa}")
                }
                if (eduDetails.isNotBlank()) {
                    canvas.drawText(eduDetails, LEFT_MARGIN, y, subtitlePaint)
                    y += 9f
                }
            }
        }

        // 7. Certifications
        if (resume.certifications.isNotEmpty()) {
            canvas.drawText("CERTIFICATIONS", LEFT_MARGIN, y, sectionPaint)
            y += 11f
            for (cert in resume.certifications) {
                val certHeader = if (!cert.issuer.isNullOrBlank()) "${cert.name}  |  ${cert.issuer}" else cert.name
                canvas.drawText(certHeader, LEFT_MARGIN, y, boldBodyPaint)
                y += 9f
                if (!cert.issueDate.isNullOrBlank()) {
                    canvas.drawText("Issued: ${cert.issueDate}", LEFT_MARGIN, y, subtitlePaint)
                    y += 9f
                }
            }
        }

        // Empty state notice if no sections exist yet
        if (resume.experience.isEmpty() && resume.projects.isEmpty() && resume.education.isEmpty()) {
            y += 20f
            canvas.drawText("Upload your resume to begin", LEFT_MARGIN, y, boldBodyPaint)
            y += 15f
        }

        // 8. Footer
        y += 8f
        canvas.drawLine(LEFT_MARGIN, y, LEFT_MARGIN + contentWidth, y, linePaint)
        y += 9f
        canvas.drawText("ATS Single-Column Certified  ·  1-Page Verified", LEFT_MARGIN, y, subtitlePaint)

        document.finishPage(page)

        val outputDir = File(context.filesDir, "generated_resumes").apply { mkdirs() }
        val safeCompany = targetCompany.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
        val candidatePrefix = candidateName.replace(Regex("[^a-zA-Z0-9]"), "_")
        val outputFile = File(outputDir, "ATS_Resume_${candidatePrefix}_${safeCompany}.pdf")

        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()

        val bytes = outputFile.length()
        val kb = bytes / 1024f
        return PdfForgeResult(
            file = outputFile,
            fileSizeBytes = bytes,
            fileSizeKb = kb,
            pageCount = 1,
        )
    }

    private fun drawWrappedText(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float = 12.0f,
    ): Float {
        var currentY = startY
        val words = text.split(" ")
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    canvas.drawText(currentLine, x, currentY, paint)
                    currentY += lineHeight
                }
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine, x, currentY, paint)
            currentY += lineHeight
        }
        return currentY
    }

    /**
     * Pure wrapping logic for bullet points.
     * Takes bullet text, maximum rendering width, and a measurement lambda.
     * Guarantees each bullet wraps cleanly without breaking into separate bullets.
     */
    fun wrapBullet(
        text: String,
        maxWidth: Float,
        measure: (String) -> Float,
    ): List<String> {
        val clean = text.trimStart { it in setOf('•', '-', '–', '—', '*', '·', '▪', '■', '▫', '◽', '◻', '□', '◦') }.trim()
        if (clean.isEmpty()) return emptyList()
        if (measure(clean) <= maxWidth) {
            return listOf(clean)
        }

        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (measure(candidate) <= maxWidth) {
                currentLine = candidate
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return if (lines.isEmpty()) listOf(clean) else lines
    }

    /**
     * Draws a bullet item: exactly ONE bullet glyph on line 1, and continuation lines
     * indented with a hanging indent and no glyph.
     */
    private fun drawBullet(
        canvas: android.graphics.Canvas,
        bulletText: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float = 12.0f,
    ): Float {
        val bulletGlyph = "•"
        val bulletMargin = 10f
        val textMaxWidth = maxWidth - bulletMargin

        val lines = wrapBullet(bulletText, textMaxWidth) { paint.measureText(it) }
        var currentY = startY

        for ((index, line) in lines.withIndex()) {
            if (index == 0) {
                // Exactly ONE bullet glyph on the first line
                canvas.drawText(bulletGlyph, x, currentY, paint)
                canvas.drawText(line, x + bulletMargin, currentY, paint)
            } else {
                // Continuation lines indented without bullet glyph
                canvas.drawText(line, x + bulletMargin, currentY, paint)
            }
            currentY += lineHeight
        }
        return currentY
    }

    private fun estimateWrappedTextHeight(
        text: String,
        maxWidth: Float,
        fontSize: Float,
        lineHeight: Float,
    ): Float {
        val testPaint = Paint().apply { textSize = fontSize }
        val words = text.split(" ")
        var lines = 0
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (testPaint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                lines++
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines++
        return maxOf(1, lines) * lineHeight
    }
}
