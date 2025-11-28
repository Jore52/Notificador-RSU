package com.example.notificadorrsuv5.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConditionDao {
    @Query("SELECT * FROM conditions WHERE projectId = :projectId ORDER BY displayOrder ASC")
    fun getConditionsForProject(projectId: Long): Flow<List<ConditionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conditions: List<ConditionEntity>) // Para insertar la lista completa

    @Query("DELETE FROM conditions WHERE projectId = :projectId")
    suspend fun deleteAllByProjectId(projectId: Long) // Para borrar las condiciones antiguas

    @Delete
    suspend fun deleteCondition(condition: ConditionEntity)

    // --- NUEVO MÉTODO DE TRANSACCIÓN ---
    @Transaction
    suspend fun saveProjectConditions(projectId: Long, conditions: List<ConditionEntity>) {
        // 1. Borra todas las condiciones existentes para este proyecto.
        deleteAllByProjectId(projectId)
        // 2. Inserta la nueva lista de condiciones.
        // Nos aseguramos de que todas las condiciones tengan el projectId correcto.
        val conditionsWithProjectId = conditions.map { it.copy(projectId = projectId) }
        insertAll(conditionsWithProjectId)
    }
}