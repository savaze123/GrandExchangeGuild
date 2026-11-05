package com.osrs.careerguild.presentation.dashboard.courierguild

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osrs.careerguild.core.forge.ApplicantAnswerPresets
import com.osrs.careerguild.core.forge.PresetInputType
import com.osrs.careerguild.data.local.entity.ApplicantAnswerEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

private val OsrsGold       = Color(0xFFFFD700)
private val OsrsStone      = Color(0xFF3D2B1F)
private val OsrsDarkStone  = Color(0xFF2B1D14)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsTextDim    = Color(0xFF9E8A6A)

/**
 * Applicant Answers Store Editor Dialog (Forge Vault).
 *
 * Strict Selection Enforcement:
 * - Authorization, relocation, sponsorship, and demographic fields are backed by canonical Enums
 *   and rendered with RadioButtons / segmented selection chips instead of free-text inputs.
 * - Earliest Start Date provides quick selection chips ("Immediately", "2 Weeks", "1 Month")
 *   plus an interactive Android DatePicker.
 * - Presets start EMPTY — no legal or demographic defaults are ever injected unless chosen by user.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApplicantAnswersDialog(
    savedAnswers: List<ApplicantAnswerEntity>,
    onSaveAnswers: (Map<String, String>) -> Unit,
    onDeleteAnswer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
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
        modifier = Modifier.fillMaxWidth(0.96f),
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
                        text = "🔒 Strict Selection Enforced: Authorization, relocation, and demographic answers are selected from exact allowed options. Presets start empty.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.padding(8.dp),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            text = "CANONICAL PRESETS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = OsrsTextDim,
                        )
                    }

                    items(ApplicantAnswerPresets.PRESETS) { preset ->
                        val currentVal = answerMap[preset.pattern].orEmpty()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OsrsStone, RoundedCornerShape(6.dp))
                                .border(1.dp, if (currentVal.isNotBlank()) OsrsGold.copy(alpha = 0.6f) else OsrsDarkBorder, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                if (currentVal.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable { answerMap.remove(preset.pattern) }
                                            .padding(start = 6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = Color(0xFFEF5350),
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Clear",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = Color(0xFFEF5350),
                                        )
                                    }
                                }
                            }

                            when (val inputType = preset.inputType) {
                                is PresetInputType.SingleChoice -> {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        inputType.options.forEach { optionText ->
                                            val isSelected = currentVal.equals(optionText, ignoreCase = true)
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isSelected) OsrsGold else Color(0xFF1F1610),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) OsrsGold else OsrsDarkBorder,
                                                ),
                                                modifier = Modifier.clickable {
                                                    answerMap[preset.pattern] = if (isSelected) "" else optionText
                                                },
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = null,
                                                        colors = RadioButtonDefaults.colors(
                                                            selectedColor = Color.Black,
                                                            unselectedColor = OsrsTextDim,
                                                        ),
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = optionText,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.sp,
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
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val isCustomDate = currentVal.isNotBlank() && inputType.quickOptions.none { it.equals(currentVal, ignoreCase = true) }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        inputType.quickOptions.forEach { optionText ->
                                            val isSelected = currentVal.equals(optionText, ignoreCase = true)
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isSelected) OsrsGold else Color(0xFF1F1610),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) OsrsGold else OsrsDarkBorder,
                                                ),
                                                modifier = Modifier.clickable {
                                                    answerMap[preset.pattern] = if (isSelected) "" else optionText
                                                },
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = null,
                                                        colors = RadioButtonDefaults.colors(
                                                            selectedColor = Color.Black,
                                                            unselectedColor = OsrsTextDim,
                                                        ),
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = optionText,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        ),
                                                        color = if (isSelected) Color.Black else Color.White,
                                                    )
                                                }
                                            }
                                        }

                                        // UI DatePicker button
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
                                                        answerMap[preset.pattern] = chosen.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                                    },
                                                    cal.get(Calendar.YEAR),
                                                    cal.get(Calendar.MONTH),
                                                    cal.get(Calendar.DAY_OF_MONTH),
                                                ).show()
                                            },
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CalendarMonth,
                                                    contentDescription = null,
                                                    tint = if (isCustomDate) Color.Black else Color(0xFF00E5FF),
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isCustomDate) currentVal else "Pick Date...",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isCustomDate) FontWeight.Bold else FontWeight.Normal,
                                                    ),
                                                    color = if (isCustomDate) Color.Black else Color(0xFF00E5FF),
                                                )
                                            }
                                        }
                                    }
                                }

                                is PresetInputType.FreeText -> {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = currentVal,
                                        onValueChange = { answerMap[preset.pattern] = it },
                                        placeholder = {
                                            Text(
                                                text = preset.hint,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
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
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "CUSTOM QUESTION PATTERNS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
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
                                .border(1.dp, OsrsDarkBorder, RoundedCornerShape(6.dp))
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
                                .border(1.dp, OsrsDarkBorder, RoundedCornerShape(6.dp))
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
                                placeholder = { Text("Pattern e.g. 'years_of_experience'", color = OsrsTextDim, fontSize = 11.sp) },
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
                                    placeholder = { Text("Answer", color = OsrsTextDim, fontSize = 11.sp) },
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
