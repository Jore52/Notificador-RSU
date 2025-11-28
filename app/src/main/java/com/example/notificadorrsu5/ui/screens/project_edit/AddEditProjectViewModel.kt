package com.example.notificadorrsuv5.ui.screens.project_edit

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.notificadorrsuv5.R
import com.example.notificadorrsuv5.data.local.SentEmailDao
import com.example.notificadorrsuv5.data.local.SentEmailEntity
import com.example.notificadorrsuv5.domain.model.*
import com.example.notificadorrsuv5.domain.repository.AuthRepository
import com.example.notificadorrsuv5.domain.repository.ProjectRepository
import com.example.notificadorrsuv5.domain.util.GmailApiService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

// Enum para controlar el color del banner (Verde/Rojo)
enum class NotificationType { SUCCESS, ERROR }

data class AddEditProjectUiState(
    val project: Project = Project(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isProjectSaved: Boolean = false,
    val isConditionDialogVisible: Boolean = false,
    val editableCondition: ConditionModel? = null,
    val isUploadingFile: Boolean = false,
    // CAMPOS PARA LA NOTIFICACIÓN SUPERIOR (BANNER)
    val notificationMessage: String? = null,
    val notificationType: NotificationType = NotificationType.SUCCESS
)

@HiltViewModel
class AddEditProjectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val authRepository: AuthRepository,
    private val mediaManager: MediaManager,
    private val gmailApiService: GmailApiService,
    private val sentEmailDao: SentEmailDao,
    private val firebaseDatabase: FirebaseDatabase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: String? = savedStateHandle.get<String>("projectId")

    private val _uiState = MutableStateFlow(AddEditProjectUiState())
    val uiState: StateFlow<AddEditProjectUiState> = _uiState.asStateFlow()

    init {
        if (projectId != null && projectId != "-1" && projectId.isNotBlank()) {
            loadProjectData(projectId)
        } else {
            _uiState.update { it.copy(isLoading = false, project = Project(id = UUID.randomUUID().toString())) }
        }
    }

    private fun loadProjectData(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val response = projectRepository.getProjectById(id)) {
                is Response.Success<*> -> {
                    val projectData = response.data as Project
                    _uiState.update { it.copy(project = projectData, isLoading = false) }
                }
                is Response.Failure -> {
                    _uiState.update { it.copy(isLoading = false) }
                    showNotification(response.e?.message ?: "Error al cargar", NotificationType.ERROR)
                }
                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onProjectChange(project: Project) {
        _uiState.update { it.copy(project = project) }
    }

    // --- FUNCIÓN PRINCIPAL DE GUARDADO ---
    fun onSaveProject() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val project = _uiState.value.project

            if (project.name.isBlank() || project.startDate == null || project.endDate == null) {
                _uiState.update { it.copy(isSaving = false) }
                showNotification("Nombre y fechas son obligatorios.", NotificationType.ERROR)
                return@launch
            }

            Log.d("DEBUG_NOTIFICADOR", "Guardando proyecto: ${project.name}")

            when (val result = projectRepository.saveProject(project)) {
                is Response.Success<*> -> {
                    Log.d("DEBUG_NOTIFICADOR", "Proyecto guardado. Iniciando flujo de éxito.")

                    // 1. Mostrar Banner de Éxito
                    showNotification("Proyecto guardado correctamente", NotificationType.SUCCESS)

                    // 2. Ejecutar envío de correo en Segundo Plano (Independiente del ciclo de vida de la pantalla)
                    // Usamos SupervisorJob + IO para que si la pantalla se cierra, esto siga vivo.
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            performImmediateConditionCheck(project)
                        } catch (e: Exception) {
                            Log.e("DEBUG_NOTIFICADOR", "Error crítico en proceso de fondo", e)
                        }
                    }

                    // 3. Esperar 2 segundos para que el usuario vea el mensaje
                    delay(2000)

                    // 4. Activar navegación de salida
                    _uiState.update { it.copy(isProjectSaved = true, isSaving = false) }
                }
                is Response.Failure -> {
                    Log.e("DEBUG_NOTIFICADOR", "Fallo al guardar proyecto en BD")
                    _uiState.update { it.copy(isSaving = false) }
                    showNotification("Error al guardar: ${result.e?.message}", NotificationType.ERROR)
                }
                else -> _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // --- LÓGICA DE NOTIFICACIÓN (BANNER) ---
    private fun showNotification(message: String, type: NotificationType) {
        viewModelScope.launch {
            // Mostrar mensaje
            _uiState.update { it.copy(notificationMessage = message, notificationType = type) }
            // Esperar 2 segundos
            delay(2000)
            // Ocultar mensaje (si seguimos en la pantalla)
            _uiState.update { it.copy(notificationMessage = null) }
        }
    }

    // --- ENVÍO DE CORREOS Y VERIFICACIÓN ---
    private suspend fun performImmediateConditionCheck(project: Project) {
        Log.d("DEBUG_NOTIFICADOR", "--- INICIO CHECK CONDITIONS ---")

        val googleSignInAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (googleSignInAccount == null) {
            Log.e("DEBUG_NOTIFICADOR", "ERROR: No hay cuenta de Google logueada.")
            return // No podemos actualizar UI desde aquí con seguridad si la pantalla ya cerró, solo log.
        }

        if (!project.notificationsEnabled) {
            Log.d("DEBUG_NOTIFICADOR", "AVISO: Las notificaciones están DESACTIVADAS.")
            return
        }

        val currentDeadlineDays = project.deadlineDays

        project.conditions.forEach { condition ->
            val isMet = when (condition.operator) {
                ConditionOperator.EQUAL_TO -> currentDeadlineDays == condition.deadlineDays.toLong()
                ConditionOperator.LESS_THAN -> currentDeadlineDays < condition.deadlineDays.toLong()
                ConditionOperator.GREATER_THAN -> currentDeadlineDays > condition.deadlineDays.toLong()
                ConditionOperator.LESS_THAN_OR_EQUAL_TO -> currentDeadlineDays <= condition.deadlineDays.toLong()
                ConditionOperator.GREATER_THAN_OR_EQUAL_TO -> currentDeadlineDays >= condition.deadlineDays.toLong()
            }

            if (isMet) {
                Log.d("DEBUG_NOTIFICADOR", "   Condición cumplida: ${condition.name}")

                val subject = condition.subject
                val body = condition.body.replacePlaceholders(project)

                try {
                    val emailResult = gmailApiService.sendEmail(
                        googleSignInAccount,
                        project.coordinatorEmail,
                        subject,
                        body,
                        condition.attachmentUris
                    )

                    // Guardar en historial independientemente del resultado
                    saveEmailToHistory(
                        project = project,
                        conditionId = condition.id,
                        subject = subject,
                        body = body,
                        isSuccess = emailResult.isSuccess,
                        errorMessage = emailResult.exceptionOrNull()?.message
                    )

                    if (emailResult.isSuccess) {
                        Log.d("DEBUG_NOTIFICADOR", "   ¡ÉXITO! Correo enviado.")
                        playSound(R.raw.success_sound)
                    } else {
                        Log.e("DEBUG_NOTIFICADOR", "   FALLO en envío: ${emailResult.exceptionOrNull()?.message}")
                        playSound(R.raw.error_sound)
                    }
                } catch (e: Exception) {
                    Log.e("DEBUG_NOTIFICADOR", "   EXCEPCIÓN CRÍTICA AL ENVIAR: ${e.message}")
                }
            }
        }
        Log.d("DEBUG_NOTIFICADOR", "--- FIN CHECK CONDITIONS ---")
    }

    private suspend fun saveEmailToHistory(
        project: Project,
        conditionId: String,
        subject: String,
        body: String,
        isSuccess: Boolean,
        errorMessage: String?
    ) {
        val sentAt = LocalDateTime.now()

        // 1. Guardar en ROOM (Localmente)
        try {
            val entity = SentEmailEntity(
                projectId = project.id,
                conditionId = conditionId,
                recipientEmail = project.coordinatorEmail,
                subject = subject,
                body = body,
                sentAt = sentAt,
                wasSuccessful = isSuccess,
                errorMessage = errorMessage
            )
            sentEmailDao.insertSentEmail(entity)
            Log.d("DEBUG_NOTIFICADOR", "Historial guardado en Room exitosamente.")
        } catch (e: Exception) {
            Log.e("DEBUG_NOTIFICADOR", "Error guardando en Room: ${e.message}")
        }

        // 2. Guardar en FIREBASE (Nube)
        // Verificamos si hay usuario autenticado antes de intentar escribir
        val userId = authRepository.currentUser.value?.uid
        if (userId != null) {
            try {
                val historyRef = firebaseDatabase.reference
                    .child("users").child(userId).child("sent_emails").push()

                // CORRECCIÓN REALIZADA AQUÍ: Se especifica <String, Any> explícitamente
                val firebaseMap = mapOf<String, Any>(
                    "projectId" to project.id,
                    "projectName" to project.name,
                    "recipient" to project.coordinatorEmail,
                    "subject" to subject,
                    "sentAt" to sentAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "success" to isSuccess, // Boolean
                    "error" to (errorMessage ?: "") // String
                )
                historyRef.setValue(firebaseMap).await()
                Log.d("DEBUG_NOTIFICADOR", "Historial guardado en Firebase exitosamente.")
            } catch (e: Exception) {
                Log.e("DEBUG_NOTIFICADOR", "Error guardando en Firebase: ${e.message}")
            }
        }
    }

    private fun playSound(soundResId: Int) {
        try {
            val mediaPlayer = MediaPlayer.create(context, soundResId)
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e("DEBUG_NOTIFICADOR", "No se pudo reproducir el sonido", e)
        }
    }

    private fun String.replacePlaceholders(project: Project): String {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        return this.replace("{nombreCoordinador}", project.coordinatorName)
            .replace("{nombreProyecto}", project.name)
            .replace("{diasRestantes}", project.deadlineDays.toString())
            .replace("{fechaFin}", project.endDate?.format(formatter) ?: "N/A")
            .replace("{fechaInicio}", project.startDate?.format(formatter) ?: "N/A")
    }

    // --- GESTIÓN DEL DIÁLOGO Y ARCHIVOS ---
    fun onShowConditionDialog(condition: ConditionModel? = null) {
        val newEditableCondition = condition ?: ConditionModel(id = UUID.randomUUID().toString())
        _uiState.update { it.copy(isConditionDialogVisible = true, editableCondition = newEditableCondition) }
    }

    fun onDismissConditionDialog() {
        _uiState.update { it.copy(isConditionDialogVisible = false, editableCondition = null) }
    }

    fun onEditableConditionChange(condition: ConditionModel) {
        _uiState.update { it.copy(editableCondition = condition) }
    }

    fun onSaveCondition() {
        val conditionToSave = _uiState.value.editableCondition ?: return
        val currentProject = _uiState.value.project
        val currentConditions = currentProject.conditions.toMutableList()

        val existingIndex = currentConditions.indexOfFirst { it.id == conditionToSave.id }

        if (existingIndex != -1) {
            currentConditions[existingIndex] = conditionToSave
        } else {
            currentConditions.add(conditionToSave)
        }

        onProjectChange(currentProject.copy(conditions = currentConditions.sortedBy { it.name }))
        onDismissConditionDialog()
    }

    fun onRemoveCondition(condition: ConditionModel) {
        val currentProject = _uiState.value.project
        val updatedConditions = currentProject.conditions.toMutableList().apply { remove(condition) }
        onProjectChange(currentProject.copy(conditions = updatedConditions))
    }

    fun onFileAttached(uri: Uri, isDialog: Boolean) {
        _uiState.update { it.copy(isUploadingFile = true) }
        val user = authRepository.currentUser.value
        if (user == null) {
            _uiState.update { it.copy(isUploadingFile = false) }
            showNotification("Error: Usuario no autenticado", NotificationType.ERROR)
            return
        }

        val folderName = if (isDialog) "uploads/${user.uid}/conditions" else "uploads/${user.uid}/projects"

        mediaManager.upload(uri)
            .unsigned("notificador_preset")
            .option("folder", folderName)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val downloadUrl = resultData["secure_url"] as? String
                    if (downloadUrl != null) {
                        if (isDialog) {
                            val currentCondition = _uiState.value.editableCondition
                            if (currentCondition != null) {
                                val updatedCondition = currentCondition.copy(
                                    attachmentUris = currentCondition.attachmentUris + downloadUrl
                                )
                                onEditableConditionChange(updatedCondition)
                            }
                        } else {
                            val currentProject = _uiState.value.project
                            val updatedProject = currentProject.copy(
                                attachedFileUris = currentProject.attachedFileUris + downloadUrl
                            )
                            onProjectChange(updatedProject)
                        }
                        _uiState.update { it.copy(isUploadingFile = false) }
                    } else {
                        _uiState.update { it.copy(isUploadingFile = false) }
                        showNotification("Error: No se recibió URL", NotificationType.ERROR)
                    }
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    _uiState.update { it.copy(isUploadingFile = false) }
                    showNotification("Error al subir: ${error.description}", NotificationType.ERROR)
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            })
            .dispatch()
    }

    fun onFileRemoved(url: String, isDialog: Boolean) {
        viewModelScope.launch {
            if (isDialog) {
                val condition = _uiState.value.editableCondition?.let {
                    it.copy(attachmentUris = it.attachmentUris - url)
                } ?: return@launch
                onEditableConditionChange(condition)
            } else {
                val project = _uiState.value.project
                onProjectChange(project.copy(attachedFileUris = project.attachedFileUris - url))
            }
        }
    }
}