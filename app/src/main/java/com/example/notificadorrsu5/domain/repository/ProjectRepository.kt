package com.example.notificadorrsuv5.domain.repository

import com.example.notificadorrsuv5.domain.model.Project
import com.example.notificadorrsuv5.domain.model.Response
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun getProjects(): Flow<Response<List<Project>>>
    suspend fun getProjectById(projectId: String): Response<Project>
    suspend fun saveProject(project: Project): Response<Boolean>
    suspend fun deleteProject(projectId: String): Response<Boolean>
}
