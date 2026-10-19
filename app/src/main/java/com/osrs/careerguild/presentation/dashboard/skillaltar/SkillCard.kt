package com.osrs.careerguild.presentation.dashboard.skillaltar

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.osrs.careerguild.domain.model.Skill

/**
 * OSRS-inspired skill card for the Skill Altar grid.
 *
 * Tier colours match OSRS equipment tiers (applied to border + level badge):
 *  99       → Dragon  (Gold)
 *  90–98    → Rune    (Blue)
 *  70–89    → Adamant (Green)
 *  50–69    → Mithril (Purple)
 *  30–49    → Steel   (Teal)
 *  1–29     → Iron    (Grey)
 *
 * Active skills show a glowing primary border; inactive skills are dimmed.
 *
 * Node: DYNAMIC_SKILL_MATRIX
 */
@Composable
fun SkillCard(
    skill       : Skill,
    onToggle    : (String) -> Unit,
    onRemove    : (String) -> Unit,
    modifier    : Modifier = Modifier,
) {
    val tierColor = skillTierColor(skill.level)

    val borderColor by animateColorAsState(
        targetValue = if (skill.isActive) tierColor else tierColor.copy(alpha = 0.25f),
        label       = "SkillCardBorder",
    )
    val bgAlpha = if (skill.isActive) 0.10f else 0.03f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = tierColor.copy(alpha = bgAlpha),
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (skill.isActive) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(10.dp),
    ) {
        Column {
            // --- Level badge + skill name ---
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LevelBadge(level = skill.level, color = tierColor)

                Text(
                    text     = skill.name,
                    style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color    = if (skill.isActive) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- Category chip ---
            Text(
                text  = skill.category,
                style = MaterialTheme.typography.labelSmall,
                color = tierColor.copy(alpha = 0.8f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- Active toggle + delete ---
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked        = skill.isActive,
                        onCheckedChange = { onToggle(skill.name) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = tierColor,
                            checkedTrackColor   = tierColor.copy(alpha = 0.3f),
                        ),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text  = if (skill.isActive) "Active" else "Off",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (skill.isActive) tierColor
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }

                IconButton(
                    onClick  = { onRemove(skill.name) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Default.DeleteForever,
                        contentDescription = "Remove ${skill.name}",
                        tint               = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier           = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun LevelBadge(level: Int, color: Color) {
    Box(
        modifier         = Modifier
            .size(36.dp)
            .background(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = level.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

// ---------------------------------------------------------------------------
// OSRS tier colour mapping
// ---------------------------------------------------------------------------

private fun skillTierColor(level: Int): Color = when {
    level >= 99 -> Color(0xFFD4AA00) // Dragon — OSRS Gold
    level >= 90 -> Color(0xFF3A6EA5) // Rune   — Blue
    level >= 70 -> Color(0xFF4CAF50) // Adamant — Green
    level >= 50 -> Color(0xFF9C27B0) // Mithril — Purple
    level >= 30 -> Color(0xFF009688) // Steel   — Teal
    else        -> Color(0xFF9E9E9E) // Iron    — Grey
}
