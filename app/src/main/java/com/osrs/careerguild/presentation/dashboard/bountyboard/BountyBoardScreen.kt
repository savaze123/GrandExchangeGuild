package com.osrs.careerguild.presentation.dashboard.bountyboard

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Bounty Board screen — the full right-pane feature for [UI_SPLIT_SCAFFOLD]
 * when the user taps the Bounty Board building in [UI_3D_GUILD_HUB].
 *
 * Node: UI_SPLIT_SCAFFOLD (Bounty Board detail pane)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BountyBoardScreen(
    viewModel       : BountyBoardViewModel,
    isSinglePane    : Boolean = false,
    onNavigateBack  : () -> Unit = {},
    onForgeScroll   : (com.osrs.careerguild.domain.model.JobListing) -> Unit = {},
    onQueueAutoApply: (com.osrs.careerguild.domain.model.JobListing) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Show block confirmation in Snackbar
    LaunchedEffect(uiState.blockedCompany) {
        uiState.blockedCompany?.let { company ->
            snackbarHostState.showSnackbar("🚫 $company blocked from future results")
            viewModel.clearBlockedConfirmation()
        }
    }

    // Show errors in Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar("⚠ $error")
            viewModel.dismissError()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- Lowered OSRS Header with visible borders ---
        Surface(
            shape  = RoundedCornerShape(8.dp),
            color  = Color(0xFF2B1D14).copy(alpha = 0.95f),
            border = BorderStroke(2.dp, Color(0xFFFFD700)),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp, bottom = 4.dp)
                .border(1.dp, Color(0xFF1A0F08), RoundedCornerShape(8.dp)),
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
                        androidx.compose.material3.OutlinedButton(
                            onClick = onNavigateBack,
                            shape   = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            border  = BorderStroke(1.dp, Color(0xFFFFD700)),
                            colors  = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF3D2B1F),
                            ),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(
                                text  = "← Back to Guild",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFFD700),
                            )
                        }
                    }
                    Column {
                        Text(
                            text  = "📋 Bounty Board",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFFD700),
                        )
                        Text(
                            text  = "Live Tech Radar & Trajectory Scoring",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9E8A6A),
                        )
                    }
                }

                IconButton(
                    onClick  = { viewModel.refresh() },
                    enabled  = !uiState.isScraping && !uiState.isScoring,
                ) {
                    Icon(
                        imageVector        = Icons.Default.Refresh,
                        contentDescription = "Refresh job listings",
                        tint               = Color(0xFFFFD700),
                    )
                }
            }
        }

        // --- Search bar ---
        OutlinedTextField(
            value         = uiState.searchQuery,
            onValueChange = viewModel::search,
            placeholder   = { Text("Search jobs or companies…") },
            leadingIcon   = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            singleLine     = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        // --- Scraping / Scoring progress bar ---
        AnimatedVisibility(
            visible = uiState.isScraping || uiState.isScoring,
            enter   = fadeIn(),
            exit    = fadeOut(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = uiState.scoringProgress?.let { p ->
                        p.current.toFloat() / p.total.toFloat()
                    } ?: 0f,
                    color    = MaterialTheme.colorScheme.primary,
                )
                val label = when {
                    uiState.isScraping -> "⚔ Scouting job boards…"
                    uiState.scoringProgress != null -> {
                        val p = uiState.scoringProgress!!
                        "🔮 Scoring ${p.current}/${p.total}: ${p.currentJobTitle.take(30)}…"
                    }
                    else -> "Processing…"
                }
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }

        // --- Job list ---
        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = MaterialTheme.colorScheme.primary,
                    )
                }

                uiState.jobs.isEmpty() && !uiState.isScraping -> {
                    EmptyBountyBoard(onRefresh = viewModel::refresh)
                }

                else -> {
                    LazyColumn(
                        contentPadding      = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Header stats
                        item {
                            BountyBoardHeader(
                                totalJobs   = uiState.jobs.size,
                                topScore    = uiState.jobs.firstOrNull()?.score ?: 0,
                            )
                        }

                        items(
                            items = uiState.jobs,
                            key   = { it.id },
                        ) { job ->
                            JobListingCard(
                                job              = job,
                                onApply          = viewModel::markApplied,
                                onBlock          = viewModel::blockCompany,
                                onOpenAts        = { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                onForgeScroll    = onForgeScroll,
                                onQueueAutoApply = onQueueAutoApply,
                            )
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }

            // Snackbar host
            SnackbarHost(
                hostState = snackbarHostState,
                modifier  = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun BountyBoardHeader(totalJobs: Int, topScore: Int) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text  = "$totalJobs quests available",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (topScore > 0) {
            Text(
                text  = "Top score: $topScore",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyBountyBoard(onRefresh: () -> Unit) {
    Column(
        modifier              = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center,
    ) {
        Text(
            text  = "📜",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text  = "No quests posted yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text  = "Hit refresh to scout the job boards",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scout Now")
        }
    }
}
