package com.osrs.careerguild.presentation.dashboard.courierguild

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.osrs.careerguild.data.local.dao.CourierTaskDao
import com.osrs.careerguild.data.local.entity.CourierTaskEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Represents one of the up to 5 concurrent worker terminals inside the Courier Agent Room UI
 * (instruction.md §5 & Reconciliation §2).
 */
data class CourierWorkerSlot(
    val slotIndex: Int,
    val taskId: String = "",
    val jobId: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val status: CourierTaskStatus = CourierTaskStatus.QUEUED,
    val blockedReason: String? = null,
    val isOccupied: Boolean = false,
)

/**
 * Headless multi-slot WebView orchestration host for the Courier background agent (Phase 1.4 & Reconciliation §2).
 *
 * Thread-Safety & Reliability Invariants:
 * 1. Every WebView instantiation, navigation, DOM injection, JS evaluation, and destruction is
 *    explicitly wrapped in `withContext(Dispatchers.Main)`.
 * 2. Up to `CourierAgentConfig.maxConcurrentWorkers` (5) concurrent slots, each owning its own WebView
 *    and sharing the persistent [CookieManager].
 * 3. `WebChromeClient.onCreateWindow` routes `target="_blank"` / `window.open` apply links back into
 *    the same worker WebView so popups never escape headless orchestration.
 * 4. `WebViewClient.onRenderProcessGone` returns `true` (preventing app crash), destroys the affected
 *    WebView on the main thread, transitions the task back to `QUEUED` with `attempts + 1` (via [CourierStateMachine]),
 *    and frees the slot so it can be recreated safely.
 * 5. [onTrimMemory] reduces the active slot ceiling under memory pressure and destroys idle WebViews.
 * 6. Every JavaScript literal parameter is escaped via [JSONObject.quote]; raw page text or model
 *    output is NEVER concatenated into executable JS.
 */
class CourierWebHost(
    context: Context,
    private val courierTaskDao: CourierTaskDao? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slotMutex = Mutex()

    @Volatile
    var effectiveMaxSlots: Int = CourierAgentConfig.maxConcurrentWorkers
        private set

    private data class SlotSession(
        val slotIndex: Int,
        var taskId: String,
        var jobId: String,
        var jobTitle: String,
        var company: String,
        var status: CourierTaskStatus,
        var webView: WebView?,
        var pendingUploadFile: File? = null,
        var lastHttpError: Int? = null,
        var rendererCrashed: Boolean = false,
        var pageFinishDeferred: CompletableDeferred<String>? = null,
    )

    private val activeSlots = ConcurrentHashMap<Int, SlotSession>()
    private val taskToSlot = ConcurrentHashMap<String, Int>()

    private val _workerSlots = MutableStateFlow<List<CourierWorkerSlot>>(buildEmptySlots())
    val workerSlots: StateFlow<List<CourierWorkerSlot>> = _workerSlots.asStateFlow()

    fun observeWorkerSlots(): Flow<List<CourierWorkerSlot>> = workerSlots

    /**
     * AssetLoader for serving local debug fixtures (`https://appassets.androidplatform.net/assets/courier_fixtures/...`)
     * in debug/instrumented verification without touching external servers (Phase 2.7).
     */
    private val assetLoader: WebViewAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(appContext))
            .build()
    }

    private fun buildEmptySlots(): List<CourierWorkerSlot> {
        val count = CourierAgentConfig.maxConcurrentWorkers.coerceAtLeast(1)
        return (0 until count).map { idx ->
            CourierWorkerSlot(slotIndex = idx, isOccupied = false)
        }
    }

    private fun publishSlotsState() {
        val count = CourierAgentConfig.maxConcurrentWorkers.coerceAtLeast(1)
        val snapshot = (0 until count).map { idx ->
            val session = activeSlots[idx]
            if (session != null) {
                CourierWorkerSlot(
                    slotIndex = idx,
                    taskId = session.taskId,
                    jobId = session.jobId,
                    jobTitle = session.jobTitle,
                    company = session.company,
                    status = session.status,
                    isOccupied = true,
                )
            } else {
                CourierWorkerSlot(slotIndex = idx, isOccupied = false)
            }
        }
        _workerSlots.value = snapshot
        CourierAutoApplyDispatcher.updateWorkerSlots(snapshot)
    }

    /**
     * Acquires an available worker slot (0 .. effectiveMaxSlots - 1) and creates its dedicated WebView
     * on `Dispatchers.Main`. Returns the assigned slot index, or `null` if all slots are busy.
     */
    suspend fun acquireSlot(task: CourierTaskEntity): Int? = slotMutex.withLock {
        val existingSlot = taskToSlot[task.taskId]
        if (existingSlot != null && activeSlots.containsKey(existingSlot)) {
            return@withLock existingSlot
        }
        val limit = effectiveMaxSlots.coerceIn(1, CourierAgentConfig.maxConcurrentWorkers)
        val freeIndex = (0 until limit).firstOrNull { !activeSlots.containsKey(it) } ?: return@withLock null

        val webView = withContext(Dispatchers.Main) {
            createConfiguredWebView(slotIndex = freeIndex, taskId = task.taskId)
        }
        val session = SlotSession(
            slotIndex = freeIndex,
            taskId = task.taskId,
            jobId = task.jobId,
            jobTitle = task.title,
            company = task.company,
            status = CourierTaskStatus.fromString(task.status),
            webView = webView,
        )
        activeSlots[freeIndex] = session
        taskToSlot[task.taskId] = freeIndex
        publishSlotsState()
        freeIndex
    }

    /**
     * Updates the status of the worker slot assigned to [taskId] and checkpoints state to Room.
     */
    suspend fun checkpointStep(
        task: CourierTaskEntity,
        status: CourierTaskStatus,
        step: String,
        currentUrl: String,
        filledFields: Map<String, String>,
    ): CourierTaskEntity {
        val filledJson = JSONObject(filledFields).toString()
        val now = System.currentTimeMillis()
        val updated = task.copy(
            status = status.name,
            step = step,
            currentUrl = currentUrl,
            filledFieldsJson = filledJson,
            updatedAt = now,
        )
        courierTaskDao?.checkpointStep(
            taskId = task.taskId,
            status = status.name,
            step = step,
            currentUrl = currentUrl,
            filledFieldsJson = filledJson,
            updatedAt = now,
        )
        taskToSlot[task.taskId]?.let { slotIdx ->
            activeSlots[slotIdx]?.status = status
            publishSlotsState()
        }
        return updated
    }

    /**
     * Releases the slot owned by [taskId] and destroys its WebView on `Dispatchers.Main`.
     */
    suspend fun releaseSlot(taskId: String) = slotMutex.withLock {
        val slotIdx = taskToSlot.remove(taskId) ?: return@withLock
        val session = activeSlots.remove(slotIdx)
        publishSlotsState()
        val wv = session?.webView
        if (wv != null) {
            withContext(Dispatchers.Main) {
                destroyWebViewSafely(wv)
            }
        }
    }

    /**
     * Immediately cancels the worker for [taskId], frees its slot, and destroys its WebView on `Dispatchers.Main`
     * (Reconciliation §3: "Cancel Worker" in Agent Room).
     */
    suspend fun cancelSlotAndDestroyWebView(taskId: String) {
        releaseSlot(taskId)
        courierTaskDao?.getById(taskId)?.let { existing ->
            val current = CourierTaskStatus.fromString(existing.status)
            if (CourierStateMachine.canTransition(current, CourierTaskStatus.CANCELLED)) {
                val cancelled = CourierStateMachine.transition(
                    existing,
                    CourierTaskStatus.CANCELLED,
                    step = "USER_CANCELLED",
                )
                courierTaskDao.upsert(cancelled)
            }
        }
    }

    /**
     * Handles Android `onTrimMemory`: temporarily reduces active slot ceiling and destroys any idle WebViews
     * on `Dispatchers.Main` because all WebViews share one renderer process (Reconciliation §2).
     */
    @Suppress("DEPRECATION")
    fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                effectiveMaxSlots = 1
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                effectiveMaxSlots = 2
            }
            else -> {
                effectiveMaxSlots = CourierAgentConfig.maxConcurrentWorkers
            }
        }
    }

    fun restoreMaxSlots() {
        effectiveMaxSlots = CourierAgentConfig.maxConcurrentWorkers
    }

    fun wasRendererCrashed(taskId: String): Boolean {
        val slotIdx = taskToSlot[taskId] ?: return false
        return activeSlots[slotIdx]?.rendererCrashed == true
    }

    fun getLastHttpError(taskId: String): Int? {
        val slotIdx = taskToSlot[taskId] ?: return null
        return activeSlots[slotIdx]?.lastHttpError
    }

    /**
     * Navigates the task's WebView to [url] on `Dispatchers.Main` and waits for `onPageFinished`.
     */
    suspend fun loadUrlAndWait(
        taskId: String,
        url: String,
        timeoutMs: Long = 20_000L,
    ): String? {
        val slotIdx = taskToSlot[taskId] ?: return null
        val session = activeSlots[slotIdx] ?: return null
        val deferred = CompletableDeferred<String>()
        session.pageFinishDeferred = deferred
        session.lastHttpError = null

        withContext(Dispatchers.Main) {
            val wv = session.webView ?: createConfiguredWebView(slotIdx, taskId).also { session.webView = it }
            wv.loadUrl(url)
        }

        val loadedUrl = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }
        return loadedUrl ?: getCurrentUrl(taskId)
    }

    suspend fun getCurrentUrl(taskId: String): String = withContext(Dispatchers.Main) {
        val slotIdx = taskToSlot[taskId] ?: return@withContext ""
        activeSlots[slotIdx]?.webView?.url.orEmpty()
    }

    /**
     * Evaluates a safe JavaScript snippet inside the task's WebView on `Dispatchers.Main`.
     */
    suspend fun evaluateJs(taskId: String, script: String): String? = withContext(Dispatchers.Main) {
        val slotIdx = taskToSlot[taskId] ?: return@withContext null
        val wv = activeSlots[slotIdx]?.webView ?: return@withContext null
        suspendCoroutine { cont ->
            try {
                wv.evaluateJavascript(script) { rawResult ->
                    cont.resume(unquoteJsResult(rawResult))
                }
            } catch (t: Throwable) {
                cont.resume(null)
            }
        }
    }

    /**
     * Extracts a compact (~6 KB max) JSON snapshot of visible, enabled form controls and click candidates.
     * Skips hidden, disabled, offscreen, `tabindex="-1"`, and honeypot fields.
     * Never returns raw HTML or untrusted script tags.
     */
    suspend fun extractDomSnapshot(taskId: String): List<DomControlSnapshot> {
        val raw = evaluateJs(taskId, buildCompactSnapshotExtractorJs()) ?: return emptyList()
        if (raw.isBlank() || raw == "null") return emptyList()
        return runCatching {
            json.decodeFromString<List<DomControlSnapshot>>(raw)
        }.getOrDefault(emptyList())
    }

    /**
     * Runs the existing [CourierGuardrails] probe on `Dispatchers.Main` to detect CAPTCHAs or
     * mandatory video/skills assessments.
     */
    suspend fun runGuardrailsProbe(taskId: String): CourierGuardrails.ProbeResult {
        val url = getCurrentUrl(taskId)
        CourierGuardrails.handoffKindForUrl(url)?.let {
            return CourierGuardrails.ProbeResult.Handoff(it)
        }
        val raw = evaluateJs(taskId, CourierGuardrails.buildGuardProbeJs())
        return CourierGuardrails.parse(raw)
    }

    /**
     * Injects the Forge resume into `<input type="file">` using [CourierResumeAttachment] DataTransfer
     * primary path, with fallback to a real touch event answered by `WebChromeClient.onShowFileChooser` (Phase 2.4).
     */
    suspend fun attachResumeFirst(
        taskId: String,
        forgePdfFile: File,
    ): Boolean {
        val slotIdx = taskToSlot[taskId] ?: return false
        val session = activeSlots[slotIdx] ?: return false

        // 1. Probe file input accept + hint using existing CourierResumeAttachment
        val probeRaw = evaluateJs(taskId, "JSON.stringify(${CourierResumeAttachment.buildResumeInputProbeJs()})")
            ?: return false
        val probeObj = runCatching { JSONObject(probeRaw) }.getOrNull() ?: return false
        if (!probeObj.optBoolean("found", false)) return false

        val accept = probeObj.optString("accept", "")
        val hint = probeObj.optString("hint", "")
        val format = CourierResumeAttachment.chooseUploadFormat(accept, hint)
        val prepared = withContext(Dispatchers.IO) {
            CourierResumeAttachment.prepareUpload(appContext, forgePdfFile, format)
        } ?: return false

        // 2. Primary: DataTransfer injection via CourierResumeAttachment
        val injectRes = evaluateJs(taskId, CourierResumeAttachment.buildResumeInjectJs(prepared.file, prepared.format))
            .orEmpty()
        if (injectRes.startsWith("ATTACHED:")) {
            waitForResumeAutofillToSettle(taskId)
            return true
        }

        // 3. Fallback: Stage file for onShowFileChooser and trigger real touch event on the file input
        session.pendingUploadFile = prepared.file
        val coordsRaw = evaluateJs(
            taskId,
            """
            (function() {
                const el = window.__cgResumeInput;
                if (!el) return null;
                const r = el.getBoundingClientRect();
                return JSON.stringify({ x: r.left + r.width / 2, y: r.top + r.height / 2 });
            })();
            """.trimIndent()
        )
        val coords = runCatching { JSONObject(coordsRaw.orEmpty()) }.getOrNull()
        if (coords != null) {
            withContext(Dispatchers.Main) {
                val wv = session.webView ?: return@withContext
                val x = coords.optDouble("x", 50.0).toFloat()
                val y = coords.optDouble("y", 50.0).toFloat()
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(now, now + 40L, MotionEvent.ACTION_UP, x, y, 0)
                wv.dispatchTouchEvent(down)
                wv.dispatchTouchEvent(up)
                down.recycle()
                up.recycle()
            }
            waitForResumeAutofillToSettle(taskId)
            return session.pendingUploadFile == null
        }
        return false
    }

    /**
     * Waits up to 15 seconds for ATS resume parsing / autofill spinner to settle (Phase 2.4).
     */
    private suspend fun waitForResumeAutofillToSettle(taskId: String, maxWaitMs: Long = 15_000L) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            val status = evaluateJs(
                taskId,
                """
                (function() {
                    const spinners = document.querySelectorAll('[class*="spinner"], [class*="loading"], [aria-busy="true"]');
                    const activeSpinners = Array.from(spinners).filter(el => el.offsetParent !== null);
                    return activeSpinners.length === 0 ? "SETTLED" : "PARSING";
                })();
                """.trimIndent()
            )
            if (status == "SETTLED") break
            kotlinx.coroutines.delay(500L)
        }
    }

    /**
     * Safely sets the value of a control identified by [elementId] using `JSONObject.quote` for
     * both the element ID and the value (Reconciliation §5: never concatenate unescaped strings).
     */
    suspend fun fillElementValueSafely(
        taskId: String,
        elementId: String,
        value: String,
    ): Boolean {
        val quotedId = JSONObject.quote(elementId)
        val quotedVal = JSONObject.quote(value)
        val res = evaluateJs(
            taskId,
            """
            (function() {
                const elId = $quotedId;
                const val = $quotedVal;
                const el = document.querySelector('[data-cg-el=' + JSON.stringify(elId) + ']') || document.getElementById(elId);
                if (!el) return "NOT_FOUND";
                try { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch (e) {}
                if (el.tagName === 'SELECT') {
                    const norm = s => (s || '').toLowerCase().replace(/[^a-z0-9 ]/g, ' ').replace(/\s+/g, ' ').trim();
                    const want = norm(val);
                    const opts = Array.from(el.options);
                    let match = opts.find(o => norm(o.text) === want || norm(o.value) === want);
                    if (!match) {
                        match = opts.find(o => norm(o.text).indexOf(want) !== -1 || (norm(o.text).length > 2 && want.indexOf(norm(o.text)) !== -1));
                    }
                    if (!match) return "NO_OPTION_MATCH";
                    el.value = match.value;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    return "FILLED";
                }
                if (el.type === 'checkbox' || el.type === 'radio') {
                    if (!el.checked) el.click();
                    return "FILLED";
                }
                el.focus();
                const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                const setter = Object.getOwnPropertyDescriptor(proto, 'value');
                if (setter && setter.set) {
                    setter.set.call(el, val);
                } else {
                    el.value = val;
                }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return "FILLED";
            })();
            """.trimIndent()
        )
        return res == "FILLED"
    }

    /**
     * Reads back the current value of [elementId] from the DOM (Phase 2.5: never trust ATS autofill;
     *Phase 4.1: verify every required field before `AUTO_SUBMIT`).
     */
    suspend fun readElementValueSafely(taskId: String, elementId: String): String {
        val quotedId = JSONObject.quote(elementId)
        return evaluateJs(
            taskId,
            """
            (function() {
                const elId = $quotedId;
                const el = document.querySelector('[data-cg-el=' + JSON.stringify(elId) + ']') || document.getElementById(elId);
                if (!el) return "";
                if (el.tagName === 'SELECT') {
                    const opt = el.options[el.selectedIndex];
                    return opt ? (opt.text || opt.value || '') : (el.value || '');
                }
                if (el.type === 'checkbox' || el.type === 'radio') {
                    return el.checked ? "true" : "";
                }
                return (el.value || '').trim();
            })();
            """.trimIndent()
        ).orEmpty()
    }

    /**
     * Clicks the element tagged with `data-cg-el=[elementId]` using `JSONObject.quote`.
     */
    suspend fun clickElementSafely(taskId: String, elementId: String): Boolean {
        val quotedId = JSONObject.quote(elementId)
        val res = evaluateJs(
            taskId,
            """
            (function() {
                const elId = $quotedId;
                const el = document.querySelector('[data-cg-el=' + JSON.stringify(elId) + ']') || document.getElementById(elId);
                if (!el) return "NOT_FOUND";
                try { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch (e) {}
                el.click();
                return "CLICKED";
            })();
            """.trimIndent()
        )
        return res == "CLICKED"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(slotIndex: Int, taskId: String): WebView {
        val wv = WebView(appContext)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(wv, true)
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                // Route new-tab / target="_blank" apply links into the SAME WebView (Phase 1.4)
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val redirectView = WebView(appContext).apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            v: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val targetUrl = request?.url?.toString()
                            if (!targetUrl.isNullOrBlank()) {
                                view?.loadUrl(targetUrl)
                            }
                            v?.destroy()
                            return true
                        }
                    }
                }
                transport.webView = redirectView
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                val session = activeSlots[slotIndex]
                val file = session?.pendingUploadFile
                if (file != null && file.exists()) {
                    session.pendingUploadFile = null
                    filePathCallback?.onReceiveValue(arrayOf(Uri.fromFile(file)))
                    return true
                }
                filePathCallback?.onReceiveValue(null)
                return true
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                return assetLoader.shouldInterceptRequest(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                runCatching { CookieManager.getInstance().flush() }
                val session = activeSlots[slotIndex]
                session?.pageFinishDeferred?.complete(url.orEmpty())
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    activeSlots[slotIndex]?.lastHttpError = error?.errorCode ?: -1
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    activeSlots[slotIndex]?.lastHttpError = errorResponse?.statusCode
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
            ): Boolean {
                // Reconciliation §2: Return true so the app never crashes when the shared WebView
                // renderer process dies; destroy the dead WebView on Main, move task back to QUEUED
                // with attempts + 1, and free the slot so it can be recreated cleanly.
                val session = activeSlots.remove(slotIndex)
                taskToSlot.remove(taskId)
                session?.rendererCrashed = true
                session?.pageFinishDeferred?.complete("")
                view?.let { destroyWebViewSafely(it) }
                publishSlotsState()

                ioScope.launch {
                    val existing = courierTaskDao?.getById(taskId)
                    if (existing != null) {
                        val recovered = CourierStateMachine.recoverOnStartup(existing)
                        if (recovered != null) {
                            courierTaskDao.upsert(recovered)
                        }
                    }
                }
                return true
            }
        }

        return wv
    }

    private fun destroyWebViewSafely(wv: WebView) {
        runCatching {
            wv.stopLoading()
            wv.loadUrl("about:blank")
            wv.clearHistory()
            wv.removeAllViews()
            wv.destroy()
        }
    }

    companion object {
        internal fun unquoteJsResult(raw: String?): String? {
            if (raw == null || raw == "null") return null
            if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
                return runCatching {
                    JSONObject("{\"v\":$raw}").getString("v")
                }.getOrDefault(raw.substring(1, raw.length - 1))
            }
            return raw
        }

        /**
         * Compact DOM Snapshot Extractor (Architecture Decisions):
         * - Extracts visible, enabled form controls (`input`, `select`, `textarea`, `[role="combobox"]`)
         *   and click candidates (`button`, `a`, `[role="button"]`, `input[type="submit"]`).
         * - Skips hidden, disabled, offscreen, `tabindex="-1"`, and honeypot (`opacity: 0`, `height < 2`,
         *   `aria-hidden="true"`, honeypot names like `website_url_hp` / `trap` / `b_`) fields.
         * - Assigns `data-cg-el="cg_el_N"` to each returned element for safe lookup without XPath/CSS injection.
         */
        fun buildCompactSnapshotExtractorJs(): String = """
            (function() {
                const out = [];
                const honeypotRe = /honeypot|trap|bot_field|confirm_email_hp|^b_[a-f0-9]{10}/i;
                const disclaimerRe = /need assistance|reasonable accommodation|accessibility|reach out to tinyurl/i;
                function isVisibleAndReal(el, allowHiddenFile) {
                    if (!el || el.disabled) return false;
                    if (el.getAttribute('aria-hidden') === 'true') return false;
                    if (el.getAttribute('tabindex') === '-1' && !allowHiddenFile) return false;
                    if (honeypotRe.test((el.name || '') + ' ' + (el.id || '') + ' ' + (el.className || ''))) return false;
                    if (allowHiddenFile) return true;
                    const style = window.getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity || '1') < 0.05) return false;
                    const rect = el.getBoundingClientRect();
                    if (rect.width < 4 || rect.height < 4) return false;
                    return true;
                }
                function labelFor(el) {
                    let t = '';
                    if (el.id) {
                        const l = document.querySelector('label[for=' + JSON.stringify(el.id) + ']');
                        if (l) t = l.innerText || l.textContent || '';
                    }
                    if (!t) {
                        const l = el.closest('label');
                        if (l) t = l.innerText || l.textContent || '';
                    }
                    if (!t) {
                        const box = el.closest('.field, .form-group, .application-question, fieldset');
                        if (box) t = (box.innerText || '').split('\n')[0];
                    }
                    if (!t) t = el.getAttribute('aria-label') || el.innerText || el.value || el.placeholder || '';
                    return (t || '').replace(/\s+/g, ' ').trim().substring(0, 140);
                }
                function containerHintFor(el) {
                    const c = el.closest('form, section, aside, footer, header, nav, [class*="newsletter"], [class*="cookie"], [class*="alert"], [class*="subscribe"], [class*="modal"]');
                    if (!c) return '';
                    return ((c.tagName || '') + ' ' + (c.id || '') + ' ' + (c.className || '') + ' ' + (c.getAttribute('aria-label') || ''))
                        .replace(/\s+/g, ' ').trim().substring(0, 100);
                }
                const nodes = Array.from(document.querySelectorAll(
                    'input, select, textarea, button, a[href], [role="button"], [role="combobox"], [role="listbox"]'
                ));
                let idx = 0;
                for (let i = 0; i < nodes.length && out.length < 48; i++) {
                    const el = nodes[i];
                    const tag = (el.tagName || '').toLowerCase();
                    const type = (el.getAttribute('type') || '').toLowerCase();
                    if (type === 'hidden') continue;
                    const isFile = tag === 'input' && type === 'file';
                    if (!isVisibleAndReal(el, isFile)) continue;
                    const lbl = labelFor(el);
                    if (disclaimerRe.test(lbl)) continue;
                    const elId = 'cg_el_' + idx++;
                    el.setAttribute('data-cg-el', elId);
                    const opts = tag === 'select'
                        ? Array.from(el.options).slice(0, 16).map(o => (o.text || o.value || '').trim()).filter(Boolean)
                        : [];
                    const req = !!(el.required || el.getAttribute('aria-required') === 'true' || /\*/.test(lbl));
                    const href = tag === 'a' ? (el.getAttribute('href') || '').substring(0, 140) : '';
                    const hasVal = tag === 'select' ? el.selectedIndex > 0 : !!(el.value && el.value.trim().length > 0);
                    out.push({
                        el_id: elId,
                        tag: tag,
                        type: type,
                        name: (el.name || '').substring(0, 60),
                        id: (el.id || '').substring(0, 60),
                        label: lbl,
                        placeholder: (el.placeholder || '').substring(0, 80),
                        options: opts,
                        required: req,
                        maxLength: el.maxLength > 0 ? el.maxLength : 0,
                        containerHint: containerHintFor(el),
                        hrefPath: href,
                        currentValuePresent: hasVal
                    });
                }
                return JSON.stringify(out);
            })();
        """.trimIndent()
    }
}
