package com.osrs.careerguild.presentation.dashboard.scribetower

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.osrs.careerguild.core.resume.BaseResume
import com.osrs.careerguild.core.resume.ResumeExperience

private val OsrsGold = Color(0xFFFFD700)
private val OsrsStone = Color(0xFF3D2B1F)
private val OsrsDarkStone = Color(0xFF2B1D14)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsCyan = Color(0xFF00E5FF)

/**
 * Task 1 Review Step Dialog.
 * Shows extracted raw text side-by-side or tabbed next to the parsed editable [BaseResume].
 * Only the user's confirmed version becomes ground truth in SQLCipher Room storage.
 */
@Composable
fun ResumeReviewDialog(
    rawText: String,
    initialResume: BaseResume,
    droppedItems: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (BaseResume) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(1) } // 0 = Raw Text, 1 = Parsed Ground Truth
    var candidateSummary by remember { mutableStateOf(initialResume.summary ?: "") }
    var candidateSkills by remember { mutableStateOf(initialResume.skills.joinToString(", ")) }
    var editableExperience by remember { mutableStateOf(initialResume.experience) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.90f)
            .border(2.dp, OsrsGold, RoundedCornerShape(12.dp)),
        containerColor = OsrsDarkStone,
        shape = RoundedCornerShape(12.dp),
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "📜 Review & Confirm Ground Truth",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = OsrsGold,
                    )
                }
                Text(
                    text = "Verify transcription against raw extracted text before committing to encrypted vault.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                if (droppedItems.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF3E2723),
                        border = BorderStroke(1.dp, Color(0xFFFF9800)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                            Text(
                                text = "Fact-check check dropped ungrounded items: ${droppedItems.joinToString("; ")}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color(0xFFFFCC80),
                            )
                        }
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = OsrsStone,
                    contentColor = OsrsGold,
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("1. Raw Extracted Text", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("2. Structured Ground Truth", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        // Raw extracted text viewer
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E1E1E),
                            border = BorderStroke(1.dp, Color.Gray),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = rawText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                    ),
                                    color = Color(0xFFE0E0E0),
                                )
                            }
                        }
                    } else {
                        // Editable structured resume fields
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = candidateSummary,
                                onValueChange = { candidateSummary = it },
                                label = { Text("Professional Summary (Verbatim)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = OsrsGold,
                                    unfocusedBorderColor = OsrsDarkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                            )

                            OutlinedTextField(
                                value = candidateSkills,
                                onValueChange = { candidateSkills = it },
                                label = { Text("Technical Skills (Comma separated)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = OsrsGold,
                                    unfocusedBorderColor = OsrsDarkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                            )

                            Text(
                                text = "Verified Work Experience (${editableExperience.size} entries):",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = OsrsCyan,
                            )

                            editableExperience.forEachIndexed { idx, exp ->
                                Card(
                                    shape = RoundedCornerShape(6.dp),
                                    colors = CardDefaults.cardColors(containerColor = OsrsStone),
                                    border = BorderStroke(1.dp, OsrsGold.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "• ${exp.employer}  |  ${exp.title}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = OsrsGold,
                                        )
                                        Text(
                                            text = "${exp.startDate ?: ""} – ${exp.endDate ?: (if (exp.isCurrent) "Present" else "")} · ${exp.location ?: ""}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = Color.LightGray,
                                        )
                                        exp.bullets.forEach { b ->
                                            Text(
                                                text = "• $b",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                                                color = Color(0xFFDDDDDD),
                                                modifier = Modifier.padding(start = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedSkills = candidateSkills.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val confirmed = initialResume.copy(
                        summary = candidateSummary.trim().takeIf { it.isNotBlank() },
                        skills = if (updatedSkills.isNotEmpty()) updatedSkills else initialResume.skills,
                        experience = editableExperience,
                    )
                    onConfirm(confirmed)
                },
                colors = ButtonDefaults.buttonColors(containerColor = OsrsGold),
                shape = RoundedCornerShape(6.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Confirm Ground Truth", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color.Gray),
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel", color = Color.LightGray, fontSize = 11.sp)
            }
        },
    )
}
