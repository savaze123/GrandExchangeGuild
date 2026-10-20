package com.osrs.careerguild.presentation.dashboard.skillaltar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// OSRS palette constants
// ---------------------------------------------------------------------------
private val OsrsStone      = Color(0xFF3D2B1F)  // dark stone background
private val OsrsBrown      = Color(0xFF5C3A1E)  // panel / tile background
private val OsrsGold       = Color(0xFFFFD700)  // gold text / active border
private val OsrsDarkBorder = Color(0xFF1A0F08)  // inset border — inactive tile
private val OsrsTextDim    = Color(0xFF9E8A6A)  // dimmed text for inactive tiles

/**
 * Skill Altar screen — DYNAMIC_SKILL_MATRIX UI.
 *
 * Layout:
 * ┌─────────────────────────────────────────────┐
 * │  TopAppBar: "⚗ Skill Altar"  Stats badge    │
 * │  [Expandable] Add-Skill panel               │
 * │  Category filter chips (All / AI / Backend…)│
 * │  LazyVerticalGrid — fixed 3 columns         │
 * │    Tile tap = multi-select toggle           │
 * │    Active  = bright + gold border #FFD700   │
 * │    Inactive = dimmed/gray + dark border     │
 * │    Level badge shows "current/max" (e.g. 72/99) │
 * │  SnackbarHost for confirmations             │
 * └─────────────────────────────────────────────┘
 *
 * Active skills are exposed to [BountyBoardViewModel] via [SkillRepository]
 * reactive Flow — no direct VM-to-VM coupling needed.
 *
 * Node: DYNAMIC_SKILL_MATRIX
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillAltarScreen(
    viewModel     : SkillAltarViewModel,
    isSinglePane  : Boolean = false,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Snackbars for success / error
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar("✅ $it")
            viewModel.dismissSuccess()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar("⚠ $it")
            viewModel.dismissError()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = OsrsStone,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // --- Lowered OSRS Header with visible borders ---
            Surface(
                shape  = RoundedCornerShape(8.dp),
                color  = OsrsBrown.copy(alpha = 0.95f),
                border = BorderStroke(2.dp, OsrsGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 12.dp, end = 12.dp, bottom = 4.dp)
                    .border(
                        1.dp,
                        OsrsDarkBorder,
                        RoundedCornerShape(8.dp)
                    ),
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
                            OutlinedButton(
                                onClick = onNavigateBack,
                                shape   = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                border  = BorderStroke(1.dp, OsrsGold),
                                colors  = ButtonDefaults.outlinedButtonColors(
                                    containerColor = OsrsStone,
                                ),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text(
                                    text  = "← Back to Guild",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = OsrsGold,
                                )
                            }
                        }
                        Column {
                            Text(
                                text  = "⚗ Skill Altar",
                                style = MaterialTheme.typography.titleMedium
                                    .copy(fontWeight = FontWeight.Bold),
                                color = OsrsGold,
                            )
                            if (!uiState.isLoading) {
                                Text(
                                    text  = "${uiState.activeCount} active · ${uiState.totalCount} total",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    color = OsrsGold.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }

                    IconButton(onClick = viewModel::toggleAddSkillPanel) {
                        Icon(
                            imageVector        = if (uiState.isAddSkillExpanded)
                                                    Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle add skill panel",
                            tint               = OsrsGold,
                        )
                    }
                }
            }

            // --- Add Skill expandable panel ---
            AnimatedVisibility(
                visible = uiState.isAddSkillExpanded,
                enter   = expandVertically(),
                exit    = shrinkVertically(),
            ) {
                AddSkillPanel(
                    name                = uiState.addSkillName,
                    category            = uiState.addSkillCategory,
                    availableCategories = listOf("AI & Agents", "Backend & Data", "Automation & Web", "Custom"),
                    onNameChange        = viewModel::updateAddSkillName,
                    onCategoryChange    = viewModel::updateAddSkillCategory,
                    onAdd               = {
                        keyboard?.hide()
                        viewModel.addSkill()
                    },
                    modifier            = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            // --- Category filter chips ---
            if (uiState.categories.isNotEmpty()) {
                CategoryFilterRow(
                    categories       = uiState.categories,
                    selectedCategory = uiState.selectedCategory,
                    onSelect         = viewModel::selectCategory,
                    modifier         = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // --- Skill grid — fixed 3 columns per spec ---
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(32.dp),
                    color    = OsrsGold,
                )
            } else {
                val displayedSkills = uiState.filteredSkills

                LazyVerticalGrid(
                    columns               = GridCells.Fixed(3),
                    contentPadding        = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement   = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.weight(1f),
                ) {
                    if (displayedSkills.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text     = "No skills in this category.\nTap ∨ above to add one.",
                                style    = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color    = OsrsTextDim,
                                modifier = Modifier.padding(32.dp),
                            )
                        }
                    }

                    items(
                        items = displayedSkills,
                        key   = { it.name },
                    ) { skill ->
                        // Tap anywhere on tile to toggle (multi-select)
                        OsrsSkillTile(
                            skill    = skill,
                            onToggle = viewModel::toggleSkill,
                            onRemove = viewModel::removeSkill,
                        )
                    }
                }
            }

            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

// ---------------------------------------------------------------------------
// Add Skill Panel
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSkillPanel(
    name                : String,
    category            : String,
    availableCategories : List<String>,
    onNameChange        : (String) -> Unit,
    onCategoryChange    : (String) -> Unit,
    onAdd               : () -> Unit,
    modifier            : Modifier = Modifier,
) {
    Surface(
        modifier       = modifier.fillMaxWidth(),
        color          = OsrsBrown,
        tonalElevation = 2.dp,
        shape          = androidx.compose.material3.MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text  = "+ Add Skill to Altar",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                    .copy(fontWeight = FontWeight.SemiBold),
                color = OsrsGold,
            )

            OutlinedTextField(
                value         = name,
                onValueChange = onNameChange,
                label         = { Text("Skill name", color = OsrsTextDim) },
                placeholder   = { Text("e.g. LangGraph, PyTorch, Ktor…", color = OsrsTextDim) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = OsrsGold,
                    unfocusedBorderColor = OsrsDarkBorder,
                    focusedTextColor     = OsrsGold,
                    unfocusedTextColor   = OsrsTextDim,
                    cursorColor          = OsrsGold,
                ),
                modifier      = Modifier.fillMaxWidth(),
            )

            // Category dropdown
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded         = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value             = category,
                    onValueChange     = {},
                    readOnly          = true,
                    label             = { Text("Category", color = OsrsTextDim) },
                    trailingIcon      = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    colors            = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = OsrsGold,
                        unfocusedBorderColor = OsrsDarkBorder,
                        focusedTextColor     = OsrsGold,
                        unfocusedTextColor   = OsrsTextDim,
                    ),
                    modifier          = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded         = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    availableCategories.forEach { cat ->
                        DropdownMenuItem(
                            text    = { Text(cat, color = OsrsGold) },
                            onClick = {
                                onCategoryChange(cat)
                                expanded = false
                            },
                        )
                    }
                }
            }

            Button(
                onClick  = onAdd,
                enabled  = name.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = OsrsBrown,
                    contentColor   = OsrsGold,
                ),
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add to Altar")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Category filter chip row
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterRow(
    categories       : List<String>,
    selectedCategory : String?,
    onSelect         : (String?) -> Unit,
    modifier         : Modifier = Modifier,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding        = PaddingValues(horizontal = 4.dp),
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick  = { onSelect(null) },
                label    = { Text("All", color = if (selectedCategory == null) OsrsGold else OsrsTextDim) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OsrsBrown,
                    containerColor         = OsrsStone,
                    selectedLabelColor     = OsrsGold,
                    labelColor             = OsrsTextDim,
                ),
                border   = FilterChipDefaults.filterChipBorder(
                    enabled              = true,
                    selected             = selectedCategory == null,
                    selectedBorderColor  = OsrsGold,
                    borderColor          = OsrsDarkBorder,
                ),
            )
        }
        items(categories.size) { i ->
            val cat = categories[i]
            val isSelected = selectedCategory == cat
            FilterChip(
                selected = isSelected,
                onClick  = { onSelect(if (isSelected) null else cat) },
                label    = { Text(cat, color = if (isSelected) OsrsGold else OsrsTextDim) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OsrsBrown,
                    containerColor         = OsrsStone,
                    selectedLabelColor     = OsrsGold,
                    labelColor             = OsrsTextDim,
                ),
                border   = FilterChipDefaults.filterChipBorder(
                    enabled              = true,
                    selected             = isSelected,
                    selectedBorderColor  = OsrsGold,
                    borderColor          = OsrsDarkBorder,
                ),
            )
        }
    }
}
