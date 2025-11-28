package com.example.notificadorrsuv5.ui.screens.project_edit

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notificadorrsuv5.R
import com.example.notificadorrsuv5.data.local.SentEmailDao
import com.example.notificadorrsuv5.data.local.SentEmailEntity
import com.example.notificadorrsuv5.domain.model.*
import com.example.notificadorrsuv5.domain.repository.AuthRepository
import com.example.notificadorrsuv5.domain.repository.ProjectRepository
import com.example.notificadorrsuv5.domain.util.FileNameResolver
import com.example.notificadorrsuv5.domain.util.GmailApiService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.UUID
import javax.inject.Inject

enum class NotificationType { SUCCESS, ERROR }

data class AddEditProjectUiState(
    val project: Project = Project(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isProjectSaved: Boolean = false, // Sirve para cerrar pantalla tras guardar O eliminar
    val isConditionDialogVisible: Boolean = false,
    val editableCondition: ConditionModel? = null,
    val isUploadingFile: Boolean = false,
    val notificationMessage: String? = null,
    val notificationType: NotificationType = NotificationType.SUCCESS,
    val authRecoverIntent: Intent? = null,
    val isEditing: Boolean = false // <--- NUEVO CAMPO: Indica si estamos editando un proyecto existente
)

@HiltViewModel
class AddEditProjectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val authRepository: AuthRepository,
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
        // Lógica para determinar si es edición o creación
        val isEditing = projectId != null && projectId != "-1" && projectId.isNotBlank()

        if (isEditing) {
            loadProjectData(projectId!!)
            _uiState.update { it.copy(isEditing = true) }
        } else {
            _uiState.update { it.copy(isLoading = false, project = Project(id = UUID.randomUUID().toString()), isEditing = false) }
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

    // --- NUEVA FUNCIÓN: ELIMINAR PROYECTO ---
    fun onDeleteProject() {
        val currentProjectId = _uiState.value.project.id
        if (currentProjectId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) } // Mostramos carga mientras borra

            // El repositorio se encarga de borrar en Local (Room) y Firebase
            when (val result = projectRepository.deleteProject(currentProjectId)) {
                is Response.Success<*> -> {
                    showNotification("Proyecto eliminado correctamente", NotificationType.SUCCESS)
                    delay(1000) // Breve pausa para leer el mensaje
                    // Activamos la bandera para salir de la pantalla
                    _uiState.update { it.copy(isProjectSaved = true, isSaving = false) }
                }
                is Response.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    showNotification("Error al eliminar: ${result.e?.message}", NotificationType.ERROR)
                }
                else -> _uiState.update { it.copy(isSaving = false) }
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
                            Log.e("Notificador", "Error en background", e)
                        }
                    }
                    delay(2000)
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
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (googleAccount == null || !project.notificationsEnabled) return

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

                val emailResult = gmailApiService.sendEmail(
                    googleAccount,
                    project.coordinatorEmail,
                    subject,
                    body,
                    condition.attachmentUris
                )

                if (emailResult.isFailure) {
                    val exception = emailResult.exceptionOrNull()
                    if (exception is UserRecoverableAuthIOException) {
                        _uiState.update { it.copy(authRecoverIntent = exception.intent) }
                        return
                    } else {
                        playSound(R.raw.error_sound)
                        showNotification("Error envío: ${exception?.message}", NotificationType.ERROR)
                    }
                } else {
                    playSound(R.raw.success_sound)
                    showNotification("Correo enviado con éxito", NotificationType.SUCCESS)
                }

                saveEmailToHistory(project, condition.id, subject, body, emailResult.isSuccess, emailResult.exceptionOrNull()?.message)
            }
        }
    }

    private suspend fun saveEmailToHistory(project: Project, conditionId: String, subject: String, body: String, isSuccess: Boolean, errorMessage: String?) {
        val sentAt = LocalDateTime.now()
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
        try { sentEmailDao.insertSentEmail(entity) } catch (e: Exception) { Log.e("Room", "Error", e) }

        val userId = authRepository.currentUser.value?.uid
        if (userId != null) {
            try {
                val historyRef = firebaseDatabase.reference.child("users").child(userId).child("sent_emails").push()
                val firebaseMap = mapOf<String, Any>(
                    "projectId" to project.id,
                    "projectName" to project.name,
                    "recipient" to project.coordinatorEmail,
                    "subject" to subject,
                    "sentAt" to sentAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "success" to isSuccess,
                    "error" to (errorMessage ?: "")
                )
                historyRef.setValue(firebaseMap)
            } catch (e: Exception) { Log.e("Firebase", "Error", e) }
        }
    }

    private fun playSound(soundResId: Int) {
        try {
            val mediaPlayer = MediaPlayer.create(context, soundResId)
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.start()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun String.replacePlaceholders(project: Project): String {
        return this.replace("{nombreCoordinador}", project.coordinatorName)
            .replace("{nombreProyecto}", project.name)
            .replace("{diasRestantes}", project.deadlineDays.toString())
    }

    fun onFileAttached(uri: Uri, isDialog: Boolean) {
        _uiState.update { it.copy(isUploadingFile = true) }
        val account = GoogleSignIn.getLastSignedInAccount(context)

        if (account == null) {
            _uiState.update { it.copy(isUploadingFile = false) }
            showNotification("Debes iniciar sesión con Google", NotificationType.ERROR)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_FILE))
                credential.selectedAccount = account.account

                val driveService = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                    .setApplicationName("Notificador RSU V5").build()

                val name = fileNameResolver.getFileName(uri)
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val fileMetadata = File().apply { this.name = name }
                val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("No se pudo leer archivo")
                val mediaContent = InputStreamContent(mimeType, inputStream)

                val file = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, webViewLink")
                    .execute()

                val driveLink = file.webViewLink ?: "https://drive.google.com/file/d/${file.id}/view"
                val combinedData = "$name|$driveLink"

                withContext(Dispatchers.Main) {
                    if (isDialog) {
                        _uiState.value.editableCondition?.let {
                            onEditableConditionChange(it.copy(attachmentUris = it.attachmentUris + combinedData))
                        }
                    } else {
                        val p = _uiState.value.project
                        onProjectChange(p.copy(attachedFileUris = p.attachedFileUris + combinedData))
                    }
                    _uiState.update { it.copy(isUploadingFile = false) }
                    showNotification("Archivo subido a Drive", NotificationType.SUCCESS)
                }

            } catch (e: Exception) {
                if (e is UserRecoverableAuthIOException) {
                    _uiState.update { it.copy(isUploadingFile = false, authRecoverIntent = e.intent) }
                } else {
                    Log.e("DriveUpload", "Error", e)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isUploadingFile = false) }
                        showNotification("Error: ${e.message}", NotificationType.ERROR)
                    }
                }
            }
        }
    }

    fun onFileRemoved(url: String, isDialog: Boolean) {
        if (isDialog) {
            _uiState.value.editableCondition?.let {
                onEditableConditionChange(it.copy(attachmentUris = it.attachmentUris - url))
            }
        } else {
            val p = _uiState.value.project
            onProjectChange(p.copy(attachedFileUris = p.attachedFileUris - url))
        }
    }

    fun onShowConditionDialog(condition: ConditionModel? = null) {
        val newCondition = condition ?: ConditionModel(id = UUID.randomUUID().toString())
        _uiState.update { it.copy(isConditionDialogVisible = true, editableCondition = newCondition) }
    }
    fun onDismissConditionDialog() { _uiState.update { it.copy(isConditionDialogVisible = false, editableCondition = null) } }
    fun onEditableConditionChange(condition: ConditionModel) { _uiState.update { it.copy(editableCondition = condition) } }
    fun onSaveCondition() {
        val condition = _uiState.value.editableCondition ?: return
        val p = _uiState.value.project
        val conditions = p.conditions.toMutableList()
        val idx = conditions.indexOfFirst { it.id == condition.id }
        if (idx != -1) conditions[idx] = condition else conditions.add(condition)
        onProjectChange(p.copy(conditions = conditions))
        onDismissConditionDialog()
    }
    fun onRemoveCondition(condition: ConditionModel) {
        val p = _uiState.value.project
        onProjectChange(p.copy(conditions = p.conditions - condition))
    }
}