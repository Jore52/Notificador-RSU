package com.example.notificadorrsuv5.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "sent_emails")
data class SentEmailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: String, // CAMBIO: De Long a String
    val conditionId: Long,
    val recipientEmail: String,
    val subject: String,
    val body: String,
    val sentAt: LocalDateTime,
    val wasSuccessful: Boolean,
    val errorMessage: String? = null
)