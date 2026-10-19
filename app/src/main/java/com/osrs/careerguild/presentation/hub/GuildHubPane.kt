package com.osrs.careerguild.presentation.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.osrs.careerguild.domain.model.GuildBuilding

/**
 * Left pane: hosts the Google Filament 3D OSRS Career Guild Hub.
 *
 * Renders a [FilamentSurfaceView] at 120 Hz with low-poly glTF/GLB buildings.
 * Building tap events are forwarded to [onBuildingSelected] which triggers the
 * [ListDetailPaneScaffold] to open the matching feature on the right pane.
 *
 * TODO Phase-2: Replace placeholder with FilamentSurfaceView + GltfLoader.
 *
 * Node: UI_3D_GUILD_HUB
 * Edge → UI_SPLIT_SCAFFOLD (opens_job_detail_and_resume_forge_on_right_pane)
 */
@Composable
fun GuildHubPane(
    onBuildingSelected: (GuildBuilding) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier          = modifier.fillMaxSize(),
        contentAlignment  = Alignment.Center,
    ) {
        // Phase-1 placeholder — will be replaced by FilamentSurfaceView
        GuildHubPlaceholder(onBuildingSelected = onBuildingSelected)
    }
}
