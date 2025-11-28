package com.example.notificadorrsuv5.ui.screens.project_edit

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.notificadorrsuv5.domain.model.ConditionModel
import com.example.notificadorrsuv5.domain.model.ConditionOperator
import com.example.notificadorrsuv5.domain.model.DeadlineCalculationMethod
import com.example.notificadorrsuv5.domain.model.FrequencyType
import com.example.notificadorrsuv5.domain.util.FileNameResolver
import com.example.notificadorrsuv5.ui.theme.SuccessGreen
import com.example.notificadorrsuv5.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProjectScreen(
    navController: NavController,
    fileNameResolver: FileNameResolver,
    viewModel: AddEditProjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Manejo de Notificaciones (Éxito o Error desde el ViewModel)
    LaunchedEffect(uiState.notificationMessage) {
        uiState.notificationMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = true
            )
        }
    }

    // Navegación al guardar exitosamente
    LaunchedEffect(uiState.isProjectSaved) {
        if (uiState.isProjectSaved) {
            navController.navigateUp()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.project.id.isNotBlank() && uiState.project.id != "0") "Editar Proyecto" else "Añadir Proyecto")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::onSaveProject,
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Guardar")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { PrincipalInfoCard(uiState, viewModel) }
                item { ProjectDetailsCard(uiState, viewModel) }
                item { DatesAndDeadlinesCard(uiState, viewModel) }
                item { ApprovalDocumentCard(uiState, viewModel, fileNameResolver) }
                item { AutomaticNotifierCard(uiState, viewModel) }
            }
        }

        // --- DIÁLOGO DE EDICIÓN DE CONDICIONES ---
        if (uiState.isConditionDialogVisible && uiState.editableCondition != null) {
            ConditionDialog(
                condition = uiState.editableCondition!!,
                isUploadingFile = uiState.isUploadingFile,
                fileNameResolver = fileNameResolver,
                onDismiss = viewModel::onDismissConditionDialog,
                onSave = viewModel::onSaveCondition,
                onConditionChange = viewModel::onEditableConditionChange,
                onFileAttached = { uri -> viewModel.onFileAttached(uri, isDialog = true) },
                onFileRemoved = { url -> viewModel.onFileRemoved(url, isDialog = true) }
            )
        }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun PrincipalInfoCard(uiState: AddEditProjectUiState, viewModel: AddEditProjectViewModel) {
    val project = uiState.project
    FormSectionCard(title = "Información Principal (*)", icon = Icons.Default.Info) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = project.name,
                onValueChange = { viewModel.onProjectChange(project.copy(name = it)) },
                label = { Text("Nombre proyecto RSU") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = project.coordinatorName,
                onValueChange = { viewModel.onProjectChange(project.copy(coordinatorName = it)) },
                label = { Text("Coordinador") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = project.coordinatorEmail,
                onValueChange = { viewModel.onProjectChange(project.copy(coordinatorEmail = it)) },
                label = { Text("Correo del Coordinador") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ProjectDetailsCard(uiState: AddEditProjectUiState, viewModel: AddEditProjectViewModel) {
    val project = uiState.project
    FormSectionCard(title = "Detalles del Proyecto", icon = Icons.Default.ListAlt) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = project.projectType,
                onValueChange = { viewModel.onProjectChange(project.copy(projectType = it)) },
                label = { Text("TIPO (Proyecto, Actividad, ...)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = project.school,
                onValueChange = { viewModel.onProjectChange(project.copy(school = it)) },
                label = { Text("ESCUELA PROFESIONAL") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = project.executionPlace,
                onValueChange = { viewModel.onProjectChange(project.copy(executionPlace = it)) },
                label = { Text("LUGAR DE EJECUCIÓN") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DatesAndDeadlinesCard(uiState: AddEditProjectUiState, viewModel: AddEditProjectViewModel) {
    val project = uiState.project
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    fun showDatePicker(initialDate: LocalDate?, onDateSelected: (LocalDate) -> Unit) {
        val now = LocalDate.now()
        DatePickerDialog(context,
            { _, year: Int, month: Int, day: Int -> onDateSelected(LocalDate.of(year, month + 1, day)) },
            initialDate?.year ?: now.year,
            initialDate?.monthValue?.minus(1) ?: now.monthValue - 1,
            initialDate?.dayOfMonth ?: now.dayOfMonth
        ).show()
    }

    FormSectionCard(title = "Fechas y Plazos (*)", icon = Icons.Default.DateRange) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Método de cálculo:", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    if (project.deadlineCalculationMethod == DeadlineCalculationMethod.BUSINESS_DAYS) "Días Hábiles" else "Días Calendario",
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = {
                    val newMethod = if (project.deadlineCalculationMethod == DeadlineCalculationMethod.BUSINESS_DAYS)
                        DeadlineCalculationMethod.CALENDAR_DAYS
                    else
                        DeadlineCalculationMethod.BUSINESS_DAYS
                    viewModel.onProjectChange(project.copy(deadlineCalculationMethod = newMethod))
                }) {
                    Icon(Icons.Default.CalendarMonth, "Cambiar método de cálculo")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = project.startDate?.format(dateFormatter) ?: "",
                    onValueChange = {},
                    modifier = Modifier.weight(1f).clickable {
                        showDatePicker(project.startDate) { viewModel.onProjectChange(project.copy(startDate = it)) }
                    },
                    readOnly = true,
                    label = { Text("FECHA INICIO", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color.Transparent,
                        disabledBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                OutlinedTextField(
                    value = project.endDate?.format(dateFormatter) ?: "",
                    onValueChange = {},
                    modifier = Modifier.weight(1f).clickable {
                        showDatePicker(project.endDate) { viewModel.onProjectChange(project.copy(endDate = it)) }
                    },
                    readOnly = true,
                    label = { Text("FECHA CULMINACIÓN", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color.Transparent,
                        disabledBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            // FILA 2: Días informe y Fecha final
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val daysText = if (project.deadlineDays == 0L && project.endDate == null) "Días de plazo" else project.deadlineDays.toString()

                OutlinedTextField(
                    value = daysText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("DÍAS INF.", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.weight(1f),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color.Transparent,
                        disabledBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                OutlinedTextField(
                    value = project.finalReportDate?.format(dateFormatter) ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("FECHA INF. FINAL", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.weight(1f),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color.Transparent,
                        disabledBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
    }
}

@Composable
fun ApprovalDocumentCard(uiState: AddEditProjectUiState, viewModel: AddEditProjectViewModel, fileNameResolver: FileNameResolver) {
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFileAttached(it, isDialog = false) }
    }
    val project = uiState.project

    FormSectionCard(title = "Documento de Aprobación", icon = Icons.Default.AttachFile) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isUploadingFile
            ) {
                if (uiState.isUploadingFile && !uiState.isConditionDialogVisible) {
                    CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize))
                } else {
                    Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Adjuntar Documento")
                }
            }

            project.attachedFileUris.forEach { url ->
                val fileName = remember(url) { fileNameResolver.getFileName(Uri.parse(url)) }
                FileItem(fileName = fileName, onRemove = { viewModel.onFileRemoved(url, isDialog = false) })
            }
        }
    }
}

@Composable
fun AutomaticNotifierCard(uiState: AddEditProjectUiState, viewModel: AddEditProjectViewModel) {
    val project = uiState.project
    FormSectionCard(title = "Notificador Automático", icon = Icons.Default.NotificationsActive) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activar notificaciones", modifier = Modifier.weight(1f))
                Switch(
                    checked = project.notificationsEnabled,
                    onCheckedChange = { viewModel.onProjectChange(project.copy(notificationsEnabled = it)) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reglas de Envío", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.onShowConditionDialog() }) {
                    Icon(Icons.Default.Add, "Añadir Regla de Envío")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (project.conditions.isEmpty()) {
                Text(
                    "Añade tu primera regla de envío",
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
                )
            } else {
                project.conditions.forEach { condition ->
                    ConditionItem(
                        condition = condition,
                        onEdit = { viewModel.onShowConditionDialog(condition) },
                        onDelete = { viewModel.onRemoveCondition(condition) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConditionItem(condition: ConditionModel, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(condition.name, fontWeight = FontWeight.Bold)
            Text(
                "Notificar cuando días sean ${condition.operator.symbol} ${condition.deadlineDays}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionDialog(
    condition: ConditionModel,
    isUploadingFile: Boolean,
    fileNameResolver: FileNameResolver,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onConditionChange: (ConditionModel) -> Unit,
    onFileAttached: (Uri) -> Unit,
    onFileRemoved: (String) -> Unit
) {
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(onFileAttached)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Condición") },
        confirmButton = { Button(onClick = onSave) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = condition.name,
                    onValueChange = { onConditionChange(condition.copy(name = it)) },
                    label = { Text("Nombre de la regla") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Condición de Plazo", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = condition.deadlineDays.toString(),
                        onValueChange = {
                            if (it.all { char -> char.isDigit() }) {
                                onConditionChange(condition.copy(deadlineDays = it.toIntOrNull() ?: 0))
                            }
                        },
                        label = { Text("Días") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OperatorDropdown(
                        selectedOperator = condition.operator,
                        onOperatorChange = { onConditionChange(condition.copy(operator = it)) },
                        modifier = Modifier.weight(2f)
                    )
                }
                Text("Frecuencia de Envío", style = MaterialTheme.typography.titleSmall)
                FrequencyDropdown(
                    selectedFrequency = condition.frequency,
                    onFrequencyChange = { onConditionChange(condition.copy(frequency = it)) }
                )
                Text("Contenido del Correo", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = condition.subject,
                    onValueChange = { onConditionChange(condition.copy(subject = it)) },
                    label = { Text("Asunto") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = condition.body,
                    onValueChange = { onConditionChange(condition.copy(body = it)) },
                    label = { Text("Cuerpo del Mensaje") },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
                Text("Adjuntos", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploadingFile
                ) {
                    if (isUploadingFile) {
                        CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize))
                    } else {
                        Icon(Icons.Default.AttachFile, null)
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Adjuntar Archivos")
                    }
                }
                condition.attachmentUris.forEach { url ->
                    FileItem(
                        fileName = fileNameResolver.getFileName(Uri.parse(url)),
                        onRemove = { onFileRemoved(url) }
                    )
                }
            }
        }
    )
}

@Composable
fun FileItem(fileName: String, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(fileName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Quitar") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorDropdown(
    selectedOperator: ConditionOperator,
    onOperatorChange: (ConditionOperator) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = "${selectedOperator.symbol} (${selectedOperator.toReadableString()})",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ConditionOperator.values().forEach { operator ->
                DropdownMenuItem(
                    text = { Text("${operator.symbol} (${operator.toReadableString()})") },
                    onClick = { onOperatorChange(operator); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequencyDropdown(selectedFrequency: FrequencyType, onFrequencyChange: (FrequencyType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedFrequency.toReadableString(),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            FrequencyType.values().forEach { frequency ->
                DropdownMenuItem(
                    text = { Text(frequency.toReadableString()) },
                    onClick = { onFrequencyChange(frequency); expanded = false }
                )
            }
        }
    }
}