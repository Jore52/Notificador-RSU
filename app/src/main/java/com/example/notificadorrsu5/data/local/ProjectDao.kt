package com.example.notificadorrsuv5.data.local

import androidx.room.*
import com.example.notificadorrsu5.data.local.ProjectWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    // CAMBIO: Usamos @Transaction y devolvemos ProjectWithDetails
    @Transaction
    @Query("SELECT * FROM projects ORDER BY name ASC")
    fun getProjectsWithDetails(): Flow<List<ProjectWithDetails>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectWithDetailsById(projectId: String): ProjectWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    // Mantén este método antiguo solo si lo usas en el Worker, si no, puedes borrarlo
    @Query("SELECT * FROM projects ORDER BY name ASC")
    fun getAllProjects(): Flow<List<ProjectEntity>>
}