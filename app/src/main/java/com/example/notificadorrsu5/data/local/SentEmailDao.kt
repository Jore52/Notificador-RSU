package com.example.notificadorrsuv5.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SentEmailDao {
    @Query("SELECT * FROM sent_emails ORDER BY sentAt DESC")
    fun getAllSentEmails(): Flow<List<SentEmailEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM sent_emails WHERE conditionId = :conditionId AND wasSuccessful = 1 LIMIT 1)")
    suspend fun hasEmailBeenSentForCondition(conditionId: Long): Boolean

    @Insert
    suspend fun insertSentEmail(sentEmail: SentEmailEntity)
}