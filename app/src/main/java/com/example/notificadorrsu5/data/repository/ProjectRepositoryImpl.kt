package com.example.notificadorrsuv5.data.repository

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
import javax.inject.Inject

class ProjectRepositoryImpl @Inject constructor(
    private val db: FirebaseDatabase,
    private val authRepository: AuthRepository
) : ProjectRepository {

    private val userId: String
        get() = authRepository.currentUser.value?.uid!!

    override fun getProjects(): Flow<Response<List<Project>>> = callbackFlow {
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
            val newId = project.id.takeIf { it.isNotBlank() } ?: db.reference.push().key!!
            val projectWithId = project.copy(id = newId)
            db.reference.child("projects").child(userId).child(newId).setValue(projectWithId).await()
            Response.Success(true)
        } catch (e: Exception) {
            Response.Failure(e)
        }
    }

    override suspend fun deleteProject(projectId: String): Response<Boolean> {
        return try {
            db.reference.child("projects").child(userId).child(projectId).removeValue().await()
            Response.Success(true)
        } catch (e: Exception) {
            Response.Failure(e)
        }
    }
}