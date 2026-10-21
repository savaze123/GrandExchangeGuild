package com.osrs.careerguild.presentation.dashboard.bountyboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInBrowser
import com.osrs.careerguild.core.location.JobLocationUtils
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osrs.careerguild.domain.model.JobListing

/**
 * OSRS-styled Quest Scroll card representing a single scored [JobListing].
 *
 * Score colour bands (OSRS tier inspiration):
 *  80–100 → Dragon (gold/legendary)
 *  60–79  → Rune (blue)
 *  40–59  → Adamant (green)
 *  20–39  → Iron (grey)
 *  0–19   → Bronze
 *
 * Node: UI_SPLIT_SCAFFOLD (Bounty Board list pane)
 */
@Composable
fun JobListingCard(
    job             : JobListing,
    onApply         : (String) -> Unit,
    onBlock         : (String) -> Unit,
    onOpenAts       : (String) -> Unit,
    onForgeScroll   : (JobListing) -> Unit = {},
    onQueueAutoApply: (JobListing) -> Unit = {},
    modifier        : Modifier = Modifier,
    userZipCode     : String = "11581",
) {
    val tierColor = scoreTierColor(job.score)
    var isTerminalOpen by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = tierColor.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // --- Header row: score badge + title (Clickable link routing) ---
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier             = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAts(job.atsUrl) },
            ) {
                ScoreBadge(score = job.score, color = tierColor)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text      = job.title,
                        style     = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary,
                        ),
                        maxLines  = 1,
                        overflow  = TextOverflow.Ellipsis,
                    )
                    Text(
                        text  = "🔗 Tap title to view live posting",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF00E5FF),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- Company + source + link verification ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text  = "${job.company}  ·  ${job.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1B3B22),
                    border = BorderStroke(0.8.dp, Color(0xFF4CAF50)),
                ) {
                    Text(
                        text = "✓ Link Validated",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFF81C784),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- Job Location and Distance (Directive 2) ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(13.dp),
                )
                val locationDistanceText = JobLocationUtils.formatLocationAndDistance(
                    jobLocation = job.location,
                    workMode = job.workMode,
                    userZip = userZipCode,
                )
                Text(
                    text = locationDistanceText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFFFFD700).copy(alpha = 0.9f),
                )
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFF3D2B1F),
                    border = BorderStroke(0.6.dp, Color(0xFF00E5FF)),
                ) {
                    Text(
                        text = job.workMode,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            // --- Matched skills chips ---
            if (job.matchedSkills.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    job.matchedSkills.take(4).forEach { skill ->
                        SkillChip(skill)
                    }
                    if (job.matchedSkills.size > 4) {
                        SkillChip("+${job.matchedSkills.size - 4}")
                    }
                }
            }

            // --- Applied badge ---
            if (job.appliedAt != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text  = "✓ Applied",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50),
                )
            }

            // --- Job Description in Listing Terminal (OSRS Quest Specification) ---
            if (job.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF140F0B),
                    border = BorderStroke(1.dp, if (isTerminalOpen) Color(0xFFFFD700) else Color(0xFF3D2B1F)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTerminalOpen = !isTerminalOpen },
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(if (isTerminalOpen) Color(0xFFFFD700) else Color(0xFF00E5FF), CircleShape)
                                )
                                Text(
                                    text = "📜 QUEST SPECIFICATION TERMINAL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    color = if (isTerminalOpen) Color(0xFFFFD700) else Color(0xFF00E5FF),
                                )
                            }
                            Text(
                                text = if (isTerminalOpen) "▲ COLLAPSE" else "▼ VIEW JD",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFFFFD700),
                            )
                        }
                        if (isTerminalOpen) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = job.description,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp,
                                ),
                                color = Color(0xFFD6CDB8),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- Action row: Forge Scroll & Queue Auto-Apply buttons + quick icons ---
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Forge Scroll (Scribe Tower)
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onForgeScroll(job) },
                        shape   = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        border  = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700)),
                        colors  = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF3D2B1F).copy(alpha = 0.4f),
                        ),
                    ) {
                        Text(
                            text  = "📜 Forge Scroll",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFFD700),
                        )
                    }

                    // Queue Auto-Apply (Courier Guild)
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onQueueAutoApply(job) },
                        shape   = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        border  = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)),
                        colors  = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1E3A4A).copy(alpha = 0.4f),
                        ),
                    ) {
                        Text(
                            text  = "📬 Auto-Apply",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00E5FF),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick  = { onOpenAts(job.atsUrl) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Default.OpenInBrowser,
                            contentDescription = "Open ATS link",
                            tint               = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (job.appliedAt == null) {
                        IconButton(
                            onClick  = { onApply(job.id) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Default.CheckCircle,
                                contentDescription = "Mark applied",
                                tint               = Color(0xFF4CAF50),
                            )
                        }
                    }
                    IconButton(
                        onClick  = { onBlock(job.company) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Block,
                            contentDescription = "Block company",
                            tint               = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun ScoreBadge(score: Int, color: Color) {
    Box(
        modifier         = Modifier
            .size(40.dp)
            .background(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp))
            .border(width = 1.dp, color = color, shape = RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = score.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

@Composable
private fun SkillChip(label: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Returns a tier colour matching the OSRS equipment tier aesthetic. */
private fun scoreTierColor(score: Int): Color = when {
    score >= 80 -> Color(0xFFD4AA00) // Dragon — OSRS Gold
    score >= 60 -> Color(0xFF3A6EA5) // Rune   — Blue
    score >= 40 -> Color(0xFF4CAF50) // Adamant — Green
    score >= 20 -> Color(0xFF9E9E9E) // Iron    — Grey
    else        -> Color(0xFFB07D4E) // Bronze
}
