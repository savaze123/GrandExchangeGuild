package com.osrs.careerguild.core.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

data class PdfForgeResult(
    val file: File,
    val fileSizeBytes: Long,
    val fileSizeKb: Float,
    val pageCount: Int,
)

/**
 * RESUME_FORGE_PDF — Lightweight ATS Single-Column PDF Generator.
 *
 * Directives from GEMINI.md:
 * 1. Uses Android's native [PdfDocument] API (zero heavy third-party PDF suites).
 * 2. Enforces clean, single-column UTF-8 typography.
 * 3. Generated PDFs strictly under 100KB for flawless ATS parsing.
 *
 * Node: RESUME_FORGE_PDF
 */
object ResumeForgePdfGenerator {

    fun forgeAtsResume(
        context       : Context,
        candidateName : String = "Savazek",
        targetTitle   : String,
        targetCompany : String,
        infusedSkills : List<String>,
        customSummary : String? = null,
    ): Result<PdfForgeResult> = runCatching {
        val document = PdfDocument()

        // Standard US Letter dimensions: 612 x 792 points (72 points/inch)
        val pageWidth = 612
        val pageHeight = 792
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // Paints
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val sectionPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
        }

        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val boldBodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        var y = 48f
        val leftMargin = 48f
        val contentWidth = pageWidth - (leftMargin * 2)

        // 1. Header: Name & Contact Info
        canvas.drawText(candidateName, leftMargin, y, titlePaint)
        y += 16f
        canvas.drawText("Target Quest: $targetTitle at $targetCompany", leftMargin, y, boldBodyPaint)
        y += 14f
        canvas.drawText("San Francisco, CA · savazek.dev · github.com/savazek · linkedin.com/in/savazek", leftMargin, y, subtitlePaint)
        y += 18f
        canvas.drawLine(leftMargin, y, leftMargin + contentWidth, y, linePaint)
        y += 20f

        // 2. Executive Summary (Single Column ATS Friendly)
        canvas.drawText("PROFESSIONAL SUMMARY", leftMargin, y, sectionPaint)
        y += 14f
        val summaryText = customSummary ?: "Results-driven Software Engineer with proven expertise in building high-performance distributed systems, on-device AI integration, and robust automation pipelines. Adept at leveraging modern tech stacks including ${infusedSkills.take(5).joinToString(", ")} to solve mission-critical engineering bottlenecks and scale resilient infrastructure."
        y = drawWrappedText(canvas, summaryText, leftMargin, y, contentWidth, bodyPaint, lineHeight = 13f)
        y += 16f

        // 3. Technical Skills (Infused from Skill Altar)
        canvas.drawText("CORE TECHNICAL SKILLS", leftMargin, y, sectionPaint)
        y += 14f
        canvas.drawText("• Tailored Stack: ${infusedSkills.joinToString("  •  ")}", leftMargin, y, boldBodyPaint)
        y += 14f
        canvas.drawText("• Languages: Kotlin, Python, SQL, C++, TypeScript, Bash", leftMargin, y, bodyPaint)
        y += 14f
        canvas.drawText("• AI & Systems: Gemini API, AICore, LLM Agents, RAG, PyTorch, LangGraph", leftMargin, y, bodyPaint)
        y += 14f
        canvas.drawText("• Data & Cloud: Kafka, Spark, Airflow, Hadoop, PostgreSQL, Docker, AWS", leftMargin, y, bodyPaint)
        y += 18f

        // 4. Relevant Experience (Tailored Bullet Points)
        canvas.drawText("PROFESSIONAL EXPERIENCE", leftMargin, y, sectionPaint)
        y += 16f

        // Role 1
        canvas.drawText("Senior Software & AI Engineer  |  Grand Exchange Guild", leftMargin, y, boldBodyPaint)
        y += 12f
        canvas.drawText("Jan 2024 – Present  ·  San Francisco, CA", leftMargin, y, subtitlePaint)
        y += 14f
        val bullets1 = listOf(
            "Architected 120Hz Filament 3D rendering pipeline and reactive MVI Compose client on Android (Tensor G5, 16KB ELF alignment).",
            "Implemented TokenGovernor budget control layer enforcing Gemini API spend ceilings with zero telemetry leak.",
            "Engineered encrypted local vault utilizing Room SQLite + SQLCipher with hardware-backed Keystore AES/GCM encryption.",
            "Developed automated ATS form-mapping engine with DOM traversal resolving Greenhouse, Lever, and Ashby endpoints."
        )
        for (b in bullets1) {
            y = drawWrappedText(canvas, "•  $b", leftMargin + 8f, y, contentWidth - 8f, bodyPaint, lineHeight = 13f)
            y += 4f
        }
        y += 10f

        // Role 2
        canvas.drawText("Data Platform & Systems Engineer  |  Apex Automation Labs", leftMargin, y, boldBodyPaint)
        y += 12f
        canvas.drawText("Jun 2021 – Dec 2023  ·  Remote", leftMargin, y, subtitlePaint)
        y += 14f
        val bullets2 = listOf(
            "Constructed resilient ETL streaming pipelines processing 100M+ events/day using Kafka, Airflow, and Python.",
            "Accelerated complex SQL queries by 45% via partitioning, index tuning, and distributed query execution plans.",
            "Integrated multi-agent RAG pipelines for real-time document extraction achieving 99.2% parsing fidelity."
        )
        for (b in bullets2) {
            y = drawWrappedText(canvas, "•  $b", leftMargin + 8f, y, contentWidth - 8f, bodyPaint, lineHeight = 13f)
            y += 4f
        }
        y += 12f

        // 5. Education
        canvas.drawText("EDUCATION", leftMargin, y, sectionPaint)
        y += 14f
        canvas.drawText("Bachelor of Science in Computer Science", leftMargin, y, boldBodyPaint)
        y += 12f
        canvas.drawText("Specialization: Distributed Systems & Artificial Intelligence  ·  Dean's Honors List", leftMargin, y, subtitlePaint)

        document.finishPage(page)

        // Write to output file in app filesDir
        val safeCompany = targetCompany.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
        val outFile = File(context.filesDir, "ATS_Resume_${safeCompany}.pdf")
        FileOutputStream(outFile).use { fos ->
            document.writeTo(fos)
        }
        document.close()

        val bytes = outFile.length()
        val kb = bytes / 1024f

        PdfForgeResult(
            file          = outFile,
            fileSizeBytes = bytes,
            fileSizeKb    = kb,
            pageCount     = 1,
        )
    }

    private fun drawWrappedText(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
    ): Float {
        var curY = startY
        val words = text.split(" ")
        var line = ""

        for (w in words) {
            val candidate = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(candidate) <= maxWidth) {
                line = candidate
            } else {
                canvas.drawText(line, x, curY, paint)
                curY += lineHeight
                line = w
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, x, curY, paint)
            curY += lineHeight
        }
        return curY
    }
}
