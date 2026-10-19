package com.osrs.careerguild.presentation.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.osrs.careerguild.domain.model.GuildBuilding
import com.osrs.careerguild.presentation.dashboard.bountyboard.BountyBoardScreen
import com.osrs.careerguild.presentation.dashboard.bountyboard.BountyBoardViewModel

/**
 * Right pane of [GuildScaffold].
 *
 * Routes to the appropriate feature screen based on the [selectedBuilding]
 * the user tapped in [GuildHubPane]:
 *
 * | Building        | Screen                                  | Nodes                        |
 * |-----------------|-----------------------------------------|------------------------------|
 * | BOUNTY_BOARD    | Job Radar + Trajectory scores           | TECH_RADAR_SCRAPER + SCORER  |
 * | SCRIBE_TOWER    | Resume Forge + Cover Letter + PDF       | RESUME_FORGE_PDF             |
 * | SKILL_ALTAR     | OSRS Skill Tree / Dynamic Skill Matrix  | DYNAMIC_SKILL_MATRIX         |
 * | COURIER_GUILD   | Auto-Apply Queue + Ledger               | AUTO_APPLY_AGENT             |
 * | null            | Overview / CLI bar                      | SAFE_CLI_ENGINE              |
 *
 * Node: UI_SPLIT_SCAFFOLD (right / detail pane)
 */
@Composable
fun DashboardPane(
    selectedBuilding  : GuildBuilding?,
    bountyBoardViewModel: BountyBoardViewModel,
    modifier          : Modifier = Modifier,
) {
    Box(
        modifier         = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
    ) {
        when (selectedBuilding) {
            GuildBuilding.BOUNTY_BOARD  -> BountyBoardScreen(viewModel = bountyBoardViewModel)
            GuildBuilding.SCRIBE_TOWER  -> ScribeTowerScreen()
            GuildBuilding.SKILL_ALTAR   -> SkillAltarScreen()
            GuildBuilding.COURIER_GUILD -> CourierGuildScreen()
            null                        -> DashboardOverview()
        }
    }
}

// ---------------------------------------------------------------------------
// Phase-2+ stub screens — will grow into full MVI features in later phases
// ---------------------------------------------------------------------------

@Composable private fun ScribeTowerScreen() = DashboardStub(
    title    = "📜 Scribe Tower",
    subtitle = "Resume & Cover Letter Forge — RESUME_FORGE_PDF",
)

@Composable private fun SkillAltarScreen() = DashboardStub(
    title    = "⚗ Skill Altar",
    subtitle = "OSRS Skill Matrix — DYNAMIC_SKILL_MATRIX",
)

@Composable private fun CourierGuildScreen() = DashboardStub(
    title    = "📬 Courier Guild",
    subtitle = "Auto-Apply Queue — AUTO_APPLY_AGENT",
)

@Composable private fun DashboardOverview() = DashboardStub(
    title    = "🏰 Guild Overview",
    subtitle = "Select a building or use the CLI bar below",
)

@Composable
private fun DashboardStub(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text  = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text  = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}
