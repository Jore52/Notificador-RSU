package com.example.notificadorrsuv5.ui.screens.project_list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.notificadorrsuv5.domain.model.Project
import com.example.notificadorrsuv5.ui.AppRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsListScreen(navController: NavController) {
    // Esta pantalla ahora es para seleccionar un proyecto para añadir integrantes.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seleccionar Proyecto") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        ProjectsListScreenContent(
            navController = navController,
            modifier = Modifier.padding(paddingValues),
            isForSelection = true // Flag para cambiar el comportamiento del clic.
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsListScreenContent(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ProjectsListViewModel = hiltViewModel(),
    isForSelection: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val dragDropState = rememberDragDropState(
        onMove = { fromIndex, toIndex -> viewModel.onMoveProject(fromIndex, toIndex) }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            placeholder = { Text("Buscar por nombre o coordinador") },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // --- CÓDIGO DE FILTERCHIPS RESTAURADO ---
            FilterChip(
                selected = uiState.sortType == SortType.DEFAULT,
                onClick = { viewModel.onSortChange(SortType.DEFAULT) },
                label = { Text("Default") },
                trailingIcon = {
                    if (uiState.sortType == SortType.DEFAULT) {
                        Icon(
                            imageVector = if (uiState.sortOrder == SortOrder.ASC) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = "Sort Order",
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                }
            )
            FilterChip(
                selected = uiState.sortType == SortType.DEADLINE_DAYS,
                onClick = { viewModel.onSortChange(SortType.DEADLINE_DAYS) },
                label = { Text("Días plazo") },
                trailingIcon = {
                    if (uiState.sortType == SortType.DEADLINE_DAYS) {
                        Icon(
                            imageVector = if (uiState.sortOrder == SortOrder.ASC) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = "Sort Order",
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (uiState.projects.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (isForSelection) "No hay proyectos para seleccionar." else "No hay proyectos. ¡Añade uno!") }
        } else {
            LazyColumn(
                state = dragDropState.lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(uiState.sortType) {
                        if (uiState.sortType == SortType.DEFAULT) {
                            detectDragGesturesAfterLongPress(
                                onDrag = { change, offset -> change.consume(); dragDropState.onDrag(offset) },
                                onDragStart = { offset -> dragDropState.onDragStart(offset) },
                                onDragEnd = { dragDropState.onDragEnd() },
                                onDragCancel = { dragDropState.onDragEnd() }
                            )
                        }
                    }
            ) {
                itemsIndexed(uiState.projects, key = { _, item -> item.id }) { index, project ->
                    DraggableItem(dragDropState = dragDropState, index = index) { isDragging ->
                        ProjectItem(
                            project = project,
                            isDragging = isDragging,
                            isDragEnabled = uiState.sortType == SortType.DEFAULT,
                            onItemClick = {
                                if (isForSelection) {
                                    // TODO: Navegar a la pantalla de gestión de integrantes con el ID del proyecto
                                    // navController.navigate("${AppRoutes.MEMBER_MANAGEMENT}/${project.id}")
                                } else {
                                    // Navega a la pantalla de edición
                                    navController.navigate("${AppRoutes.PROJECT_EDIT_BASE}/${project.id}")
                                }
                            },
                            onToggleNotification = { viewModel.onToggleNotification(project) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectItem(
    project: Project,
    isDragging: Boolean,
    isDragEnabled: Boolean,
    onItemClick: () -> Unit,
    onToggleNotification: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onItemClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = isDragEnabled,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(Icons.Default.DragHandle, "Reordenar")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(project.coordinatorName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${project.deadlineDays} días",
                style = MaterialTheme.typography.bodyMedium,
                color = if (project.deadlineDays < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = project.notificationsEnabled,
                onCheckedChange = { onToggleNotification() },
                modifier = Modifier.height(24.dp)
            )
        }
    }
}


// --- El resto del código de Drag & Drop (DraggableItem, DragDropState, etc.) no cambia ---
private fun LazyListState.getVisibleItemInfoFor(absoluteIndex: Int): LazyListItemInfo? {
    return this.layoutInfo.visibleItemsInfo.getOrNull(absoluteIndex - this.layoutInfo.visibleItemsInfo.first().index)
}

private val LazyListItemInfo.offsetEnd: Int
    get() = this.offset + this.size

@Composable
private fun rememberDragDropState(
    onMove: (Int, Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    return remember(lazyListState) {
        DragDropState(
            lazyListState = lazyListState,
            onMove = onMove,
            scope = scope
        )
    }
}

private class DragDropState(
    val lazyListState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
    private var draggingItemOffset by mutableStateOf(0f)
    private var autoscrollJob by mutableStateOf<Job?>(null)

    val draggingItemLayoutInfo: LazyListItemInfo?
        get() = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == draggingItemIndex }

    fun onDragStart(offset: Offset) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offsetEnd) }
            ?.also {
                draggingItemIndex = it.index
            }
    }

    fun onDrag(offset: Offset) {
        draggingItemOffset += offset.y
        val topOffset = draggingItemLayoutInfo?.offset
        val bottomOffset = topOffset?.plus(draggingItemLayoutInfo?.size ?: 0)

        if (topOffset != null && bottomOffset != null) {
            val startOffset = topOffset + draggingItemOffset
            val endOffset = bottomOffset + draggingItemOffset

            val hoveredItem = lazyListState.layoutInfo.visibleItemsInfo.find {
                val mid = (it.offset + it.offsetEnd) / 2F
                mid in startOffset..endOffset && draggingItemIndex != it.index
            }
            if (hoveredItem != null) {
                val dragIndex = draggingItemIndex ?: return
                if (dragIndex != hoveredItem.index) {
                    onMove(dragIndex, hoveredItem.index)
                    draggingItemIndex = hoveredItem.index
                    draggingItemOffset = 0f
                }
            }
        }

        if (autoscrollJob?.isActive != true) {
            autoscrollJob = scope.launch {
                val listVisibleArea = lazyListState.layoutInfo.viewportSize.height
                val itemVisibleArea = draggingItemLayoutInfo?.size ?: 0
                val dragPlusItem = (draggingItemLayoutInfo?.offset ?: 0) + draggingItemOffset + itemVisibleArea

                if (draggingItemOffset > 0 && dragPlusItem > listVisibleArea) {
                    lazyListState.scrollBy(draggingItemOffset)
                } else if (draggingItemOffset < 0 && (draggingItemLayoutInfo?.offset ?: 0) + draggingItemOffset < 0) {
                    lazyListState.scrollBy(draggingItemOffset)
                }
            }
        }
    }

    fun onDragEnd() {
        draggingItemIndex = null
        draggingItemOffset = 0f
        autoscrollJob?.cancel()
    }
}

@Composable
private fun DraggableItem(
    dragDropState: DragDropState,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit
) {
    val isDragging = index == dragDropState.draggingItemIndex
    val draggingItemCurrentOffset = if (isDragging) {
        dragDropState.draggingItemLayoutInfo?.offset
    } else null
    val yOffset = remember(draggingItemCurrentOffset) {
        if (draggingItemCurrentOffset != null) {
            val startOffset = dragDropState.lazyListState.getVisibleItemInfoFor(index)?.offset ?: 0
            (draggingItemCurrentOffset - startOffset).toFloat()
        } else {
            0f
        }
    }

    Box(modifier = modifier.graphicsLayer { translationY = yOffset }) {
        content(isDragging)
    }
}