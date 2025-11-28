package com.example.notificadorrsuv5.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConditionDao {
    // CAMBIO: projectId ahora es String
    @Query("SELECT * FROM conditions WHERE projectId = :projectId ORDER BY displayOrder ASC")
    fun getConditionsForProject(projectId: String): Flow<List<ConditionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conditions: List<ConditionEntity>)

    // CAMBIO: projectId ahora es String
    @Query("DELETE FROM conditions WHERE projectId = :projectId")
    suspend fun deleteAllByProjectId(projectId: String)

    @Delete
    suspend fun deleteCondition(condition: ConditionEntity)

    // --- TRANSACCIÓN ACTUALIZADA ---
    @Transaction
    // CAMBIO: projectId ahora es String
    suspend fun saveProjectConditions(projectId: String, conditions: List<ConditionEntity>) {
        // 1. Borra todas las condiciones existentes para este proyecto.
        deleteAllByProjectId(projectId)

        // 2. Inserta la nueva lista de condiciones.
        // Ahora sí compilará porque both 'it.copy' y 'projectId' son Strings.
        val conditionsWithProjectId = conditions.map { it.copy(projectId = projectId) }
        insertAll(conditionsWithProjectId)
    }
}