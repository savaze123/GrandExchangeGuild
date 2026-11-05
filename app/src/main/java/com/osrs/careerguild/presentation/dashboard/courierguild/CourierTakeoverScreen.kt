package com.osrs.careerguild.presentation.dashboard.courierguild

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewAssetLoader
import com.osrs.careerguild.core.forge.ApplicantAnswerPresets
import com.osrs.careerguild.core.forge.PresetInputType
import com.osrs.careerguild.data.local.entity.CourierTaskEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

private val TakeoverGold = Color(0xFFFFD700)
private val TakeoverStone = Color(0xFF3D2B1F)
private val TakeoverDarkStone = Color(0xFF2B1D14)
private val TakeoverBorder = Color(0xFF1A0F08)
private val TakeoverDim = Color(0xFF9E8A6A)

/**
 * Interactive Takeover Screen (Phase 1.5 & Phase 4.2).
 *
 * - Opens the saved `current_url` (or `job_url`) in a visible [WebView] sharing the persistent [CookieManager]
 *   so the user can solve a CAPTCHA, log in, or review/fill missing fields.
 * - Renders an inline missing-answers editor for `blocked_fields_json` with a "Remember this answer"
 *   checkbox that saves answers into `ApplicantAnswers` (`applicant_answers` table).
 * - Detects ATS confirmation navigation (`/thanks`, `thank you for applying`, `application submitted`)
 *   to mark the task `SUBMITTED` automatically, and provides an explicit `"I submitted it"` button.
 * - Provides `"Resume Courier"`, `"Snooze"`, and `"Cancel"` actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CourierTakeoverScreen(
    task: CourierTaskEntity,
    onResumeTask: (updatedUrl: String, enteredAnswers: Map<String, String>, rememberedAnswers: Map<String, String>) -> Unit,
    onMarkSubmitted: (confirmationNote: String) -> Unit,
    onSnoozeTask: () -> Unit,
    onCancelTask: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val initialUrl = task.currentUrl.ifBlank { task.jobUrl }
    var liveUrl by remember(task.taskId) { mutableStateOf(initialUrl) }
    var autoConfirmed by remember(task.taskId) { mutableStateOf(false) }

    val blockedFields: List<String> = remember(task.blockedFieldsJson) {
        parseBlockedFieldsJson(task.blockedFieldsJson)
    }

    val fieldInputs = remember(task.taskId, blockedFields) {
        mutableStateMapOf<String, String>().apply {
            blockedFields.forEach { put(it, "") }
        }
    }
    val rememberFlags = remember(task.taskId, blockedFields) {
        mutableStateMapOf<String, Boolean>().apply {
            blockedFields.forEach { put(it, true) }
        }
    }

    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context.applicationContext))
            .build()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.94f)
            .border(2.dp, TakeoverGold, RoundedCornerShape(12.dp)),
        containerColor = TakeoverDarkStone,
        shape = RoundedCornerShape(12.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "🛡 Courier Takeover · ${task.company.ifBlank { task.ats.uppercase() }}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TakeoverGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF33200A),
                        border = BorderStroke(1.dp, Color(0xFFFFA726)),
                    ) {
                        Text(
                            text = task.blockedReason ?: task.status,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = Color(0xFFFFA726),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = "${task.title} · URL: $liveUrl",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = TakeoverDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (blockedFields.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1F1610),
                        border = BorderStroke(1.dp, Color(0xFFFFA726)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Missing / Blocked Fields (Fill below or directly in the form)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TakeoverGold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(blockedFields) { fieldLabel ->
                                    val preset = remember(fieldLabel) { ApplicantAnswerPresets.findPreset(fieldLabel) }
                                    val currentVal = fieldInputs[fieldLabel].orEmpty()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(TakeoverStone, RoundedCornerShape(6.dp))
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = preset?.label ?: fieldLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White,
                                        )
                                        when (val inputType = preset?.inputType) {
                                            is PresetInputType.SingleChoice -> {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    inputType.options.forEach { opt ->
                                                        val isSelected = currentVal.equals(opt, ignoreCase = true)
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = if (isSelected) TakeoverGold else Color(0xFF1F1610),
                                                            border = BorderStroke(
                                                                1.dp,
                                                                if (isSelected) TakeoverGold else TakeoverBorder,
                                                            ),
                                                            modifier = Modifier.clickable {
                                                                fieldInputs[fieldLabel] = if (isSelected) "" else opt
                                                            },
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                            ) {
                                                                RadioButton(
                                                                    selected = isSelected,
                                                                    onClick = null,
                                                                    colors = RadioButtonDefaults.colors(
                                                                        selectedColor = Color.Black,
                                                                        unselectedColor = TakeoverDim,
                                                                    ),
                                                                    modifier = Modifier.size(12.dp),
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = opt,
                                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                                        fontSize = 10.sp,
                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                    ),
                                                                    color = if (isSelected) Color.Black else Color.White,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            is PresetInputType.StartDate -> {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val isCustomDate = currentVal.isNotBlank() && inputType.quickOptions.none { it.equals(currentVal, ignoreCase = true) }
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    inputType.quickOptions.forEach { opt ->
                                                        val isSelected = currentVal.equals(opt, ignoreCase = true)
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = if (isSelected) TakeoverGold else Color(0xFF1F1610),
                                                            border = BorderStroke(
                                                                1.dp,
                                                                if (isSelected) TakeoverGold else TakeoverBorder,
                                                            ),
                                                            modifier = Modifier.clickable {
                                                                fieldInputs[fieldLabel] = if (isSelected) "" else opt
                                                            },
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                            ) {
                                                                RadioButton(
                                                                    selected = isSelected,
                                                                    onClick = null,
                                                                    colors = RadioButtonDefaults.colors(
                                                                        selectedColor = Color.Black,
                                                                        unselectedColor = TakeoverDim,
                                                                    ),
                                                                    modifier = Modifier.size(12.dp),
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = opt,
                                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                                        fontSize = 10.sp,
                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                    ),
                                                                    color = if (isSelected) Color.Black else Color.White,
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isCustomDate) Color(0xFF00E5FF) else Color(0xFF10262E),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            if (isCustomDate) Color(0xFF00E5FF) else Color(0xFF0A4452),
                                                        ),
                                                        modifier = Modifier.clickable {
                                                            val cal = Calendar.getInstance()
                                                            DatePickerDialog(
                                                                context,
                                                                { _, y, m, d ->
                                                                    val chosen = LocalDate.of(y, m + 1, d)
                                                                    fieldInputs[fieldLabel] = chosen.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                                                },
                                                                cal.get(Calendar.YEAR),
                                                                cal.get(Calendar.MONTH),
                                                                cal.get(Calendar.DAY_OF_MONTH),
                                                            ).show()
                                                        },
                                                    ) {
                                                        Text(
                                                            text = if (isCustomDate) "📅 $currentVal" else "📅 Pick Date...",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = if (isCustomDate) FontWeight.Bold else FontWeight.Normal,
                                                            ),
                                                            color = if (isCustomDate) Color.Black else Color(0xFF00E5FF),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            else -> {
                                                OutlinedTextField(
                                                    value = currentVal,
                                                    onValueChange = { fieldInputs[fieldLabel] = it },
                                                    placeholder = {
                                                        Text("Enter verified answer...", fontSize = 10.sp, color = TakeoverDim)
                                                    },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = TakeoverGold,
                                                        unfocusedBorderColor = TakeoverBorder,
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White,
                                                    ),
                                                )
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Checkbox(
                                                checked = rememberFlags[fieldLabel] ?: true,
                                                onCheckedChange = { rememberFlags[fieldLabel] = it },
                                                colors = CheckboxDefaults.colors(checkedColor = TakeoverGold),
                                            )
                                            Text(
                                                text = "Remember this answer in ApplicantAnswers Vault",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = TakeoverDim,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, TakeoverGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                runCatching {
                                    val cm = CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(this, true)
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): WebResourceResponse? {
                                        val u = request?.url ?: return null
                                        return assetLoader.shouldInterceptRequest(u)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        runCatching { CookieManager.getInstance().flush() }
                                        val current = url.orEmpty()
                                        if (current.isNotBlank()) {
                                            liveUrl = current
                                        }
                                        // Phase 4.2: Detect ATS confirmation navigation in Takeover screen
                                        if (!autoConfirmed && isConfirmationUrl(current)) {
                                            autoConfirmed = true
                                            onMarkSubmitted("Confirmed via URL: $current")
                                        } else if (!autoConfirmed && view != null) {
                                            view.evaluateJavascript(
                                                "(function(){ return (document.body && document.body.innerText) ? document.body.innerText.substring(0, 4000) : ''; })()"
                                            ) { rawText ->
                                                val bodyText = CourierWebHost.unquoteJsResult(rawText).orEmpty()
                                                if (!autoConfirmed && isConfirmationText(bodyText)) {
                                                    autoConfirmed = true
                                                    onMarkSubmitted("Confirmed via portal confirmation text")
                                                }
                                            }
                                        }
                                    }
                                }
                                if (initialUrl.isNotBlank()) {
                                    loadUrl(initialUrl)
                                }
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val entered = fieldInputs.filterValues { it.isNotBlank() }
                        val remembered = entered.filterKeys { rememberFlags[it] == true }
                        onResumeTask(liveUrl, entered, remembered)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TakeoverGold),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = "▶ Resume Courier",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }

                Button(
                    onClick = { onMarkSubmitted("Confirmed manually by user ('I submitted it')") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = "✓ I submitted it",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onSnoozeTask,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, TakeoverDim),
                ) {
                    Text("Snooze", color = TakeoverDim, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onCancelTask,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, Color(0xFFE57373)),
                ) {
                    Text("Cancel", color = Color(0xFFE57373), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(2.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, TakeoverDim),
                ) {
                    Text("Close", color = TakeoverDim, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
    )
}

internal fun parseBlockedFieldsJson(rawJson: String): List<String> {
    if (rawJson.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(rawJson)
        (0 until arr.length()).mapNotNull { i ->
            arr.optString(i)?.trim()?.takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}

internal fun encodeBlockedFieldsJson(fields: List<String>): String {
    val arr = JSONArray()
    fields.filter { it.isNotBlank() }.distinct().forEach { arr.put(it) }
    return arr.toString()
}

internal fun isConfirmationUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.endsWith("/thanks") ||
        lower.contains("/thanks?") ||
        lower.contains("/confirmation") ||
        lower.contains("application-submitted") ||
        lower.contains("thank-you") ||
        lower.contains("thank_you")
}

internal fun isConfirmationText(text: String): Boolean {
    val lower = text.lowercase()
    return Regex(
        "thank you for applying|application (has been )?(submitted|received)|" +
            "we('ve| have) received your application|thanks for applying"
    ).containsMatchIn(lower)
}
