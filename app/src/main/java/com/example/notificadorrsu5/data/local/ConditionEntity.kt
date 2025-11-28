package com.example.notificadorrsuv5.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.notificadorrsuv5.domain.model.ConditionOperator
import com.example.notificadorrsuv5.domain.model.FrequencyType

@Entity(
    tableName = "conditions",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    // SOLUCIÓN AL WARNING: Agregamos el índice
    indices = [Index(value = ["projectId"])]
)
data class ConditionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: String, // CAMBIO: De Long a String
    val name: String,
    val subject: String,
    val body: String,
    val deadlineDays: Int,
    val operator: ConditionOperator,
    val frequency: FrequencyType,
    val displayOrder: Int,
    val attachmentUris: List<String> = emptyList()
)