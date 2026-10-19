package com.osrs.careerguild.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// OSRS Color Tokens
private val OsrsGold       = Color(0xFFFFD700)
private val OsrsStone      = Color(0xFF3D2B1F)
private val OsrsDarkStone  = Color(0xFF2B1D14)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsTextDim    = Color(0xFF9E8A6A)
private val OsrsCyan       = Color(0xFF00E5FF)

/**
 * Dynamic Information Tooltip Trigger:
 * Accessible info-circle trigger `(i)` mounted adjacent to pipeline metrics,
 * statuses, and navigation headers to clarify platform mechanics on tap.
 */
@Composable
fun InfoTooltipTrigger(
    title: String,
    explanation: String,
    modifier: Modifier = Modifier,
    badgeText: String = "i",
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(22.dp)
            .background(OsrsDarkStone.copy(alpha = 0.9f), CircleShape)
            .border(1.dp, OsrsGold.copy(alpha = 0.8f), CircleShape)
            .clickable { showDialog = true },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badgeText,
            color = OsrsGold,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = OsrsDarkStone,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(2.dp, OsrsGold, RoundedCornerShape(12.dp)),
            title = {
                Text(
                    text = "⚔ $title",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = OsrsGold,
                )
            },
            text = {
                Surface(
                    color = OsrsStone.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, OsrsDarkBorder),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = explanation,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            color = Color(0xFFE0D5C1),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OsrsStone),
                    border = BorderStroke(1.dp, OsrsGold),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = "Understood",
                        color = OsrsGold,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            },
        )
    }
}
