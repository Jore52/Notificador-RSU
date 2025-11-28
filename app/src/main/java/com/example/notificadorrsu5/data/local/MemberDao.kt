package com.example.notificadorrsuv5.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    // CAMBIO: El parámetro ahora es String
    @Query("SELECT * FROM members WHERE projectId = :projectId ORDER BY displayOrder ASC")
    fun getMembersForProject(projectId: String): Flow<List<MemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<MemberEntity>)

    // CAMBIO: El parámetro ahora es String
    @Query("DELETE FROM members WHERE projectId = :projectId")
    suspend fun deleteAllByProjectId(projectId: String)

    @Transaction
    // CAMBIO: El parámetro ahora es String
    suspend fun saveProjectMembers(projectId: String, members: List<MemberEntity>) {
        deleteAllByProjectId(projectId)
        insertAll(members)
    }
}