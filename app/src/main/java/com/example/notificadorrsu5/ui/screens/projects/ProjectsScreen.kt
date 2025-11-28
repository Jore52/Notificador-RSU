package com.example.notificadorrsuv5.ui.screens.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.notificadorrsuv5.domain.model.Project
import com.example.notificadorrsuv5.ui.AppRoutes
import com.example.notificadorrsuv5.ui.screens.project_list.*
import com.example.notificadorrsuv5.ui.theme.SearchBarColor
import com.example.notificadorrsuv5.ui.theme.SuccessGreen
import androidx.compose.foundation.shape.RoundedCornerShape
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    navController: NavController,
    viewModel: ProjectsListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Proyectos", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { navController.navigate(AppRoutes.PROJECT_EDIT_BASE + "/-1") },
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Add, "Agregar Nuevo Proyecto")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Buscador Rediseñado
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Buscar por nombre o coordinador") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = SearchBarColor,
                    focusedContainerColor = SearchBarColor,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            // Filtros Rediseñados
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.sortType == SortType.DEFAULT,
                    onClick = { viewModel.onSortChange(SortType.DEFAULT) },
                    label = { Text("Default") },
                    leadingIcon = if (uiState.sortType == SortType.DEFAULT) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } } else { null },
                    trailingIcon = {
                        if (uiState.sortType == SortType.DEFAULT) {
                            Icon(if (uiState.sortOrder == SortOrder.ASC) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                        }
                    }
                )
                FilterChip(
                    selected = uiState.sortType == SortType.DEADLINE_DAYS,
                    onClick = { viewModel.onSortChange(SortType.DEADLINE_DAYS) },
                    label = { Text("Días plazo") },
                    leadingIcon = if (uiState.sortType == SortType.DEADLINE_DAYS) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } } else { null }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Lista de Proyectos con el nuevo Card
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(uiState.projects, key = { _, item -> item.id }) { index, project ->
                    ProjectCard(
                        projectName = project.name,
                        coordinatorName = project.coordinatorName,
                        deadlineDays = project.deadlineDays.toInt(),
                        isNotifierEnabled = project.notificationsEnabled,
                        onToggleNotifier = { viewModel.onToggleNotification(project) },
                        onClick = { navController.navigate("${AppRoutes.PROJECT_EDIT_BASE}/${project.id}") }
                    )
                }
            }
        }
    }
}


// --- NUEVO COMPONENTE: ProjectCard REDISEÑADO ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCard(
    projectName: String,
    coordinatorName: String,
    deadlineDays: Int,
    isNotifierEnabled: Boolean,
    onToggleNotifier: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deadlineColor = when {
        deadlineDays <= 5 -> MaterialTheme.colorScheme.error
        deadlineDays <= 15 -> MaterialTheme.colorScheme.tertiary
        else -> SuccessGreen
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Reordenar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = coordinatorName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = { /* No action */ },
                    label = { Text("$deadlineDays días") },
                    leadingIcon = { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = deadlineColor, leadingIconContentColor = deadlineColor),
                    border = BorderStroke(1.dp, deadlineColor.copy(alpha = 0.4f))
                )
            }
            Switch(
                checked = isNotifierEnabled,
                onCheckedChange = onToggleNotifier,
                thumbContent = if (isNotifierEnabled) { { Icon(Icons.Filled.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) } } else { null }
            )
        }
    }
}