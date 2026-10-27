package com.osrs.careerguild.core.resume

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Extracts raw textual content from uploaded PDF or DOCX resume documents.
 *
 * PDF Strategy:
 * 1. Primary: Text layer extraction via pdfbox-android (fast, accurate for digital PDFs).
 * 2. Fallback: If text layer is empty (scanned image PDF), renders pages with [PdfRenderer]
 *    and executes on-device OCR using Google ML Kit Text Recognition v2.
 *
 * DOCX Strategy:
 * Pure stream decompression via [ZipInputStream], extracting and parsing `word/document.xml`
 * without third-party dependencies.
 */
class ResumeTextExtractor(private val context: Context) {

    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (t: Throwable) {
            android.util.Log.w("ResumeTextExtractor", "PDFBoxResourceLoader init warning: ${t.message}")
        }
    }

    suspend fun extractText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: ""
            val fileName = queryFileName(uri).lowercase()

            when {
                mimeType == "application/pdf" || fileName.endsWith(".pdf") -> {
                    extractFromPdf(uri)
                }
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                        fileName.endsWith(".docx") -> {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        extractFromDocx(stream)
                    } ?: throw IllegalStateException("Could not open input stream for $uri")
                }
                else -> {
                    // Try PDF first, then fallback to DOCX
                    try {
                        extractFromPdf(uri)
                    } catch (_: Exception) {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            extractFromDocx(stream)
                        } ?: throw IllegalArgumentException("Unsupported file type: $mimeType ($fileName)")
                    }
                }
            }
        }
    }

    private suspend fun extractFromPdf(uri: Uri): String {
        // Step 1: Text-layer extraction via PDFBox
        var textLayer = ""
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { document ->
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = true
                    textLayer = stripper.getText(document).trim()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ResumeTextExtractor", "PDFBox text-layer extraction failed: ${e.message}")
        }

        if (textLayer.length > 50) {
            return textLayer
        }

        // Step 2: Fallback to PdfRenderer + ML Kit OCR
        return performOcrOnPdf(uri)
    }

    private suspend fun performOcrOnPdf(uri: Uri): String {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Unable to open ParcelFileDescriptor for $uri")

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val extractedText = StringBuilder()

        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pageCount = renderer.pageCount
                for (pageIndex in 0 until pageCount) {
                    val page = renderer.openPage(pageIndex)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    try {
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        val visionText = recognizer.process(inputImage).awaitTask()
                        extractedText.append(visionText.text).append("\n\n")
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }

        val result = extractedText.toString().trim()
        if (result.isBlank()) {
            throw IllegalStateException("No readable text found in PDF (both text-layer and OCR were empty).")
        }
        return result
    }

    private fun extractFromDocx(stream: InputStream): String {
        val zip = ZipInputStream(stream)
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val xmlContent = zip.bufferedReader(Charsets.UTF_8).readText()
                return parseDocxXml(xmlContent)
            }
            entry = zip.nextEntry
        }
        throw IllegalStateException("word/document.xml not found inside DOCX archive.")
    }

    private fun parseDocxXml(xml: String): String {
        val paragraphRegex = Regex("<w:p[ >](.*?)</w:p>", setOf(RegexOption.DOT_MATCHES_ALL))
        val textRegex = Regex("<w:t[^>]*>(.*?)</w:t>", setOf(RegexOption.DOT_MATCHES_ALL))
        val sb = StringBuilder()

        for (pMatch in paragraphRegex.findAll(xml)) {
            val paragraphXml = pMatch.value
            val paragraphText = StringBuilder()
            for (tMatch in textRegex.findAll(paragraphXml)) {
                paragraphText.append(decodeXml(tMatch.groupValues[1]))
            }
            val line = paragraphText.toString().trim()
            if (line.isNotEmpty()) {
                sb.append(line).append("\n")
            }
        }

        val result = sb.toString().trim()
        if (result.isEmpty()) {
            // Fallback: extract all <w:t> nodes directly if paragraphs were structured differently
            for (tMatch in textRegex.findAll(xml)) {
                sb.append(decodeXml(tMatch.groupValues[1])).append(" ")
            }
            return sb.toString().trim()
        }
        return result
    }

    private fun decodeXml(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun queryFileName(uri: Uri): String {
        var name = ""
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: ""
                }
            }
        } catch (_: Exception) {}
        return name
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
