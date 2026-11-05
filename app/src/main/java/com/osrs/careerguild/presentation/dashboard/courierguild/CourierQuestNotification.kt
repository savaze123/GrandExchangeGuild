package com.osrs.careerguild.presentation.dashboard.courierguild

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A paused CAPTCHA or human-in-the-loop quest raised by the Courier (instruction.md §5, §6). */
data class CourierCaptchaQuest(
    val company: String,
    val title: String,
    val kind: String,
    val awaitingHuman: Boolean = false,
    val workerSlotIndex: Int = 0,
    val appId: String = "",
)

private val OrbCore = Color(0xFFFFB300)
private val OrbEdge = Color(0xFFFF6D00)
private val OrbHighlight = Color(0xFFFFF3C4)

/**
 * Bright, glowing orange orb (Dragon Ball style) with a "!" and a rounded "Notifications"
 * badge underneath. Tapping it opens [CourierCaptchaQuestModal].
 */
@Composable
fun CourierQuestOrb(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "questOrb")
    val glow by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Courier notification: action needed" },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            Canvas(modifier = Modifier.size(64.dp)) {
                val c = center
                val r = size.minDimension / 2f
                // Outer glow halo
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(OrbCore.copy(alpha = 0.75f * glow), OrbEdge.copy(alpha = 0.25f * glow), Color.Transparent),
                        center = c, radius = r,
                    ),
                    radius = r, center = c,
                )
                // Sphere body
                val body = r * 0.68f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(OrbHighlight, OrbCore, OrbEdge),
                        center = Offset(c.x - body * 0.3f, c.y - body * 0.35f),
                        radius = body * 1.45f,
                    ),
                    radius = body, center = c,
                )
                // Specular glint
                drawOval(
                    color = Color.White.copy(alpha = 0.75f),
                    topLeft = Offset(c.x - body * 0.55f, c.y - body * 0.7f),
                    size = Size(body * 0.55f, body * 0.32f),
                )
            }
            Text(
                text = "!",
                color = Color(0xFFB71C1C),
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF2B1D14),
            border = BorderStroke(1.dp, OrbCore),
        ) {
            Text(
                text = "Notifications",
                color = OrbCore,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** Modal shown when the orb is tapped: Complete CAPTCHA / Leave it / Snooze. */
@Composable
fun CourierCaptchaQuestModal(
    quest: CourierCaptchaQuest,
    onCompleteCaptcha: () -> Unit,
    onLeaveIt: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2B1D14),
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = "⚠ ${quest.company} · ${quest.kind}",
                color = OrbCore,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This agent needs you to complete this action/CAPTCHA.",
                    color = Color(0xFFF4EDE2),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onCompleteCaptcha,
                    colors = ButtonDefaults.buttonColors(containerColor = OrbCore),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Complete", color = Color.Black, fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onLeaveIt,
                    border = BorderStroke(1.dp, Color(0xFFE57373)),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Leave it / Cancel", color = Color(0xFFE57373)) }
                OutlinedButton(
                    onClick = onSnooze,
                    border = BorderStroke(1.dp, Color(0xFF9E8A6A)),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Snooze", color = Color(0xFF9E8A6A)) }
                Text(
                    text = "Complete resumes the agent after human input · Leave it / Cancel aborts this worker's application · Snooze moves it to the back of the queue.",
                    color = Color(0xFF9E8A6A),
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                )
            }
        },
        confirmButton = {},
    )
}
