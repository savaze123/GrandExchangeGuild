package com.osrs.careerguild.presentation.dashboard.courierguild

import com.osrs.careerguild.core.resume.BaseResume
import com.osrs.careerguild.core.resume.ResumeExperience
import com.osrs.careerguild.core.resume.ResumeProject
import com.osrs.careerguild.presentation.dashboard.courierguild.CourierResumeAttachment.UploadFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class CourierInstructionTest {

    // ---------- §1 format selection & conversion
    @Test fun pdfPreferredWhenAllowed() {
        assertEquals(UploadFormat.PDF, CourierResumeAttachment.chooseUploadFormat(".pdf,.doc,.docx"))
        assertEquals(UploadFormat.PDF, CourierResumeAttachment.chooseUploadFormat(""))
        assertEquals(UploadFormat.PDF, CourierResumeAttachment.chooseUploadFormat("application/pdf"))
    }

    @Test fun convertsWhenPdfNotAccepted() {
        assertEquals(UploadFormat.DOCX, CourierResumeAttachment.chooseUploadFormat(".doc,.docx"))
        assertEquals(UploadFormat.DOC, CourierResumeAttachment.chooseUploadFormat("application/msword"))
        assertEquals(UploadFormat.TXT, CourierResumeAttachment.chooseUploadFormat(".txt"))
        assertEquals(UploadFormat.UNSUPPORTED, CourierResumeAttachment.chooseUploadFormat(".png"))
        assertEquals(UploadFormat.DOCX, CourierResumeAttachment.chooseUploadFormat("", "Accepted file types: DOC, DOCX"))
    }

    @Test fun docxPackageIsValidAndLossless() {
        val out = File.createTempFile("cg_test", ".docx")
        CourierResumeAttachment.writeDocx("JANE DOE\nBuilt <Kotlin> & Rust tools\n\nEXPERIENCE", out)
        ZipFile(out).use { zip ->
            assertTrue(zip.getEntry("[Content_Types].xml") != null)
            assertTrue(zip.getEntry("_rels/.rels") != null)
            val doc = zip.getInputStream(zip.getEntry("word/document.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(doc.contains("Built &lt;Kotlin&gt; &amp; Rust tools"))
            assertTrue(doc.contains("<w:b/>"))
        }
        out.delete()
    }

    @Test fun rtfEscapesSpecialChars() {
        val rtf = CourierResumeAttachment.buildRtf("a{b}\\c é")
        assertTrue(rtf.startsWith("{\\rtf1"))
        assertTrue(rtf.contains("a\\{b\\}\\\\c \\u233?"))
    }

    // ---------- §2 grounded answers
    private val base = BaseResume(
        experience = listOf(ResumeExperience(employer = "Acme", title = "Android Engineer", isCurrent = true, bullets = listOf("Built an offline sync engine in Kotlin"))),
        projects = listOf(ResumeProject(name = "GuildApp", technologies = listOf("Kotlin", "Compose"), bullets = listOf("Shipped a 3D hub"))),
        skills = listOf("Kotlin", "Compose", "Room"),
    )
    private val g = CourierResponseSynthesizer.groundingFrom(base, "Stripe", "Mobile Engineer", "https://github.com/x", "")

    @Test fun whyCompanyIsGroundedAndHumble() {
        val a = CourierResponseSynthesizer.answer("Why do you want to work at Stripe?", g)!!
        assertTrue(a.contains("Stripe"))
        assertTrue(a.contains("Android Engineer at Acme"))
        assertTrue(a.contains("built an offline sync engine in Kotlin"))
        assertTrue(a.contains("grateful"))
        assertFalse(a.contains("best", ignoreCase = true))
    }

    @Test fun projectUsesOnlyResumeProject() {
        val a = CourierResponseSynthesizer.answer("Tell us about a project you are proud of", g)!!
        assertTrue(a.contains("GuildApp"))
        assertTrue(a.contains("Kotlin and Compose"))
    }

    @Test fun sensitiveAndUnknownLeftForUser() {
        assertNull(CourierResponseSynthesizer.answer("What are your salary expectations?", g))
        assertNull(CourierResponseSynthesizer.answer("Favorite color of the moon", g))
    }

    @Test fun respectsMaxLength() {
        val a = CourierResponseSynthesizer.answer("Cover letter", g, maxLength = 120)!!
        assertTrue(a.length <= 120)
        assertTrue(a.endsWith("."))
    }

    @Test fun emptyResumeNeverInventsEmployer() {
        val empty = CourierResponseSynthesizer.groundingFrom(null, "Stripe", "Engineer", "", "")
        val a = CourierResponseSynthesizer.answer("Why are you interested in this role?", empty)!!
        assertFalse(a.contains(" at  "))
        assertNull(CourierResponseSynthesizer.answer("Describe a project", empty))
    }

    // ---------- §3/§5 guard parsing
    @Test fun guardParsing() {
        assertTrue(CourierGuardrails.parse("\"CAPTCHA:reCAPTCHA\"") is CourierGuardrails.ProbeResult.Captcha)
        assertTrue(CourierGuardrails.parse("\"HANDOFF:Skills assessment\"") is CourierGuardrails.ProbeResult.Handoff)
        assertTrue(CourierGuardrails.parse("\"CLEAR\"") is CourierGuardrails.ProbeResult.Clear)
        assertTrue(CourierGuardrails.parse(null) is CourierGuardrails.ProbeResult.Clear)
        assertEquals("External assessment (hirevue)", CourierGuardrails.handoffKindForUrl("https://app.hirevue.com/x"))
        assertNull(CourierGuardrails.handoffKindForUrl("https://boards.greenhouse.io/stripe"))
    }
}
