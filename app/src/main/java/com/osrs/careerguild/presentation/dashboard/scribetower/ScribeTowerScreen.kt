package com.osrs.careerguild.presentation.dashboard.scribetower

import android.content.Intent
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.osrs.careerguild.core.pdf.PdfForgeResult
import com.osrs.careerguild.core.pdf.ResumeForgePdfGenerator
import com.osrs.careerguild.domain.model.GuildBuilding
import com.osrs.careerguild.domain.model.JobListing

private val OsrsGold       = Color(0xFFFFD700)
private val OsrsStone      = Color(0xFF3D2B1F)
private val OsrsDarkStone  = Color(0xFF2B1D14)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsTextDim    = Color(0xFF9E8A6A)
private val OsrsParchment  = Color(0xFFF4EDE2)

/**
 * SCRIBE TOWER — ATS Resume & Cover Letter Forge (Node: RESUME_FORGE_PDF).
 *
 * Features:
 *  1. Target Quest Selector: displays the selected Bounty Board job or allows manual quest selection.
 *  2. Skill Infusion: displays matching active skills from Skill Altar with interactive toggle chips.
 *  3. Live ATS Single-Column Parchment Preview.
 *  4. Native <100KB PDF Generator powered by Android's PdfDocument API.
 *  5. Direct dispatch action forwarding the forged resume to the Courier Guild queue.
 */
@Composable
fun ScribeTowerScreen(
    initialJob          : JobListing? = null,
    availableJobs       : List<JobListing> = emptyList(),
    activeSkills        : List<String> = listOf("Python", "Kotlin", "Gemini API", "RAG", "SQL", "Airflow", "Kafka", "Playwright"),
    isSinglePane        : Boolean = false,
    onNavigateBack      : () -> Unit = {},
    onSendToCourier     : (JobListing, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current

    var selectedJob by remember(initialJob) {
        mutableStateOf(
            initialJob ?: availableJobs.firstOrNull() ?: JobListing(
                id = "demo_ai_eng",
                title = "AI Research & Agent Infrastructure Engineer",
                company = "OpenAI",
                atsUrl = "https://openai.com/careers",
                source = "TechRadar Verified",
                score = 96,
                matchedSkills = listOf("Python", "AI Agents", "LLM Integration", "RAG"),
                isBlocked = false,
                appliedAt = null,
            )
        )
    }

    val infusedSkills = remember { mutableStateListOf<String>() }
    LaunchedEffect(selectedJob) {
        infusedSkills.clear()
        val jobMatches = selectedJob.matchedSkills
        val initialSelection = if (jobMatches.isNotEmpty()) jobMatches else activeSkills.take(5)
        infusedSkills.addAll(initialSelection)
    }

    var isForging by remember { mutableStateOf(false) }
    var forgeResult by remember { mutableStateOf<PdfForgeResult?>(null) }
    var customNotes by remember { mutableStateOf("") }

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
                            text  = "📜 Scribe Tower",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = OsrsGold,
                        )
                        Text(
                            text  = "ATS Resume & Cover Letter Forge (<100KB Native PDF)",
                            style = MaterialTheme.typography.labelSmall,
                            color = OsrsTextDim,
                        )
                    }
                }
            }
        }

        // --- Main Content Scroll ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // 1. Target Quest Banner
            item {
                TargetQuestCard(
                    job = selectedJob,
                    availableJobs = availableJobs,
                    onSelectJob = { selectedJob = it },
                )
            }

            // 2. Skill Infusion Chips
            item {
                SkillInfusionCard(
                    allSkills = (activeSkills + selectedJob.matchedSkills).distinct(),
                    infusedSkills = infusedSkills,
                    onToggleSkill = { skill ->
                        if (infusedSkills.contains(skill)) {
                            if (infusedSkills.size > 1) infusedSkills.remove(skill)
                        } else {
                            infusedSkills.add(skill)
                        }
                    },
                )
            }

            // 3. Forge Action Card
            item {
                ForgeActionCard(
                    isForging = isForging,
                    forgeResult = forgeResult,
                    onForgePdf = {
                        isForging = true
                        val res = ResumeForgePdfGenerator.forgeAtsResume(
                            context = context,
                            targetTitle = selectedJob.title,
                            targetCompany = selectedJob.company,
                            infusedSkills = infusedSkills.toList(),
                            customSummary = customNotes.takeIf { it.isNotBlank() },
                        )
                        forgeResult = res.getOrNull()
                        isForging = false
                    },
                    onViewPdf = { file ->
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(viewIntent, "Open ATS Resume"))
                    },
                    onSendToCourier = {
                        val pdfName = forgeResult?.file?.name ?: "ATS_Resume_${selectedJob.company}.pdf"
                        onSendToCourier(selectedJob, pdfName)
                    },
                )
            }

            // 4. ATS Single-Column Live Parchment Preview
            item {
                AtsParchmentPreview(
                    job = selectedJob,
                    infusedSkills = infusedSkills,
                    customSummary = customNotes,
                    onCustomSummaryChange = { customNotes = it },
                )
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun TargetQuestCard(
    job: JobListing,
    availableJobs: List<JobListing>,
    onSelectJob: (JobListing) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = OsrsStone),
        border = BorderStroke(1.5.dp, OsrsGold),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "🎯 Target Quest (Bounty)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = OsrsGold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = job.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Text(
                text = "${job.company}  ·  Match Level: ${job.score}/99",
                style = MaterialTheme.typography.bodySmall,
                color = OsrsGold,
            )
        }
    }
}

@Composable
private fun SkillInfusionCard(
    allSkills: List<String>,
    infusedSkills: List<String>,
    onToggleSkill: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = OsrsStone),
        border = BorderStroke(1.dp, OsrsGold.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "✨ Skill Infusion (Tap to Include)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = OsrsGold,
                )
                Text(
                    text = "${infusedSkills.size} infused",
                    style = MaterialTheme.typography.labelSmall,
                    color = OsrsTextDim,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(allSkills) { skill ->
                    val isInfused = infusedSkills.contains(skill)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isInfused) Color(0xFF2E5A27) else OsrsDarkStone,
                        border = BorderStroke(1.dp, if (isInfused) Color(0xFF00E5FF) else OsrsDarkBorder),
                        modifier = Modifier.clickable { onToggleSkill(skill) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isInfused) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(12.dp).padding(end = 2.dp),
                                )
                            }
                            Text(
                                text = skill,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isInfused) Color.White else OsrsTextDim,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForgeActionCard(
    isForging: Boolean,
    forgeResult: PdfForgeResult?,
    onForgePdf: () -> Unit,
    onViewPdf: (java.io.File) -> Unit,
    onSendToCourier: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = OsrsDarkStone),
        border = BorderStroke(1.5.dp, OsrsGold),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onForgePdf,
                enabled = !isForging,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OsrsGold),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isForging) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Forging PDF...", color = Color.Black, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⚔ Forge <100KB ATS PDF", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            if (forgeResult != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1B3B2B),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "✅ Forged: ${String.format("%.1f", forgeResult.fileSizeKb)} KB (Single-Column ATS Verified)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF81C784),
                        )
                        Text(
                            text = "Saved: ${forgeResult.file.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onViewPdf(forgeResult.file) },
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFF81C784)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open / Share", color = Color(0xFF81C784), style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = onSendToCourier,
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Queue in Courier Guild", color = Color.Black, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtsParchmentPreview(
    job: JobListing,
    infusedSkills: List<String>,
    customSummary: String,
    onCustomSummaryChange: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = OsrsParchment,
        border = BorderStroke(2.dp, OsrsGold),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "📜 ATS SINGLE-COLUMN PREVIEW",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF5A3D1E),
            )
            Text(
                text = "Savazek",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
            )
            Text(
                text = "Target Quest: ${job.title}  |  ${job.company}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF1E3A4A),
            )
            Text(
                text = "San Francisco, CA · savazek.dev · github.com/savazek · linkedin.com/in/savazek",
                style = MaterialTheme.typography.bodySmall,
                color = Color.DarkGray,
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "PROFESSIONAL SUMMARY",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
            )
            Text(
                text = if (customSummary.isNotBlank()) customSummary else
                    "Results-driven Software Engineer with proven expertise in building high-performance distributed systems, on-device AI integration, and robust automation pipelines. Adept at leveraging ${infusedSkills.take(4).joinToString(", ")} to solve mission-critical engineering bottlenecks and scale resilient infrastructure.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF222222),
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "INFUSED SKILLS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
            )
            Text(
                text = "• Core: " + infusedSkills.joinToString("  •  "),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFF0F3A22),
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "EXPERIENCE HIGHLIGHT",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
            )
            Text(
                text = "• Architected 120Hz Filament 3D rendering pipeline and reactive MVI Compose client on Android (Tensor G5, 16KB ELF alignment).",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF222222),
            )
            Text(
                text = "• Constructed resilient ETL streaming pipelines processing 100M+ events/day using Kafka, Airflow, and Python.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF222222),
            )
        }
    }
}
