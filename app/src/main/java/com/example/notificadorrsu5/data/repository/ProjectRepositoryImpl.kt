package com.example.notificadorrsuv5.data.repository

import com.example.notificadorrsuv5.data.local.ConditionDao
import com.example.notificadorrsuv5.data.local.ProjectDao
import com.example.notificadorrsuv5.data.local.ProjectEntity
import com.example.notificadorrsuv5.domain.model.DeadlineCalculationMethod
import com.example.notificadorrsuv5.domain.model.Project
import com.example.notificadorrsuv5.domain.model.Response
import com.example.notificadorrsuv5.domain.repository.AuthRepository
import com.example.notificadorrsuv5.domain.repository.ProjectRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class ProjectRepositoryImpl @Inject constructor(
    private val db: FirebaseDatabase,
    private val authRepository: AuthRepository,
    private val projectDao: ProjectDao,
    private val conditionDao: ConditionDao
) : ProjectRepository {

    // Obtiene el ID del usuario actual de forma segura
    private val userId: String
        get() = authRepository.currentUser.value?.uid ?: ""

    override fun getProjects(): Flow<Response<List<Project>>> = callbackFlow {
        // CORRECCIÓN: Si no hay usuario, no cargamos nada
        if (userId.isBlank()) {
            trySend(Response.Failure(Exception("Usuario no autenticado")))
            close()
            return@callbackFlow
        }

        // CORRECCIÓN: La ruta incluye el userId para aislar datos
        val ref = db.reference.child("projects").child(userId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val projects = snapshot.children.mapNotNull { it.getValue(Project::class.java) }
                trySend(Response.Success(projects))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Response.Failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun getProjectById(projectId: String): Response<Project> {
        return try {
            if (userId.isBlank()) return Response.Failure(Exception("Usuario no autenticado"))

            val snapshot = db.reference.child("projects").child(userId).child(projectId).get().await()
            val project = snapshot.getValue(Project::class.java)
            if (project != null) {
                Response.Success(project)
            } else {
                Response.Failure(Exception("Project not found"))
            }
        } catch (e: Exception) {
            Response.Failure(e)
        }
    }

    override suspend fun saveProject(project: Project): Response<Boolean> {
        return try {
            if (userId.isBlank()) return Response.Failure(Exception("Usuario no logueado"))

            val newId = if (project.id.isBlank()) UUID.randomUUID().toString() else project.id
            val projectWithId = project.copy(id = newId)

            // CORRECCIÓN: Guardar bajo el nodo del usuario
            db.reference.child("projects").child(userId).child(newId).setValue(projectWithId).await()

            // Guardar en Room (Local) - Opcional si solo quieres nube, pero útil para el Worker
            val entity = projectWithId.toEntity()
            projectDao.insertProject(entity)

            Response.Success(true)
        } catch (e: Exception) {
            Response.Failure(e)
        }
    }

    override suspend fun deleteProject(projectId: String): Response<Boolean> {
        return try {
            if (userId.isBlank()) return Response.Failure(Exception("Usuario no autenticado"))

            db.reference.child("projects").child(userId).child(projectId).removeValue().await()
            // También borrar localmente si es necesario
            // projectDao.deleteProjectById(projectId)
            Response.Success(true)
        } catch (e: Exception) {
            Response.Failure(e)
        }
    }

    private fun Project.toEntity(): ProjectEntity {
        return ProjectEntity(
            id = this.id,
            name = this.name,
            coordinatorName = this.coordinatorName,
            coordinatorEmail = this.coordinatorEmail,
            school = this.school,
            projectType = this.projectType,
            executionPlace = this.executionPlace,
            notificationsEnabled = this.notificationsEnabled,
            deadlineCalculationMethod = this.deadlineCalculationMethod.name,
            startDate = this.startDate ?: LocalDate.now(),
            endDate = this.endDate ?: LocalDate.now(),
            attachedFileUris = this.attachedFileUris
        )
    }
}