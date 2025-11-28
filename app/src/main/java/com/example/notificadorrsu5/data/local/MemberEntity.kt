package com.example.notificadorrsuv5.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "members",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["projectId"])]
)
data class MemberEntity(
    // CAMBIO: 'String' en lugar de 'Long' para soportar UUIDs de Firebase/Dominio
    // autoGenerate = false porque el ID vendrá del modelo o se generará manualmente
    @PrimaryKey(autoGenerate = false)
    val id: String,

    val projectId: String, // Correcto: String para coincidir con ProjectEntity
    val fullName: String,
    val role: String,
    val dni: String,
    val phone: String,
    val email: String,
    val displayOrder: Int
)