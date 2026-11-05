package com.osrs.careerguild.presentation.dashboard.courierguild

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * instruction.md §1 — Resume Import & Format Conversion.
 *
 * - Strict Selection: only the tailored resume the Scribe Tower (Forge) produced for this exact
 *   posting is ever attached. There is NO generic / random fallback: if the Forge file is missing
 *   the upload step is skipped and flagged for the user.
 * - Dynamic Conversion: the portal's `accept` attribute (and nearby hint text such as
 *   "DOC, DOCX only") decides the upload format. A Forge `.pdf` is converted on-device to
 *   `.docx` / `.doc` / `.txt` when the portal does not accept PDF. Conversion only re-wraps the
 *   text of the Forge PDF; nothing is added or removed.
 */
object CourierResumeAttachment {

    enum class UploadFormat(val extension: String, val mimeType: String) {
        PDF("pdf", "application/pdf"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        /** Legacy Word: emitted as a Word-compatible RTF body (Word and ATS parsers open it natively). */
        DOC("doc", "application/msword"),
        TXT("txt", "text/plain"),
        UNSUPPORTED("", ""),
    }

    data class PreparedUpload(val file: File, val format: UploadFormat, val converted: Boolean)

    /**
     * Resolves the Forge resume crafted for [app]. Checks the exact file name handed over by the
     * Scribe Tower first, then the Forge's deterministic naming scheme for this company.
     * Returns null (never a generic resume) when no Forge file exists.
     */
    fun resolveForgeResume(context: Context, app: QueuedApplication, candidateNames: List<String>): File? {
        val dir = File(context.filesDir, "generated_resumes")
        if (!dir.isDirectory) return null
        File(dir, app.attachedPdf).takeIf { it.isFile && it.length() > 0 }?.let { return it }
        val safeCompany = app.company.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
        return candidateNames
            .filter { it.isNotBlank() }
            .map { File(dir, "ATS_Resume_${it.replace(Regex("[^a-zA-Z0-9]"), "_")}_$safeCompany.pdf") }
            .firstOrNull { it.isFile && it.length() > 0 }
    }

    /**
     * Picks the upload format from the portal's `accept` attribute, falling back to format
     * names mentioned in the upload field's helper text. PDF is preferred whenever allowed.
     */
    fun chooseUploadFormat(accept: String, hintText: String = ""): UploadFormat {
        val tokens = accept.lowercase().split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.any { it == "*/*" || it == "*" }) {
            return chooseFromHint(hintText)
        }
        fun has(vararg t: String) = tokens.any { it in t }
        return when {
            has(".pdf", "application/pdf", "pdf") -> UploadFormat.PDF
            has(".docx", "docx", UploadFormat.DOCX.mimeType) -> UploadFormat.DOCX
            has(".doc", "doc", "application/msword") -> UploadFormat.DOC
            has(".txt", "txt", "text/plain", "text/*") -> UploadFormat.TXT
            else -> UploadFormat.UNSUPPORTED
        }
    }

    private fun chooseFromHint(hintText: String): UploadFormat {
        val h = hintText.lowercase()
        val mentionsAny = Regex("\\b(pdf|docx?|txt|rtf)\\b").containsMatchIn(h)
        return when {
            !mentionsAny || Regex("\\bpdf\\b").containsMatchIn(h) -> UploadFormat.PDF
            Regex("\\bdocx\\b").containsMatchIn(h) -> UploadFormat.DOCX
            Regex("\\bdoc\\b").containsMatchIn(h) -> UploadFormat.DOC
            Regex("\\btxt\\b").containsMatchIn(h) -> UploadFormat.TXT
            else -> UploadFormat.PDF
        }
    }

    /** Extracts the text layer of the Forge PDF via pdfbox-android (already a project dependency). */
    fun extractPdfText(context: Context, pdf: File): String {
        runCatching { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext) }
        return com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdf).use { doc ->
            com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
        }
    }

    /**
     * Produces the file to upload in [format]. PDF passes the Forge file through untouched;
     * other formats are written next to it under `generated_resumes/converted/`.
     */
    fun prepareUpload(context: Context, forgePdf: File, format: UploadFormat): PreparedUpload? {
        if (format == UploadFormat.UNSUPPORTED) return null
        if (format == UploadFormat.PDF) return PreparedUpload(forgePdf, format, converted = false)
        val text = extractPdfText(context, forgePdf)
        if (text.isBlank()) return null
        val outDir = File(forgePdf.parentFile, "converted").apply { mkdirs() }
        val out = File(outDir, "${forgePdf.nameWithoutExtension}.${format.extension}")
        when (format) {
            UploadFormat.DOCX -> writeDocx(text, out)
            UploadFormat.DOC -> out.writeText(buildRtf(text), Charsets.US_ASCII)
            UploadFormat.TXT -> out.writeText(text, Charsets.UTF_8)
            else -> return null
        }
        return PreparedUpload(out, format, converted = true)
    }

    // ---------------------------------------------------------------- DOCX / RTF writers

    private val invalidXmlChars = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")

    private fun xmlEscape(s: String): String = s.replace(invalidXmlChars, "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun isHeading(line: String): Boolean =
        line.length in 3..40 && line.any { it.isLetter() } && line == line.uppercase()

    /** Builds `word/document.xml` (one paragraph per non-blank line; ALL-CAPS lines bolded). */
    fun buildDocumentXml(text: String): String {
        val body = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.joinToString("") { line ->
            val rPr = if (isHeading(line.trim())) "<w:rPr><w:b/></w:rPr>" else ""
            "<w:p><w:r>$rPr<w:t xml:space=\"preserve\">${xmlEscape(line)}</w:t></w:r></w:p>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body>$body<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/>" +
            "<w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\"/></w:sectPr></w:body></w:document>"
    }

    /** Writes a minimal, spec-valid OOXML package without third-party dependencies. */
    fun writeDocx(text: String, out: File) {
        val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "</Types>"
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            listOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rels,
                "word/document.xml" to buildDocumentXml(text),
            ).forEach { (name, xml) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    /** Word-compatible RTF body (non-ASCII escaped as \uN? so the file stays 7-bit clean). */
    fun buildRtf(text: String): String {
        val sb = StringBuilder("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Helvetica;}}\\f0\\fs20 ")
        text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.forEach { line ->
            val heading = isHeading(line.trim())
            if (heading) sb.append("{\\b ")
            line.forEach { c ->
                when {
                    c == '\\' || c == '{' || c == '}' -> sb.append('\\').append(c)
                    c.code > 127 -> sb.append("\\u").append(c.code.toShort()).append('?')
                    c.code < 32 -> Unit
                    else -> sb.append(c)
                }
            }
            if (heading) sb.append("}")
            sb.append("\\par\n")
        }
        return sb.append("}").toString()
    }

    // ---------------------------------------------------------------- WebView JS bridges

    /**
     * Locates the résumé `<input type=file>` (hidden inputs included — most ATS style a button
     * over a hidden input) and reports its `accept` attribute + helper text.
     * Returns an object: { found, accept, hint }.
     */
    fun buildResumeInputProbeJs(): String = """
        (function() {
            const inputs = Array.from(document.querySelectorAll('input[type="file"]'));
            if (!inputs.length) return { found: false, accept: '', hint: '' };
            function ctx(el) {
                let t = (el.name || '') + ' ' + (el.id || '') + ' ' + (el.getAttribute('aria-label') || '');
                const box = el.closest('fieldset, .field, .form-group, [class*="upload"], [class*="resume"], div');
                if (box) t += ' ' + (box.innerText || '');
                return t.toLowerCase();
            }
            let best = null;
            for (const el of inputs) {
                const c = ctx(el);
                if (/cover/.test(c) && !/resume|cv/.test(c)) continue;
                if (/resume|résumé|cv\b/.test(c)) { best = el; break; }
                if (!best) best = el;
            }
            window.__cgResumeInput = best;
            return { found: !!best, accept: best ? (best.getAttribute('accept') || '') : '', hint: best ? ctx(best).substring(0, 300) : '' };
        })();
    """.trimIndent()

    /**
     * Attaches the prepared file to the résumé input via DataTransfer (no OS picker needed),
     * highlighting the field with the amber overlay.
     */
    fun buildResumeInjectJs(file: File, format: UploadFormat): String {
        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val name = file.name.replace("'", "")
        return """
            (function() {
                const input = window.__cgResumeInput;
                if (!input) return 'NO_INPUT';
                try {
                    const bin = atob('$b64');
                    const bytes = new Uint8Array(bin.length);
                    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
                    const f = new File([bytes], '$name', { type: '${format.mimeType}' });
                    const dt = new DataTransfer();
                    dt.items.add(f);
                    input.files = dt.files;
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                    const target = input.offsetParent ? input : (input.closest('label, div') || input);
                    if (window.__cgTriggerClickRipple) window.__cgTriggerClickRipple(target);
                    if (window.__cgUpdateAmberBox) window.__cgUpdateAmberBox(target, '📎 Attached ' + f.name);
                    return 'ATTACHED:' + f.name;
                } catch (e) {
                    return 'ERROR:' + e.message;
                }
            })();
        """.trimIndent()
    }
}
