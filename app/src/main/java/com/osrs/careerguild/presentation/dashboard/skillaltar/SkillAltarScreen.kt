package com.osrs.careerguild.presentation.dashboard.skillaltar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Skill Altar screen — full UI for the DYNAMIC_SKILL_MATRIX node.
 *
 * Layout:
 * ┌─────────────────────────────────────────────┐
 * │  TopAppBar: "⚗ Skill Altar"  Stats badge    │
 * │  [Expandable] Add-Skill panel               │
 * │  Category filter chips (All / AI / Backend…)│
 * │  LazyVerticalGrid of SkillCards (2 columns) │
 * │  SnackbarHost for confirmations             │
 * └─────────────────────────────────────────────┘
 *
 * Active skills shown with glowing OSRS tier border; inactive are dimmed.
 * The grid is adaptive (min 160 dp) so it reshapes across fold postures.
 *
 * Node: DYNAMIC_SKILL_MATRIX
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillAltarScreen(
    viewModel: SkillAltarViewModel,
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

    Column(modifier = Modifier.fillMaxSize()) {

        // --- Top bar ---
        TopAppBar(
            title = {
                Column {
                    Text(
                        text  = "⚗ Skill Altar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    if (!uiState.isLoading) {
                        Text(
                            text  = "${uiState.activeCount} active · ${uiState.totalCount} total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = viewModel::toggleAddSkillPanel) {
                    Icon(
                        imageVector        = if (uiState.isAddSkillExpanded)
                                                Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle add skill panel",
                        tint               = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        // --- Add Skill expandable panel ---
        AnimatedVisibility(
            visible = uiState.isAddSkillExpanded,
            enter   = expandVertically(),
            exit    = shrinkVertically(),
        ) {
            AddSkillPanel(
                name              = uiState.addSkillName,
                category          = uiState.addSkillCategory,
                availableCategories = listOf("AI & Agents", "Backend & Data", "Automation & Web", "Custom"),
                onNameChange      = viewModel::updateAddSkillName,
                onCategoryChange  = viewModel::updateAddSkillCategory,
                onAdd             = {
                    keyboard?.hide()
                    viewModel.addSkill()
                },
                modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        // --- Category filter chips ---
        if (uiState.categories.isNotEmpty()) {
            CategoryFilterRow(
                categories       = uiState.categories,
                selectedCategory = uiState.selectedCategory,
                onSelect         = viewModel::selectCategory,
                modifier         = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        // --- Skill grid ---
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp),
                color    = MaterialTheme.colorScheme.primary,
            )
        } else {
            val displayedSkills = uiState.filteredSkills

            LazyVerticalGrid(
                columns             = GridCells.Adaptive(minSize = 160.dp),
                contentPadding      = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
                modifier            = Modifier.weight(1f),
            ) {
                if (displayedSkills.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text     = "No skills in this category.\nTap ∨ above to add one.",
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }

                items(
                    items = displayedSkills,
                    key   = { it.name },
                ) { skill ->
                    SkillCard(
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
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text  = "+ Add Skill to Altar",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value         = name,
                onValueChange = onNameChange,
                label         = { Text("Skill name") },
                placeholder   = { Text("e.g. LangGraph, PyTorch, Ktor…") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
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
                    label             = { Text("Category") },
                    trailingIcon      = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier          = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded         = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    availableCategories.forEach { cat ->
                        DropdownMenuItem(
                            text    = { Text(cat) },
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
        modifier            = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding      = PaddingValues(horizontal = 4.dp),
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick  = { onSelect(null) },
                label    = { Text("All") },
            )
        }
        items(categories.size) { i ->
            val cat = categories[i]
            FilterChip(
                selected = selectedCategory == cat,
                onClick  = { onSelect(if (selectedCategory == cat) null else cat) },
                label    = { Text(cat) },
            )
        }
    }
}
