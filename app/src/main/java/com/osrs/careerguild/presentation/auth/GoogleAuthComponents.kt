package com.osrs.careerguild.presentation.auth

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osrs.careerguild.core.auth.GoogleAuthManager
import com.osrs.careerguild.core.auth.GoogleAuthState
import com.osrs.careerguild.presentation.common.InfoTooltipTrigger

// DBZ x OSRS Theme Tokens
private val DbzGold       = Color(0xFFFFD700) // Super Saiyan Aura
private val DbzCyan       = Color(0xFF00E5FF) // Kamehameha / Ki Energy
private val DbzOrange     = Color(0xFFFF6D00) // Dragon Ball / Turtle Gi
private val OsrsStone     = Color(0xFF3D2B1F) // Retro RuneScape Stone
private val OsrsDarkStone = Color(0xFF2B1D14) // Toriyama Dark Inset
private val OsrsDarkBorder= Color(0xFF1A0F08)
private val OsrsTextDim   = Color(0xFF9E8A6A)

/**
 * Google OAuth2 Login & Inbox Synchronization Status Bar.
 *
 * Implements Section 2 constraints:
 *  - Google SSO connection with read-only Gmail scoping.
 *  - Displays authenticated user state and inbox scanning status.
 *  - Styled with DBZ Super Saiyan gold and OSRS retro stone aesthetics.
 */
@Composable
fun GoogleAuthBadge(
    authManager: GoogleAuthManager = GoogleAuthManager.getInstance(LocalContext.current),
    modifier: Modifier = Modifier,
) {
    val authState by authManager.authState.collectAsState()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = OsrsDarkStone.copy(alpha = 0.94f),
        border = BorderStroke(1.5.dp, DbzGold),
        modifier = modifier
            .border(1.dp, OsrsDarkBorder, RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val state = authState) {
                is GoogleAuthState.Authenticated -> {
                    // Dragon Ball glowing status dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(DbzCyan, CircleShape)
                            .border(1.dp, DbzGold, CircleShape),
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "✓ Google SSO Connected",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = DbzGold,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            InfoTooltipTrigger(
                                title = "Google SSO & Inbox Sync",
                                explanation = "Authenticated as ${state.user.email}. Read-only Gmail access scans incoming emails from greenhouse.io, lever.co, and ashbyhq.com to automatically update your Courier Guild application stages (Queued, In-Transit, Submitted, Interview).",
                            )
                        }
                        Text(
                            text = "${state.user.email} · Inbox Sync Active",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = DbzCyan,
                        )
                    }
                }

                is GoogleAuthState.Authenticating -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = DbzGold,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Authenticating OAuth2...",
                        style = MaterialTheme.typography.labelSmall,
                        color = DbzGold,
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(DbzOrange, CircleShape),
                    )
                    Text(
                        text = "⚡ Connect Google SSO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = DbzGold,
                        modifier = Modifier.clickable {
                            authManager.signInWithGoogle(
                                email = "savizwork@gmail.com",
                                displayName = "Savazek",
                            )
                        },
                    )
                    InfoTooltipTrigger(
                        title = "OAuth2 Scoping",
                        explanation = "Connect your Google account to enable automated application dispatch notifications to savizwork@gmail.com and active Courier Guild interview detection.",
                    )
                }
            }
        }
    }
}
