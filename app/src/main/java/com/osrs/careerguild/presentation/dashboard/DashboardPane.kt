package com.osrs.careerguild.presentation.dashboard

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.osrs.careerguild.di.LocalAppContainer
import com.osrs.careerguild.domain.model.GuildBuilding
import com.osrs.careerguild.domain.model.JobListing
import com.osrs.careerguild.presentation.dashboard.bountyboard.BountyBoardScreen
import com.osrs.careerguild.presentation.dashboard.bountyboard.BountyBoardViewModel
import com.osrs.careerguild.presentation.dashboard.courierguild.CourierGuildScreen
import com.osrs.careerguild.presentation.dashboard.scribetower.ScribeTowerScreen
import com.osrs.careerguild.presentation.dashboard.skillaltar.SkillAltarScreen
import com.osrs.careerguild.presentation.dashboard.skillaltar.SkillAltarViewModel

private val OsrsGold       = Color(0xFFFFD700)
private val OsrsStone      = Color(0xFF3D2B1F)
private val OsrsDarkStone  = Color(0xFF2B1D14)
private val OsrsDarkBorder = Color(0xFF1A0F08)
private val OsrsTextDim    = Color(0xFF9E8A6A)

/**
 * Right pane of [GuildScaffold] (or full pane in single-screen mode).
 *
 * Routes to the active feature screen based on [selectedBuilding]:
 *  - BOUNTY_BOARD  → Live Job Radar + Trajectory Scorer
 *  - SCRIBE_TOWER  → ATS Resume & Cover Letter Forge (<100KB Native PDF)
 *  - SKILL_ALTAR   → OSRS Skill Tree / Dynamic Skill Matrix
 *  - COURIER_GUILD → Auto-Apply Queue & Autofill Ledger
 *  - null          → Bounty Board or Guild Overview
 *
 * Node: UI_SPLIT_SCAFFOLD
 */
@Composable
fun DashboardPane(
    selectedBuilding      : GuildBuilding?,
    isSinglePane          : Boolean = false,
    onNavigateBack        : () -> Unit = {},
    onNavigateToBuilding  : (GuildBuilding) -> Unit = {},
    modifier              : Modifier = Modifier,
    bountyBoardViewModel  : BountyBoardViewModel = run {
        val container = LocalAppContainer.current
        viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    container.createBountyBoardViewModel() as T
            }
        )
    },
) {
    val container = LocalAppContainer.current
    val skillAltarViewModel: SkillAltarViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                container.createSkillAltarViewModel() as T
        }
    )

    val bountyUiState by bountyBoardViewModel.uiState.collectAsState()
    val skillUiState by skillAltarViewModel.uiState.collectAsState()

    var targetJobForScribe by remember { mutableStateOf<JobListing?>(null) }
    var targetJobForCourier by remember { mutableStateOf<JobListing?>(null) }
    var attachedPdfForCourier by remember { mutableStateOf<String?>(null) }

    Box(
        modifier         = modifier.fillMaxSize().background(OsrsDarkStone),
        contentAlignment = Alignment.TopStart,
    ) {
        when (selectedBuilding) {
            GuildBuilding.BOUNTY_BOARD -> {
                BountyBoardScreen(
                    viewModel        = bountyBoardViewModel,
                    isSinglePane     = isSinglePane,
                    onNavigateBack   = onNavigateBack,
                    onForgeScroll    = { job ->
                        targetJobForScribe = job
                        onNavigateToBuilding(GuildBuilding.SCRIBE_TOWER)
                    },
                    onQueueAutoApply = { job ->
                        targetJobForCourier = job
                        onNavigateToBuilding(GuildBuilding.COURIER_GUILD)
                    },
                )
            }

            GuildBuilding.SCRIBE_TOWER -> {
                ScribeTowerScreen(
                    initialJob      = targetJobForScribe,
                    availableJobs   = bountyUiState.jobs,
                    activeSkills    = skillUiState.skills.filter { it.isActive }.map { it.name },
                    isSinglePane    = isSinglePane,
                    onNavigateBack  = onNavigateBack,
                    onSendToCourier = { job, pdfName ->
                        targetJobForCourier = job
                        attachedPdfForCourier = pdfName
                        onNavigateToBuilding(GuildBuilding.COURIER_GUILD)
                    },
                )
            }

            GuildBuilding.SKILL_ALTAR -> {
                SkillAltarScreen(
                    viewModel      = skillAltarViewModel,
                    isSinglePane   = isSinglePane,
                    onNavigateBack = onNavigateBack,
                )
            }

            GuildBuilding.COURIER_GUILD -> {
                CourierGuildScreen(
                    initialQueuedJob = targetJobForCourier,
                    attachedPdfName  = attachedPdfForCourier,
                    isSinglePane     = isSinglePane,
                    onNavigateBack   = onNavigateBack,
                )
            }

            null -> {
                if (isSinglePane) {
                    // In single pane with null selectedBuilding, user is at hub
                    Box(modifier = Modifier.fillMaxSize())
                } else {
                    // In dual-pane mode, default right pane to Bounty Board so jobs are immediately visible
                    BountyBoardScreen(
                        viewModel        = bountyBoardViewModel,
                        isSinglePane     = false,
                        onNavigateBack   = onNavigateBack,
                        onForgeScroll    = { job ->
                            targetJobForScribe = job
                            onNavigateToBuilding(GuildBuilding.SCRIBE_TOWER)
                        },
                        onQueueAutoApply = { job ->
                            targetJobForCourier = job
                            onNavigateToBuilding(GuildBuilding.COURIER_GUILD)
                        },
                    )
                }
            }
        }
    }
}
