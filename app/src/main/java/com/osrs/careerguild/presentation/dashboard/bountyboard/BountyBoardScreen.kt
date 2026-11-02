package com.osrs.careerguild.presentation.dashboard.bountyboard

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osrs.careerguild.core.network.LinkValidator
import com.osrs.careerguild.core.profile.UserProfileManager
import kotlinx.coroutines.launch

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
    var searchQuery by rememberSaveable { mutableStateOf(uiState.searchQuery) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val profileManager = remember { UserProfileManager.getInstance(context) }
    val userProfile by profileManager.profile.collectAsState()

    LaunchedEffect(userProfile.location) {
        val extractedZip = userProfile.location.filter { it.isDigit() }
        if (extractedZip.isNotBlank() && uiState.zipCode.isBlank()) {
            viewModel.applyZipCode(extractedZip)
        }
    }

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
                .padding(top = 4.dp, start = 12.dp, end = 12.dp, bottom = 4.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        ) {
                            Text(
                                text  = "← 3D Hub",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFFD700),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text  = "📋 Bounty Board",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFFD700),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text  = "Live Tech Radar (${uiState.jobs.size} verified quests)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9E8A6A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Live Fetching vs. Offline Cached Data Badge + Refresh Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val isLiveActive = uiState.isScraping || uiState.isScoring
                    val isLiveSynced = uiState.dataSourceMode == "LIVE_SYNCED"
                    val badgeColor = when {
                        isLiveActive -> Color(0xFF00E5FF)
                        isLiveSynced -> Color(0xFF4CAF50)
                        else         -> Color(0xFFFFB300)
                    }
                    val badgeLabel = when {
                        isLiveActive -> "⚡ Live Fetching"
                        isLiveSynced -> "✓ Live Feed Synced"
                        else         -> "📦 Offline Cached Data"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.8.dp, badgeColor),
                        modifier = Modifier.clickable(enabled = !isLiveActive) { viewModel.refresh() },
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
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
        }

        // --- Active Scouting / Live Progress Banner (Pulsing radar indicator + Live vs. Cached telemetry) ---
        val pulseTransition = rememberInfiniteTransition(label = "ScoutingPulse")
        val pulseAlpha by pulseTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ScoutingPulseAlpha",
        )
        Surface(
            color = if (uiState.isScraping || uiState.isScoring) Color(0xFF10252E) else Color(0xFF1A140F),
            border = BorderStroke(
                0.8.dp,
                if (uiState.isScraping || uiState.isScoring) Color(0xFF00E5FF).copy(alpha = pulseAlpha) else Color(0xFF3D2B1F),
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val isBusy = uiState.isScraping || uiState.isScoring
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(
                                color = when {
                                    isBusy -> Color(0xFF00E5FF).copy(alpha = pulseAlpha)
                                    uiState.dataSourceMode == "LIVE_SYNCED" -> Color(0xFF4CAF50)
                                    else -> Color(0xFFFFB300)
                                },
                                shape = CircleShape,
                            )
                    )
                    val statusHeadline = when {
                        uiState.isScraping -> "⚡ LIVE FETCHING: Querying Greenhouse, Lever, Ashby, Remotive & Arbeitnow…"
                        uiState.scoringProgress != null -> {
                            val p = uiState.scoringProgress!!
                            val pct = ((p.current.toFloat() / p.total.coerceAtLeast(1).toFloat()) * 100).toInt()
                            "🔮 LIVE SCORING ($pct% · ${p.current}/${p.total}): ${p.currentJobTitle.take(36)}"
                        }
                        else -> uiState.lastSyncMessage
                    }
                    Text(
                        text  = statusHeadline,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = if (isBusy) Color(0xFF00E5FF) else Color(0xFFE5D9B6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                AnimatedVisibility(
                    visible = uiState.isScraping || uiState.isScoring,
                    enter   = fadeIn(),
                    exit    = fadeOut(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (uiState.scoringProgress != null) {
                            val p = uiState.scoringProgress!!
                            LinearProgressIndicator(
                                progress = { (p.current.toFloat() / p.total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color    = Color(0xFF00E5FF),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color    = Color(0xFF00E5FF),
                            )
                        }
                    }
                }
            }
        }

        // --- Static Search Bar (outside LazyColumn so focus & keyboard never drop on filter updates) ---
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = { newQuery ->
                searchQuery = newQuery
                viewModel.search(newQuery)
            },
            placeholder   = { Text("Search jobs or companies…") },
            leadingIcon   = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            singleLine     = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
        )

        // --- Main content area (Scroll-friendly to maximize vertical space for quest cards) ---
        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = MaterialTheme.colorScheme.primary,
                    )
                }

                else -> {
                    LazyColumn(
                        contentPadding      = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 1. Search Parameters: Zip code & radius controls (scrolls off)
                        item(key = "search_params_header") {
                            SearchParametersSection(
                                uiState   = uiState,
                                viewModel = viewModel,
                            )
                        }

                        if (uiState.jobs.isEmpty() && !uiState.isScraping) {
                            item(key = "empty_bounty_board") {
                                EmptyBountyBoard(onRefresh = viewModel::refresh)
                            }
                        } else {
                            // 2. Header stats & Algorithmic Sort Toggle (Task 3)
                            item(key = "bounty_board_header") {
                                BountyBoardHeader(
                                    totalJobs    = uiState.jobs.size,
                                    topScore     = uiState.jobs.firstOrNull()?.score ?: 0,
                                    sortOption   = uiState.sortOption,
                                    onSortChange = viewModel::updateSortOption,
                                )
                            }

                            // 3. Job listing cards
                            items(
                                items = uiState.jobs,
                                key   = { it.id },
                            ) { job ->
                            JobListingCard(
                                job              = job,
                                onApply          = viewModel::markApplied,
                                onBlock          = viewModel::blockCompany,
                                onOpenAts        = { url ->
                                    scope.launch {
                                        runCatching {
                                            val directUrl = url.ifBlank { job.applyUrl.ifBlank { job.atsUrl } }
                                            val validUrl = LinkValidator.validateAndResolveLaunchUrl(
                                                company = job.company,
                                                title = job.title,
                                                rawUrl = directUrl,
                                                linkStatus = job.linkStatus,
                                            )
                                            if (validUrl.isNotBlank()) {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } else {
                                                android.widget.Toast.makeText(context, "Direct ATS posting is no longer active.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }.onFailure {
                                            android.widget.Toast.makeText(context, "Could not open ATS posting: $url", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onForgeScroll    = onForgeScroll,
                                onQueueAutoApply = onQueueAutoApply,
                                userZipCode      = uiState.zipCode.ifBlank { "11581" },
                                activeSkillNames = uiState.activeSkillNames,
                            )
                        }
                        }

                        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(80.dp)) }
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
private fun BountyBoardHeader(
    totalJobs    : Int,
    topScore     : Int,
    sortOption   : String = "Best Match",
    onSortChange : (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
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

        // Algorithmic Sorting Toggle Header: Latest, Closest, Best Match
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sort by:",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFFFD700),
            )
            val sortOptions = listOf("Latest", "Closest", "Best Match")
            sortOptions.forEach { option ->
                val isSelected = sortOption.equals(option, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) Color(0xFFFFD700) else Color(0xFF1F1610),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFFFFD700) else Color(0xFF5A4632),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSortChange(option) },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            ),
                            color = if (isSelected) Color(0xFF1A140F) else Color(0xFFE5D9B6),
                            maxLines = 1,
                        )
                    }
                }
            }
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

@Composable
private fun SearchParametersSection(
    uiState: BountyBoardUiState,
    viewModel: BountyBoardViewModel,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var zipInput by remember(uiState.zipCode) { mutableStateOf(uiState.zipCode) }
    val keyboard = LocalSoftwareKeyboardController.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF2B1D14),
        border = BorderStroke(1.dp, Color(0xFF3D2B1F)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp),
                    )
                    val zipDisplay = if (uiState.zipCode.isNotBlank()) "ZIP: ${uiState.zipCode}" else "All Locations"
                    Text(
                        text = "$zipDisplay · Radius: ${uiState.searchRadius} · Mode: ${uiState.workMode}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFFD700),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1B3B22),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                ) {
                    Text(
                        text = "✓ Live Filtered",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFF81C784),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = zipInput,
                        onValueChange = { zipInput = it },
                        label = { Text("Zip Code", fontSize = 10.sp) },
                        placeholder = { Text("11581", fontSize = 10.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboard?.hide()
                                viewModel.applyZipCode(zipInput)
                            },
                            onDone = {
                                keyboard?.hide()
                                viewModel.applyZipCode(zipInput)
                            },
                        ),
                        modifier = Modifier.width(105.dp),
                    )
                    Button(
                        onClick = {
                            keyboard?.hide()
                            viewModel.applyZipCode(zipInput)
                        },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                    ) {
                        Text(
                            text = "Apply",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Search Radius:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9E8A6A),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(listOf("10 mi", "25 mi", "50 mi", "100 mi", "Remote")) { rad ->
                                val selected = uiState.searchRadius == rad
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (selected) Color(0xFFFFD700) else Color(0xFF3D2B1F),
                                    border = BorderStroke(1.dp, if (selected) Color(0xFFFFD700) else Color(0xFF1A0F08)),
                                    modifier = Modifier.clickable { viewModel.updateSearchRadius(rad) },
                                ) {
                                    Text(
                                        text = rad,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (selected) Color.Black else Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Work Mode:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9E8A6A),
                    )
                    listOf("All", "Local", "Hybrid", "Remote").forEach { mode ->
                        val selected = uiState.workMode == mode
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (selected) Color(0xFF00E5FF) else Color(0xFF3D2B1F),
                            border = BorderStroke(1.dp, if (selected) Color(0xFF00E5FF) else Color(0xFF1A0F08)),
                            modifier = Modifier.clickable { viewModel.updateWorkMode(mode) },
                        ) {
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
