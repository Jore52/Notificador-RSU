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
import com.example.notificadorrsuv5.domain.util.FileNameResolver
import com.example.notificadorrsuv5.domain.util.GmailApiService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

enum class NotificationType { SUCCESS, ERROR }

data class AddEditProjectUiState(
    val project: Project = Project(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isProjectSaved: Boolean = false,
    val isConditionDialogVisible: Boolean = false,
    val editableCondition: ConditionModel? = null,
    val isUploadingFile: Boolean = false,
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
    private val fileNameResolver: FileNameResolver,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: String? = savedStateHandle.get<String>("projectId")

    private val _uiState = MutableStateFlow(AddEditProjectUiState())
    val uiState: StateFlow<AddEditProjectUiState> = _uiState.asStateFlow()

    private var notificationJob: Job? = null

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

    fun onSaveProject() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val project = _uiState.value.project

            if (project.name.isBlank() || project.startDate == null || project.endDate == null) {
                _uiState.update { it.copy(isSaving = false) }
                showNotification("Nombre y fechas son obligatorios.", NotificationType.ERROR)
                return@launch
            }

            when (val result = projectRepository.saveProject(project)) {
                is Response.Success<*> -> {
                    showNotification("Proyecto guardado correctamente", NotificationType.SUCCESS)
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            performImmediateConditionCheck(project)
                        } catch (e: Exception) {
                            Log.e("DEBUG_NOTIFICADOR", "Error crítico en proceso de fondo", e)
                        }
                    }
                    delay(2500)
                    _uiState.update { it.copy(isProjectSaved = true, isSaving = false) }
                }
                is Response.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    showNotification("Error al guardar: ${result.e?.message}", NotificationType.ERROR)
                }
                else -> _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun showNotification(message: String, type: NotificationType) {
        notificationJob?.cancel()
        notificationJob = viewModelScope.launch {
            _uiState.update { it.copy(notificationMessage = message, notificationType = type) }
            delay(3000)
            _uiState.update { it.copy(notificationMessage = null) }
        }
    }

    private suspend fun performImmediateConditionCheck(project: Project) {
        val googleSignInAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (googleSignInAccount == null || !project.notificationsEnabled) return

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

                    saveEmailToHistory(
                        project = project,
                        conditionId = condition.id,
                        subject = subject,
                        body = body,
                        isSuccess = emailResult.isSuccess,
                        errorMessage = emailResult.exceptionOrNull()?.message
                    )

                    if (emailResult.isSuccess) {
                        playSound(R.raw.success_sound)
                        showNotification("Correo de notificación enviado", NotificationType.SUCCESS)
                    } else {
                        playSound(R.raw.error_sound)
                        showNotification("Error enviando correo: ${emailResult.exceptionOrNull()?.message}", NotificationType.ERROR)
                    }
                } catch (e: Exception) {
                    Log.e("DEBUG_NOTIFICADOR", "Excepción envío", e)
                }
            }
        }
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
        } catch (e: Exception) {
            Log.e("DEBUG_NOTIFICADOR", "Error Room: ${e.message}")
        }

        val userId = authRepository.currentUser.value?.uid
        if (userId != null) {
            try {
                val historyRef = firebaseDatabase.reference
                    .child("users").child(userId).child("sent_emails").push()

                val firebaseMap = mapOf<String, Any>(
                    "projectId" to project.id,
                    "projectName" to project.name,
                    "recipient" to project.coordinatorEmail,
                    "subject" to subject,
                    "sentAt" to sentAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "success" to isSuccess,
                    "error" to (errorMessage ?: "")
                )
                historyRef.setValue(firebaseMap).await()
            } catch (e: Exception) {
                Log.e("DEBUG_NOTIFICADOR", "Error Firebase: ${e.message}")
            }
        }
    }

    private fun playSound(soundResId: Int) {
        try {
            val mediaPlayer = MediaPlayer.create(context, soundResId)
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e("DEBUG_NOTIFICADOR", "No se pudo reproducir sonido", e)
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

    // --- DIALOGOS Y ARCHIVOS ---
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

        // 1. Obtener nombre y extensión original
        val originalFileName = fileNameResolver.getFileName(uri)
        val extension = originalFileName.substringAfterLast('.', "")

        // 2. Generar nombre único manteniendo la extensión
        val uniqueName = UUID.randomUUID().toString()
        val publicIdWithExtension = if (extension.isNotEmpty()) "$uniqueName.$extension" else uniqueName

        mediaManager.upload(uri)
            .unsigned("notificador_preset")
            .option("folder", folderName)
            .option("resource_type", "raw")
            .option("public_id", publicIdWithExtension)
            .option("access_mode", "public") // <--- SOLUCIÓN AL ERROR 401: Forzar acceso público
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val downloadUrl = resultData["secure_url"] as? String
                    if (downloadUrl != null) {
                        Log.d("DEBUG_UPLOAD", "Archivo subido exitosamente: $downloadUrl")
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
                    Log.e("DEBUG_UPLOAD", "Error Cloudinary: ${error.description}")
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