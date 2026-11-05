package com.osrs.careerguild.presentation.dashboard.courierguild

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osrs.careerguild.core.forge.ApplicantAnswerPresets
import com.osrs.careerguild.data.local.entity.ApplicantAnswerEntity

private val OsrsGold       = Color(0xFFFFD700)
private val OsrsStone      = Color(0xFF3D2B1F)
private val OsrsDarkStone  = Color(0xFF2B1D14)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsTextDim    = Color(0xFF9E8A6A)

/**
 * Task 2: Minimal applicant answers editor dialog.
 * Presets start EMPTY — no legal or demographic defaults are ever injected.
 */
@Composable
fun ApplicantAnswersDialog(
    savedAnswers: List<ApplicantAnswerEntity>,
    onSaveAnswers: (Map<String, String>) -> Unit,
    onDeleteAnswer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val answerMap = remember(savedAnswers) {
        mutableStateMapOf<String, String>().apply {
            savedAnswers.forEach { put(it.questionPattern, it.answer) }
        }
    }

    var customPattern by remember { mutableStateOf("") }
    var customAnswer by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OsrsDarkStone,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    tint = OsrsGold,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Applicant Answers Store (Forge Vault)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = OsrsGold,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1F1610),
                    border = BorderStroke(1.dp, OsrsDarkBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "🔒 Presets start EMPTY. Legal, authorization, and demographic answers are strictly omitted unless you explicitly set them here.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.padding(8.dp),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            text = "PRESET QUESTIONS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = OsrsTextDim,
                        )
                    }

                    items(ApplicantAnswerPresets.PRESETS) { preset ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OsrsStone, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = answerMap[preset.pattern] ?: "",
                                onValueChange = { answerMap[preset.pattern] = it },
                                placeholder = {
                                    Text(
                                        text = preset.hint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OsrsTextDim,
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = OsrsGold,
                                    unfocusedBorderColor = OsrsDarkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "CUSTOM QUESTION PATTERNS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = OsrsTextDim,
                        )
                    }

                    // Existing custom entries
                    val presetPatterns = ApplicantAnswerPresets.PRESETS.map { it.pattern }.toSet()
                    val customEntries = answerMap.filter { it.key !in presetPatterns }

                    items(customEntries.keys.toList()) { pat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OsrsStone, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pat,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = OsrsGold,
                                )
                                Text(
                                    text = answerMap[pat].orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                )
                            }
                            IconButton(onClick = {
                                answerMap.remove(pat)
                                onDeleteAnswer(pat)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Answer",
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF22160E), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Add New Custom Pattern",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = OsrsGold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = customPattern,
                                onValueChange = { customPattern = it },
                                placeholder = { Text("Question pattern e.g. 'years_of_experience'", color = OsrsTextDim, style = MaterialTheme.typography.bodySmall) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = OsrsGold,
                                    unfocusedBorderColor = OsrsDarkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = customAnswer,
                                    onValueChange = { customAnswer = it },
                                    placeholder = { Text("Answer", color = OsrsTextDim, style = MaterialTheme.typography.bodySmall) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = OsrsGold,
                                        unfocusedBorderColor = OsrsDarkBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                    ),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        if (customPattern.isNotBlank() && customAnswer.isNotBlank()) {
                                            answerMap[customPattern.trim()] = customAnswer.trim()
                                            customPattern = ""
                                            customAnswer = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OsrsGold),
                                    shape = RoundedCornerShape(4.dp),
                                    enabled = customPattern.isNotBlank() && customAnswer.isNotBlank(),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(16.dp))
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
                    onSaveAnswers(answerMap.toMap())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = OsrsGold),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text("Save Answers", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, OsrsDarkBorder),
            ) {
                Text("Cancel", color = OsrsTextDim)
            }
        }
    )
}
