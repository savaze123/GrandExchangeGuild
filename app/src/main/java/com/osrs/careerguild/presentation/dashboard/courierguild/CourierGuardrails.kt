package com.osrs.careerguild.presentation.dashboard.courierguild

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * instruction.md §3 (CAPTCHA detection) and §5 (Video / Assessment hard stops).
 *
 * A single in-page probe classifies the current portal state:
 *  - `CAPTCHA:<kind>`  → pause pipeline and spawn the orange Quest orb.
 *  - `HANDOFF:<kind>`  → mandatory video intro / recorded interview / external assessment:
 *                        halt, log state, push a standard system notification.
 *  - `CLEAR`           → continue.
 */
object CourierGuardrails {

    sealed interface ProbeResult {
        data object Clear : ProbeResult
        data class Captcha(val kind: String) : ProbeResult
        data class Handoff(val kind: String) : ProbeResult
    }

    fun parse(raw: String?): ProbeResult {
        val r = raw.orEmpty().trim().trim('"')
        return when {
            r.startsWith("CAPTCHA:") -> ProbeResult.Captcha(r.substringAfter(":"))
            r.startsWith("HANDOFF:") -> ProbeResult.Handoff(r.substringAfter(":"))
            else -> ProbeResult.Clear
        }
    }

    private val GUEST_APPLY_REGEX = Regex(
        "guest apply|apply as guest|apply without (an? )?account|continue as guest|" +
            "autofill with resume|apply with resume|upload resume to start|upload resume|" +
            "quick apply|continue without (an? )?account",
        RegexOption.IGNORE_CASE
    )

    private val GATEKEEPER_NEXT_REGEX = Regex(
        "^next\\b|^continue\\b|^proceed\\b|^start application\\b|^get started\\b|save & continue|save and continue|next step|submit email",
        RegexOption.IGNORE_CASE
    )

    fun findResumeFileInput(controls: List<DomControlSnapshot>): DomControlSnapshot? {
        val fileInputs = controls.filter { it.tag.equals("input", ignoreCase = true) && it.type.equals("file", ignoreCase = true) }
        if (fileInputs.isEmpty()) return null
        return fileInputs.firstOrNull {
            val text = "${it.label} ${it.name} ${it.id} ${it.placeholder}".lowercase()
            Regex("resume|cv|curriculum").containsMatchIn(text)
        } ?: fileInputs.first()
    }

    fun findGuestApplyTrigger(controls: List<DomControlSnapshot>): DomControlSnapshot? {
        return controls.firstOrNull { ctrl ->
            val tag = ctrl.tag.lowercase()
            val type = ctrl.type.lowercase()
            val isClickable = tag in setOf("a", "button") || (tag == "input" && type in setOf("button", "submit")) ||
                ctrl.containerHint.contains("button", ignoreCase = true)
            if (!isClickable) return@firstOrNull false
            val combined = "${ctrl.label} ${ctrl.id} ${ctrl.name}".trim()
            GUEST_APPLY_REGEX.containsMatchIn(combined)
        }
    }

    fun findGatekeeperNextButton(controls: List<DomControlSnapshot>): DomControlSnapshot? {
        return controls.firstOrNull { ctrl ->
            val tag = ctrl.tag.lowercase()
            val type = ctrl.type.lowercase()
            val isClickable = tag in setOf("a", "button") || (tag == "input" && type in setOf("button", "submit")) ||
                ctrl.containerHint.contains("button", ignoreCase = true)
            if (!isClickable) return@firstOrNull false
            val label = ctrl.label.trim()
            if (label.isBlank()) return@firstOrNull false
            if (Regex("cookie|consent|reject|accept|close|privacy|terms", RegexOption.IGNORE_CASE).containsMatchIn(label)) {
                return@firstOrNull false
            }
            GATEKEEPER_NEXT_REGEX.containsMatchIn(label)
        }
    }

    fun isMultiStepGatekeeper(controls: List<DomControlSnapshot>): Boolean {
        if (findResumeFileInput(controls) != null) return false
        findGatekeeperNextButton(controls) ?: return false
        val fillable = controls.filter {
            val tag = it.tag.lowercase()
            val type = it.type.lowercase()
            (tag in setOf("input", "select", "textarea")) &&
                type !in setOf("file", "button", "submit", "reset", "hidden") &&
                !CourierApplyTriggerFinder.isNewsletterOrMarketingTrap(it)
        }
        val hasPassword = fillable.any { it.type.equals("password", ignoreCase = true) }
        return !hasPassword && fillable.size in 1..8
    }

    /** Hosts of external assessment / one-way video interview vendors. */
    val HANDOFF_HOSTS = listOf(
        "hirevue", "codility", "hackerrank", "codesignal", "testgorilla", "pymetrics", "vidcruiter",
        "sparkhire", "willo.video", "harver", "criteriacorp", "shl.com", "mettl", "karat.com",
        "myinterview", "modernhire", "talview", "coderbyte", "testdome", "qualified.io",
    )

    /** Static accessibility / accommodation footer phrases that must never trigger a worker pause. */
    val ACCESSIBILITY_IGNORE_PHRASES = listOf(
        "need assistance",
        "reasonable accommodation",
        "accessibility",
        "reach out to tinyurl",
    )

    fun isIgnoredDisclaimer(text: String): Boolean {
        val lower = text.lowercase()
        return ACCESSIBILITY_IGNORE_PHRASES.any { lower.contains(it) }
    }

    /** Pure URL check (unit-testable) used alongside the DOM probe. */
    fun handoffKindForUrl(url: String): String? {
        val u = url.lowercase()
        if (u.contains("tinyurl")) return null
        return HANDOFF_HOSTS.firstOrNull { u.contains(it) }?.let { "External assessment ($it)" }
    }

    fun buildGuardProbeJs(): String {
        val hosts = HANDOFF_HOSTS.joinToString("|") { it.replace(".", "\\.") }
        return """
            (function() {
                const vis = el => { if (!el) return false; const r = el.getBoundingClientRect(); return r.width >= 60 && r.height >= 40 && getComputedStyle(el).visibility !== 'hidden'; };
                const isDisclaimer = txt => /need assistance|reasonable accommodation|accessibility|reach out to tinyurl/i.test(txt || '');
                // ---------- CAPTCHA ----------
                const solved = Array.from(document.querySelectorAll('textarea[name="g-recaptcha-response"], textarea[name="h-captcha-response"], input[name="cf-turnstile-response"]')).some(t => (t.value || '').length > 20);
                if (/just a moment|attention required|verify you are human/i.test(document.title)) return 'CAPTCHA:Cloudflare challenge';
                if (document.querySelector('#challenge-running, #challenge-form, #cf-challenge-running')) return 'CAPTCHA:Cloudflare challenge';
                if (!solved) {
                    const frames = Array.from(document.querySelectorAll('iframe'));
                    for (const f of frames) {
                        const src = (f.src || '') + ' ' + (f.title || '');
                        if (f.closest('.grecaptcha-badge')) continue;
                        if (!vis(f)) continue;
                        if (/recaptcha/i.test(src)) return 'CAPTCHA:reCAPTCHA';
                        if (/hcaptcha/i.test(src)) return 'CAPTCHA:hCaptcha';
                        if (/challenges\.cloudflare\.com|turnstile/i.test(src)) return 'CAPTCHA:Cloudflare Turnstile';
                        if (/arkoselabs|funcaptcha/i.test(src)) return 'CAPTCHA:Arkose';
                    }
                    if (vis(document.querySelector('#px-captcha'))) return 'CAPTCHA:Press & Hold';
                }
                // ---------- VIDEO / ASSESSMENT HARD STOP ----------
                const hostRe = /$hosts/i;
                if (hostRe.test(location.hostname)) return 'HANDOFF:External assessment (' + location.hostname + ')';
                for (const f of Array.from(document.querySelectorAll('iframe'))) {
                    if (hostRe.test(f.src || '') && vis(f)) return 'HANDOFF:Embedded assessment';
                }
                // Headings + action buttons only, excluding footer / accessibility disclaimers.
                const ui = Array.from(document.querySelectorAll('h1, h2, h3, button, [role="button"], input[type="submit"]'))
                    .filter(e => e.offsetParent !== null && !e.closest('footer, [role="contentinfo"]'))
                    .map(e => (e.innerText || e.value || '').trim())
                    .filter(t => !isDisclaimer(t))
                    .join(' \n ').substring(0, 20000);
                if (/record (a|your) video|video (introduction|intro|interview|response)|one-way (video )?interview|start recording|allow camera/i.test(ui)) return 'HANDOFF:Video introduction / recorded interview';
                if (/(start|begin|take) (the|your)? ?(online |skills |coding |technical )?assessment|coding (challenge|test)|skills assessment|online assessment/i.test(ui)) return 'HANDOFF:Skills assessment';
                if (document.querySelector('input[type="file"][capture], input[type="file"][accept^="video"]') && /video/i.test(ui)) return 'HANDOFF:Video upload';
                return 'CLEAR';
            })();
        """.trimIndent()
    }

    // ---------------------------------------------------------------- System notification (§5)

    private const val CHANNEL_ID = "courier_handoff"

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Pushes a standard notification-bar alert so the user can take over manually. Returns false if blocked. */
    @android.annotation.SuppressLint("MissingPermission")
    fun notifyHandoff(context: Context, company: String, title: String, reason: String): Boolean {
        if (!canPostNotifications(context)) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Courier hand-offs", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Applications paused for manual video interviews or assessments"
                }
            )
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Courier paused: $company")
            .setContentText("$reason — please take over manually.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title at $company needs you: $reason. The Courier halted and logged the application state; continue manually in the Live Agent Room."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { if (pi != null) setContentIntent(pi) }
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify((company + title).hashCode(), n)
            true
        }.getOrDefault(false)
    }
}
