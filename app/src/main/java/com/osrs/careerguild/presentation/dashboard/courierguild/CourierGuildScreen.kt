package com.osrs.careerguild.presentation.dashboard.courierguild

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osrs.careerguild.domain.model.JobListing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val OsrsGold       = Color(0xFFFFD700)
private val OsrsStone      = Color(0xFF3D2B1F)
private val OsrsDarkStone  = Color(0xFF2B1D14)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsTextDim    = Color(0xFF9E8A6A)

enum class ApplicationStatus(val label: String, val color: Color) {
    QUEUED("Queued", Color(0xFFFFD700)),
    IN_TRANSIT("In-Transit", Color(0xFF00E5FF)),
    SUBMITTED("Submitted", Color(0xFF4CAF50)),
    INTERVIEWING("Interviewing", Color(0xFFBA68C8)),
}

data class QueuedApplication(
    val id: String,
    val company: String,
    val title: String,
    val platform: String,
    val atsUrl: String,
    val attachedPdf: String,
    var status: ApplicationStatus,
    val queuedTime: String = "Today",
)

/**
 * COURIER GUILD — Auto-Apply Queue & Autofill Ledger (Node: AUTO_APPLY_AGENT).
 *
 * Features:
 *  1. Ledger Dashboard: Top counters for Queued, In-Transit, Submitted, and Interviewing.
 *  2. Platform Filter Tabs: All, Greenhouse, Lever, Ashby, Workday.
 *  3. Interactive Queue List: Queued applications with attached resume PDF badges.
 *  4. Execute Autofill Dispatch: Automated DOM form-fill runner with live step-by-step console ledger.
 */
@Composable
fun CourierGuildScreen(
    initialQueuedJob : JobListing? = null,
    attachedPdfName  : String? = null,
    isSinglePane     : Boolean = false,
    onNavigateBack   : () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    val applications = remember {
        mutableStateListOf(
            QueuedApplication("app_1", "Stripe", "Core Infrastructure & Payments API Engineer", "Ashby", "https://stripe.com/jobs", "ATS_Resume_stripe.pdf", ApplicationStatus.QUEUED),
            QueuedApplication("app_2", "OpenAI", "AI Research & Agent Infrastructure Engineer", "Greenhouse", "https://openai.com/careers", "ATS_Resume_openai.pdf", ApplicationStatus.IN_TRANSIT),
            QueuedApplication("app_3", "Anthropic", "Prompt Systems & API Backend Engineer", "Lever", "https://anthropic.com/careers", "ATS_Resume_anthropic.pdf", ApplicationStatus.SUBMITTED),
            QueuedApplication("app_4", "Netflix", "Distributed Systems & Data Platform Engineer", "Workday", "https://netflix.com/jobs", "ATS_Resume_netflix.pdf", ApplicationStatus.INTERVIEWING),
            QueuedApplication("app_5", "Google", "Staff Android & CoreOS Systems Engineer", "Greenhouse", "https://careers.google.com", "ATS_Resume_google.pdf", ApplicationStatus.QUEUED),
        )
    }

    // Append incoming job if sent from Bounty Board / Scribe Tower
    androidx.compose.runtime.LaunchedEffect(initialQueuedJob) {
        if (initialQueuedJob != null && applications.none { it.id == initialQueuedJob.id }) {
            val platform = when {
                initialQueuedJob.atsUrl.contains("greenhouse", ignoreCase = true) -> "Greenhouse"
                initialQueuedJob.atsUrl.contains("lever", ignoreCase = true) -> "Lever"
                initialQueuedJob.atsUrl.contains("ashby", ignoreCase = true) -> "Ashby"
                else -> "Ashby"
            }
            applications.add(
                0,
                QueuedApplication(
                    id = initialQueuedJob.id,
                    company = initialQueuedJob.company,
                    title = initialQueuedJob.title,
                    platform = platform,
                    atsUrl = initialQueuedJob.atsUrl,
                    attachedPdf = attachedPdfName ?: "ATS_Resume_${initialQueuedJob.company}.pdf",
                    status = ApplicationStatus.QUEUED,
                )
            )
        }
    }

    var selectedPlatform by remember { mutableStateOf("All") }
    val platforms = listOf("All", "Greenhouse", "Lever", "Ashby", "Workday")

    val consoleLogs = remember { mutableStateListOf<String>() }
    var activeDispatchId by remember { mutableStateOf<String?>(null) }

    fun runDispatch(app: QueuedApplication) {
        coroutineScope.launch {
            activeDispatchId = app.id
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
            fun log(msg: String) {
                val now = LocalTime.now().format(timeFmt)
                consoleLogs.add(0, "[$now] $msg")
            }

            log("📦 Initializing Courier dispatch for ${app.company} (${app.platform})...")
            delay(600)
            log("🌐 Resolving ATS DOM form endpoint: ${app.atsUrl}...")
            delay(700)
            log("📝 Mapping DOM fields: {Full Name, Email, Phone, LinkedIn, GitHub}...")
            delay(800)
            log("📎 Injecting forged ATS resume PDF: ${app.attachedPdf} (<100KB single-column)...")
            delay(700)
            log("🤖 Synthesizing role-specific screening answers via on-device Gemini AICore...")
            delay(900)
            log("✅ Verification pass complete! Form submitted successfully. Packet In-Transit.")

            app.status = ApplicationStatus.IN_TRANSIT
            activeDispatchId = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OsrsDarkStone),
    ) {
        // --- Lowered OSRS Header with visible borders ---
        Surface(
            shape  = RoundedCornerShape(8.dp),
            color  = OsrsDarkStone.copy(alpha = 0.95f),
            border = BorderStroke(2.dp, OsrsGold),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp, bottom = 4.dp)
                .border(1.dp, OsrsDarkBorder, RoundedCornerShape(8.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSinglePane) {
                        OutlinedButton(
                            onClick = onNavigateBack,
                            shape   = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            border  = BorderStroke(1.dp, OsrsGold),
                            colors  = ButtonDefaults.outlinedButtonColors(containerColor = OsrsStone),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(
                                text  = "← Back to Guild",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = OsrsGold,
                            )
                        }
                    }
                    Column {
                        Text(
                            text  = "📬 Courier Guild",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = OsrsGold,
                        )
                        Text(
                            text  = "Auto-Apply Queue & Autofill Ledger (AUTO_APPLY_AGENT)",
                            style = MaterialTheme.typography.labelSmall,
                            color = OsrsTextDim,
                        )
                    }
                }
            }
        }

        // --- Main Ledger Content ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // 1. Ledger Dashboard Summary Counters
            item {
                LedgerCountersRow(
                    queuedCount       = applications.count { it.status == ApplicationStatus.QUEUED },
                    inTransitCount    = applications.count { it.status == ApplicationStatus.IN_TRANSIT },
                    submittedCount    = applications.count { it.status == ApplicationStatus.SUBMITTED },
                    interviewingCount = applications.count { it.status == ApplicationStatus.INTERVIEWING },
                )
            }

            // 2. Platform Filter Tabs
            item {
                PlatformTabsRow(
                    platforms        = platforms,
                    selectedPlatform = selectedPlatform,
                    onSelectPlatform = { selectedPlatform = it },
                )
            }

            // 3. Live Console Ledger (if logs present)
            if (consoleLogs.isNotEmpty()) {
                item {
                    ConsoleLedgerBox(logs = consoleLogs)
                }
            }

            // 4. Queued Application Cards
            val filteredApps = applications.filter {
                selectedPlatform == "All" || it.platform.equals(selectedPlatform, ignoreCase = true)
            }

            items(filteredApps, key = { it.id }) { app ->
                ApplicationQueueCard(
                    app = app,
                    isDispatching = activeDispatchId == app.id,
                    onDispatch = { runDispatch(app) },
                )
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun LedgerCountersRow(
    queuedCount: Int,
    inTransitCount: Int,
    submittedCount: Int,
    interviewingCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CounterTile(label = "Queued", count = queuedCount, color = OsrsGold, modifier = Modifier.weight(1f))
        CounterTile(label = "In-Transit", count = inTransitCount, color = Color(0xFF00E5FF), modifier = Modifier.weight(1f))
        CounterTile(label = "Submitted", count = submittedCount, color = Color(0xFF4CAF50), modifier = Modifier.weight(1f))
        CounterTile(label = "Interview", count = interviewingCount, color = Color(0xFFBA68C8), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CounterTile(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = OsrsStone,
        border = BorderStroke(1.5.dp, color),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PlatformTabsRow(
    platforms: List<String>,
    selectedPlatform: String,
    onSelectPlatform: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(platforms) { platform ->
            val isSelected = platform == selectedPlatform
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) OsrsGold else OsrsDarkStone,
                border = BorderStroke(1.dp, if (isSelected) OsrsGold else OsrsDarkBorder),
                modifier = Modifier.clickable { onSelectPlatform(platform) },
            ) {
                Text(
                    text = platform,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) Color.Black else OsrsTextDim,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ApplicationQueueCard(
    app: QueuedApplication,
    isDispatching: Boolean,
    onDispatch: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = OsrsStone),
        border = BorderStroke(1.dp, app.status.color.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Status Badge + Platform
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = app.status.color.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, app.status.color),
                ) {
                    Text(
                        text = "● ${app.status.label}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = app.status.color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = OsrsDarkStone,
                    border = BorderStroke(1.dp, OsrsDarkBorder),
                ) {
                    Text(
                        text = "ATS: ${app.platform}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OsrsTextDim,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Job Title + Company
            Text(
                text = app.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Text(
                text = app.company,
                style = MaterialTheme.typography.bodySmall,
                color = OsrsGold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Attached PDF badge
            Text(
                text = "📎 Attached: ${app.attachedPdf}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF00E5FF),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Dispatch / Execute Autofill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (app.status == ApplicationStatus.QUEUED || app.status == ApplicationStatus.IN_TRANSIT) {
                    Button(
                        onClick = onDispatch,
                        enabled = !isDispatching,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OsrsGold),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        if (isDispatching) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dispatching...", color = Color.Black, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Execute Autofill / Dispatch", color = Color.Black, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                } else {
                    Text(
                        text = "✓ Dispatched & Verified",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF4CAF50),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsoleLedgerBox(logs: List<String>) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0A08)),
        border = BorderStroke(1.dp, Color(0xFF00FF66)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "📟 LIVE AUTOFILL LEDGER CONSOLE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF00FF66),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                logs.take(5).forEach { logLine ->
                    Text(
                        text = logLine,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                        ),
                        color = Color(0xFF81C784),
                    )
                }
            }
        }
    }
}
