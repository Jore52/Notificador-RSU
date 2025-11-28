package com.example.notificadorrsuv5.ui.screens.project_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notificadorrsuv5.domain.model.Project
import com.example.notificadorrsuv5.domain.model.Response
import com.example.notificadorrsuv5.domain.repository.AuthRepository
import com.example.notificadorrsuv5.domain.repository.ProjectRepository
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

enum class SortType { DEFAULT, DEADLINE_DAYS }
enum class SortOrder { ASC, DESC }

data class ProjectsListUiState(
    val projects: List<Project> = emptyList(),
    val error: String? = null,
    val isLoading: Boolean = true,
    val sortType: SortType = SortType.DEFAULT,
    val sortOrder: SortOrder = SortOrder.ASC
)

@HiltViewModel
class ProjectsListViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val authRepository: AuthRepository,
    private val googleSignInClient: GoogleSignInClient,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _sortState = MutableStateFlow(Pair(SortType.DEFAULT, SortOrder.ASC))
    private val _localProjects = MutableStateFlow<List<Project>>(emptyList())

    val currentUser: StateFlow<FirebaseUser?> = authRepository.currentUser

    val uiState: StateFlow<ProjectsListUiState> = combine(_sortState, projectRepository.getProjects(), _localProjects) { sort, projectsResponse, localList ->
        when (projectsResponse) {
            is Response.Loading -> ProjectsListUiState(isLoading = true)
            // CORRECCIÓN: Usar <*> y cast as List<Project>
            is Response.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                val projects = projectsResponse.data as List<Project>

                val sourceList = if (localList.isEmpty() || localList.size != projects.size) projects else localList

                val sortedProjects = when (sort.first) {
                    SortType.DEFAULT -> {
                        if (sort.second == SortOrder.ASC) sourceList.sortedBy { it.name }
                        else sourceList.sortedByDescending { it.name }
                    }
                    SortType.DEADLINE_DAYS -> {
                        if (sort.second == SortOrder.ASC) sourceList.sortedBy { it.deadlineDays }
                        else sourceList.sortedByDescending { it.deadlineDays }
                    }
                }
                if (localList.isEmpty()) {
                    _localProjects.value = sortedProjects
                }

                ProjectsListUiState(projects = sortedProjects, isLoading = false)
            }
            is Response.Failure -> ProjectsListUiState(error = projectsResponse.e?.message)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProjectsListUiState(isLoading = true)
    )

    fun onLogoutClicked(onLogoutComplete: () -> Unit) {
        firebaseAuth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            authRepository.logout()
            onLogoutComplete()
        }
    }

    fun onSortChange(newSortType: SortType) {
        val currentSortType = _sortState.value.first
        val currentSortOrder = _sortState.value.second

        if (newSortType == currentSortType) {
            _sortState.value = Pair(newSortType, if (currentSortOrder == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC)
        } else {
            _sortState.value = Pair(newSortType, SortOrder.ASC)
        }
    }

    fun onToggleNotification(project: Project) {
        viewModelScope.launch {
            val updatedProject = project.copy(notificationsEnabled = !project.notificationsEnabled)
            projectRepository.saveProject(updatedProject)
        }
    }

    fun onDeleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }

    fun onMoveProject(fromIndex: Int, toIndex: Int) {
        val currentList = _localProjects.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            Collections.swap(currentList, fromIndex, toIndex)
            _localProjects.value = currentList
        }
    }
}