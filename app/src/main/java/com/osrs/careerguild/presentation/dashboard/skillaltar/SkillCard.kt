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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osrs.careerguild.domain.model.Skill

// ---------------------------------------------------------------------------
// OSRS palette — matches SkillAltarScreen constants
// ---------------------------------------------------------------------------
private val OsrsStone      = Color(0xFF3D2B1F)
private val OsrsBrown      = Color(0xFF5C3A1E)
private val OsrsGold       = Color(0xFFFFD700)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsTextDim    = Color(0xFF9E8A6A)

/** Max OSRS level — used for "current/max" display. */
private const val OSRS_MAX_LEVEL = 99

/**
 * OSRS-style skill tile for the Skill Altar 3-column grid.
 *
 * Visual spec:
 *  - **Active**   = bright stone background + gold (#FFD700) border (1.5dp) + full opacity text
 *  - **Inactive** = dimmed background + dark inset border (0xFF1A0F08) + reduced-alpha text
 *  - **Level badge** = "current/max" format (e.g. "72/99") in tier color
 *  - **Tap anywhere** on the tile toggles active state (multi-select allowed)
 *  - Small ✕ button for removal (bottom-right)
 *
 * Tier colours (border + badge) follow OSRS equipment tiers:
 *  99     → Dragon  (#FFD700 gold)
 *  90–98  → Rune    blue
 *  70–89  → Adamant green
 *  50–69  → Mithril purple
 *  30–49  → Steel   teal
 *  1–29   → Iron    grey
 *
 * Node: DYNAMIC_SKILL_MATRIX
 */
@Composable
fun OsrsSkillTile(
    skill    : Skill,
    onToggle : (String) -> Unit,
    onRemove : (String) -> Unit,
    modifier : Modifier = Modifier,
) {
    val tierColor = skillTierColor(skill.level)

    val borderColor by animateColorAsState(
        targetValue = if (skill.isActive) OsrsGold else OsrsDarkBorder,
        label       = "SkillTileBorder",
    )
    val bgColor = if (skill.isActive) OsrsBrown else OsrsStone
    val textAlpha = if (skill.isActive) 1f else 0.45f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = bgColor,
                shape = RoundedCornerShape(6.dp),
            )
            .border(
                width = if (skill.isActive) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable { onToggle(skill.name) }
            .padding(8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // --- Level badge: "current/max" ---
            LevelBadge(
                level     = skill.level,
                color     = if (skill.isActive) tierColor else tierColor.copy(alpha = 0.4f),
                textAlpha = textAlpha,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // --- Skill name ---
            Text(
                text      = skill.name,
                style     = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize   = 11.sp,
                ),
                color     = if (skill.isActive) OsrsGold else OsrsTextDim,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier  = Modifier.alpha(textAlpha),
            )

            Spacer(modifier = Modifier.height(2.dp))

            // --- Category label ---
            Text(
                text  = skill.category,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = OsrsTextDim.copy(alpha = if (skill.isActive) 0.9f else 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // --- Remove button (bottom-right aligned via Row) ---
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick  = { onRemove(skill.name) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Default.DeleteForever,
                        contentDescription = "Remove ${skill.name}",
                        tint               = OsrsTextDim.copy(alpha = 0.5f),
                        modifier           = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Level badge: "current/max" (e.g. "72/99")
// ---------------------------------------------------------------------------

@Composable
private fun LevelBadge(level: Int, color: Color, textAlpha: Float) {
    Box(
        modifier         = Modifier
            .size(44.dp)
            .background(
                color = color.copy(alpha = if (textAlpha >= 1f) 0.18f else 0.06f),
                shape = RoundedCornerShape(6.dp),
            )
            .border(
                width = 1.dp,
                color = color.copy(alpha = if (textAlpha >= 1f) 0.6f else 0.2f),
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text      = "$level/$OSRS_MAX_LEVEL",
            style     = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize   = 10.sp,
            ),
            color     = color.copy(alpha = textAlpha),
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// OSRS tier colour mapping
// ---------------------------------------------------------------------------

private fun skillTierColor(level: Int): Color = when {
    level >= 99 -> Color(0xFFD4AA00) // Dragon  — OSRS Gold
    level >= 90 -> Color(0xFF3A6EA5) // Rune    — Blue
    level >= 70 -> Color(0xFF4CAF50) // Adamant — Green
    level >= 50 -> Color(0xFF9C27B0) // Mithril — Purple
    level >= 30 -> Color(0xFF009688) // Steel   — Teal
    else        -> Color(0xFF9E9E9E) // Iron    — Grey
}
