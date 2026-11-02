package com.osrs.careerguild.presentation.dashboard.courierguild

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.osrs.careerguild.domain.model.JobListing
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Central dispatcher and security guard for the Courier Guild Auto-Apply Agent.
 *
 * Implements:
 * 1. LinkedIn Anti-Ban Platform Exclusion:
 *    Strictly excludes any listing whose `source` or `applicationUrl` (`atsUrl`/`applyUrl`)
 *    contains "LinkedIn" (case-insensitive) from automated WebView/DOM pipeline execution,
 *    routing users to an external browser tab as "Manual Apply Only".
 * 2. Credential Generation & Local Vault Cipher for 'Accounts Made by Courier'.
 */
object CourierAutoApplyDispatcher {

    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}?"
    private const val ALL_CHARS = UPPER + LOWER + DIGITS + SYMBOLS

    private val secureRandom = SecureRandom()

    /**
     * Inspects job board source and target application URL.
     * If `source` or `applicationUrl` contains "LinkedIn" (ignore case), returns `true`
     * to completely exclude the listing from automated application pipelines.
     */
    fun isExcludedFromAutoApply(source: String, applicationUrl: String): Boolean {
        return source.contains("linkedin", ignoreCase = true) ||
            applicationUrl.contains("linkedin", ignoreCase = true)
    }

    /**
     * Convenience overload for [JobListing] inspecting `source`, `applyUrl`, and `atsUrl`.
     */
    fun isExcludedFromAutoApply(job: JobListing): Boolean {
        val targetUrl = job.applyUrl.ifBlank { job.atsUrl }
        return isExcludedFromAutoApply(job.source, targetUrl) ||
            job.atsUrl.contains("linkedin", ignoreCase = true)
    }

    /**
     * Convenience overload for [QueuedApplication] in Courier Guild.
     */
    fun isExcludedFromAutoApply(app: QueuedApplication): Boolean {
        return isExcludedFromAutoApply(app.platform, app.atsUrl)
    }

    /**
     * Opens a Manual-Apply-Only listing directly in an external browser tab.
     */
    fun routeToExternalManualApply(context: Context, rawUrl: String) {
        val safeUrl = rawUrl.trim()
        if (safeUrl.isBlank()) return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Generates a cryptographically strong password (18 chars by default) containing
     * guaranteed uppercase, lowercase, digits, and symbols for ATS account creation gates.
     */
    fun generateStrongPassword(length: Int = 18): String {
        val effectiveLength = length.coerceAtLeast(16)
        val chars = MutableList(effectiveLength) {
            ALL_CHARS[secureRandom.nextInt(ALL_CHARS.length)]
        }
        // Guarantee at least one character from each character class
        chars[0] = UPPER[secureRandom.nextInt(UPPER.length)]
        chars[1] = LOWER[secureRandom.nextInt(LOWER.length)]
        chars[2] = DIGITS[secureRandom.nextInt(DIGITS.length)]
        chars[3] = SYMBOLS[secureRandom.nextInt(SYMBOLS.length)]
        chars.shuffle(secureRandom)
        return chars.joinToString("")
    }

    // ---------------------------------------------------------------------------
    // Field-level AES-GCM encryption layer on top of SQLCipher database storage
    // ---------------------------------------------------------------------------

    private val vaultKeyBytes = "GrandExchangeCourierVaultKey2026".toByteArray(Charsets.UTF_8).copyOf(32)

    fun encryptPassword(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return runCatching {
            val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(vaultKeyBytes, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
            val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
            "ENC1:" + Base64.getEncoder().encodeToString(combined)
        }.getOrDefault(plaintext)
    }

    fun decryptPassword(encrypted: String): String {
        if (!encrypted.startsWith("ENC1:")) return encrypted
        return runCatching {
            val raw = Base64.getDecoder().decode(encrypted.removePrefix("ENC1:"))
            val iv = raw.copyOfRange(0, 12)
            val cipherBytes = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(vaultKeyBytes, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrDefault(encrypted)
    }
}
