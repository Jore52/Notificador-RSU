package com.example.notificadorrsuv5.data.repository

import android.util.Log
import com.example.notificadorrsu5.data.local.ProjectWithDetails
import com.example.notificadorrsuv5.data.dto.ProjectFirebaseDto
import com.example.notificadorrsuv5.data.dto.toDomain
import com.example.notificadorrsuv5.data.dto.toFirebaseDto
import com.example.notificadorrsuv5.data.local.*
import com.example.notificadorrsuv5.domain.model.*
import com.example.notificadorrsuv5.domain.repository.AuthRepository
import com.example.notificadorrsuv5.domain.repository.ProjectRepository
import com.example.notificadorrsuv5.utils.toDomainModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class ProjectRepositoryImpl @Inject constructor(
    private val db: FirebaseDatabase,
    private val authRepository: AuthRepository,
    private val projectDao: ProjectDao,
    private val conditionDao: ConditionDao,
    private val memberDao: MemberDao,
    private val appDatabase: AppDatabase // <--- INYECTAMOS LA BASE DE DATOS
) : ProjectRepository {

    private val userId: String
        get() = authRepository.currentUser.value?.uid ?: ""

    override fun getProjects(): Flow<Response<List<Project>>> = channelFlow {
        val currentUserId = userId
        if (currentUserId.isBlank()) {
            send(Response.Failure(Exception("Usuario no autenticado")))
            close()
            return@channelFlow
        }

        launch {
            projectDao.getProjectsWithDetails().collect { detailsList ->
                try {
                    val projects = detailsList.map { it.toDomain() }
                    send(Response.Success(projects))
                } catch (e: Exception) {
                    send(Response.Failure(e))
                }
            }
        }

        val ref = db.reference.child("projects").child(currentUserId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch {
                    try {
                        val projectsFromCloud = snapshot.children.mapNotNull {
                            it.getValue(ProjectFirebaseDto::class.java)?.toDomain()
                        }
                        projectsFromCloud.forEach { project ->
                            saveProjectLocally(project)
                        }
                    } catch (e: Exception) {
                        Log.e("ProjectRepo", "Error sincronizando desde Firebase", e)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProjectRepo", "Firebase cancelado: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun getProjectById(projectId: String): Response<Project> {
        return try {
            val localDetails = projectDao.getProjectWithDetailsById(projectId)
            if (localDetails != null) {
                Response.Success(localDetails.toDomain())
            } else {
                if (userId.isBlank()) return Response.Failure(Exception("Usuario no autenticado"))
                val snapshot = db.reference.child("projects").child(userId).child(projectId).get().await()
                val projectDto = snapshot.getValue(ProjectFirebaseDto::class.java)
                if (projectDto != null) {
                    Response.Success(projectDto.toDomain())
                } else {
                    Response.Failure(Exception("Project not found"))
                }
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

            try {
                withTimeout(4000L) {
                    val dtoToSave = projectWithId.toFirebaseDto()
                    db.reference.child("projects").child(userId).child(newId).setValue(dtoToSave).await()
                }
            } catch (e: Exception) {
                Log.e("ProjectRepo", "Error al escribir en Firebase", e)
            }

            saveProjectLocally(projectWithId)
            Response.Success(true)

        } catch (e: Exception) {
            Response.Failure(e)
        }
    }

    private suspend fun saveProjectLocally(project: Project) {
        projectDao.insertProject(project.toEntity())

        val conditionEntities = project.conditions.map { condition ->
            ConditionEntity(
                id = if (condition.id.toLongOrNull() != null) condition.id.toLong() else 0L,
                projectId = project.id,
                name = condition.name,
                subject = condition.subject,
                body = condition.body,
                deadlineDays = condition.deadlineDays,
                operator = condition.operator,
                frequency = condition.frequency,
                displayOrder = 0,
                attachmentUris = condition.attachmentUris
            )
        }
        conditionDao.saveProjectConditions(project.id, conditionEntities)

        val memberEntities = project.members.map { member ->
            MemberEntity(
                id = if (member.id.isBlank()) UUID.randomUUID().toString() else member.id,
                projectId = project.id,
                fullName = member.fullName,
                role = member.role,
                dni = member.dni,
                phone = member.phone,
                email = member.email,
                displayOrder = 0
            )
        }
        memberDao.saveProjectMembers(project.id, memberEntities)
    }

    override suspend fun deleteProject(projectId: String): Response<Boolean> {
        return try {
            val currentUserId = userId
            if (currentUserId.isBlank()) return Response.Failure(Exception("Usuario no autenticado"))

            try {
                db.reference.child("projects").child(currentUserId).child(projectId).removeValue().await()
            } catch (e: Exception) {
                return Response.Failure(Exception("Error al eliminar de la nube: ${e.message}"))
            }

            val details = projectDao.getProjectWithDetailsById(projectId)
            if (details != null) {
                projectDao.deleteProject(details.project)
            }

            Response.Success(true)
        } catch (e: Exception) {
            Response.Failure(e)
        }
    }

    // --- IMPLEMENTACIÓN DEL BORRADO LOCAL ---
    override suspend fun clearLocalData() {
        withContext(Dispatchers.IO) {
            appDatabase.clearAllTables()
            Log.d("ProjectRepo", "Base de datos local limpiada correctamente.")
        }
    }

    // --- MAPPERS (Igual que antes) ---
    private fun ProjectWithDetails.toDomain(): Project {
        return this.project.toDomainModel().copy(
            conditions = this.conditions.map { it.toDomain() },
            members = this.members.map { it.toDomain() }
        )
    }

    private fun ConditionEntity.toDomain(): ConditionModel {
        return ConditionModel(
            id = this.id.toString(),
            name = this.name,
            subject = this.subject,
            body = this.body,
            deadlineDays = this.deadlineDays,
            operator = this.operator,
            frequency = this.frequency,
            attachmentUris = this.attachmentUris
        )
    }

    private fun MemberEntity.toDomain(): MemberModel {
        return MemberModel(
            id = this.id,
            fullName = this.fullName,
            role = this.role,
            dni = this.dni,
            phone = this.phone,
            email = this.email
        )
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