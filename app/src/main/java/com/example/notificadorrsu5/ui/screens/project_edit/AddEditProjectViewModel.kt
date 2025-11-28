package com.example.notificadorrsuv5.ui.screens.project_edit

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notificadorrsuv5.domain.model.Response
import com.example.notificadorrsuv5.R
import com.example.notificadorrsuv5.domain.model.*
import com.example.notificadorrsuv5.domain.repository.AuthRepository
import com.example.notificadorrsuv5.domain.repository.ProjectRepository
import com.example.notificadorrsuv5.domain.util.GmailApiService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback

data class AddEditProjectUiState(
    val project: Project = Project(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isProjectSaved: Boolean = false,
    val isConditionDialogVisible: Boolean = false,
    val editableCondition: ConditionModel? = null,
    val isUploadingFile: Boolean = false
)

@HiltViewModel
class AddEditProjectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val authRepository: AuthRepository,
    private val mediaManager: MediaManager,
    private val gmailApiService: GmailApiService,
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
                // CORRECCIÓN 1: Usar <*> y cast as Project
                is Response.Success<*> -> {
                    val projectData = response.data as Project
                    _uiState.update { it.copy(project = projectData, isLoading = false) }
                }
                is Response.Failure -> _uiState.update {
                    it.copy(error = response.e?.message, isLoading = false)
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
                _uiState.update { it.copy(error = "Nombre y fechas son obligatorios.", isSaving = false) }
                return@launch
            }

            when (projectRepository.saveProject(project)) {
                // CORRECCIÓN 2: Usar <*> para la respuesta de guardado
                is Response.Success<*> -> {
                    performImmediateConditionCheck(project)
                    _uiState.update { it.copy(isProjectSaved = true, isSaving = false) }
                }
                is Response.Failure -> _uiState.update { it.copy(error = "No se pudo guardar el proyecto.", isSaving = false) }
                else -> _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun performImmediateConditionCheck(project: Project) {
        val googleSignInAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (googleSignInAccount == null) {
            _uiState.update { it.copy(error = "No se pudo enviar el correo: Sesión no iniciada.") }
            return
        }

        if (!project.notificationsEnabled) return

        val currentDeadlineDays = project.deadlineDays
        for (condition in project.conditions) {
            val isMet = when (condition.operator) {
                ConditionOperator.EQUAL_TO -> currentDeadlineDays == condition.deadlineDays.toLong()
                ConditionOperator.LESS_THAN -> currentDeadlineDays < condition.deadlineDays.toLong()
                ConditionOperator.GREATER_THAN -> currentDeadlineDays > condition.deadlineDays.toLong()
                ConditionOperator.LESS_THAN_OR_EQUAL_TO -> currentDeadlineDays <= condition.deadlineDays.toLong()
                ConditionOperator.GREATER_THAN_OR_EQUAL_TO -> currentDeadlineDays >= condition.deadlineDays.toLong()
            }

            if (isMet) {
                val alreadySent = false // TODO: Verificar historial

                if (!alreadySent) {
                    val subject = condition.subject
                    val body = condition.body.replacePlaceholders(project)
                    val emailResult = gmailApiService.sendEmail(googleSignInAccount, project.coordinatorEmail, subject, body, condition.attachmentUris)

                    if (emailResult.isSuccess) {
                        playSound(R.raw.success_sound)
                    } else {
                        _uiState.update { it.copy(error = "Error al enviar el correo.") }
                        playSound(R.raw.error_sound)
                    }
                }
            }
        }
    }

    private fun playSound(soundResId: Int) {
        try {
            val mediaPlayer = MediaPlayer.create(context, soundResId)
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e("ViewModel", "No se pudo reproducir el sonido $soundResId", e)
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
            _uiState.update { it.copy(error = "Error: Usuario no autenticado.", isUploadingFile = false) }
            return
        }

        val folderName = if (isDialog) "uploads/${user.uid}/conditions" else "uploads/${user.uid}/projects"

        // 2. CORRECCIÓN AQUÍ: Usa la variable inyectada 'mediaManager' en vez de 'MediaManager.get()'
        mediaManager.upload(uri)
            .unsigned("notificador_preset") // <--- ¡RECUERDA CAMBIAR ESTO POR TU PRESET REAL!
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
                        _uiState.update { it.copy(error = "Error: No se recibió URL.", isUploadingFile = false) }
                    }
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    _uiState.update { it.copy(error = "Error al subir: ${error.description}", isUploadingFile = false) }
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            })
            .dispatch()
    }

    // LA FUNCIÓN DE BORRADO (CORREGIDA para quitar Firebase Storage):
    fun onFileRemoved(url: String, isDialog: Boolean) {
        viewModelScope.launch {
            // Ya no intentamos borrar de la nube porque Cloudinary requiere firma para eso.
            // Simplemente quitamos el link de nuestra lista local y guardamos el proyecto.

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

    private fun removeFileFromState(url: String, isDialog: Boolean) {
        if (isDialog) {
            val condition = _uiState.value.editableCondition?.let {
                it.copy(attachmentUris = it.attachmentUris - url)
            } ?: return
            onEditableConditionChange(condition)
        } else {
            onProjectChange(_uiState.value.project.copy(attachedFileUris = _uiState.value.project.attachedFileUris - url))
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}