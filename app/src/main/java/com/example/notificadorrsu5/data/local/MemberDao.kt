package com.example.notificadorrsuv5.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE projectId = :projectId ORDER BY displayOrder ASC")
    fun getMembersForProject(projectId: Long): Flow<List<MemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<MemberEntity>)

    @Query("DELETE FROM members WHERE projectId = :projectId")
    suspend fun deleteAllByProjectId(projectId: Long)

    @Transaction
    suspend fun saveProjectMembers(projectId: Long, members: List<MemberEntity>) {
        deleteAllByProjectId(projectId)
        insertAll(members)
    }
}