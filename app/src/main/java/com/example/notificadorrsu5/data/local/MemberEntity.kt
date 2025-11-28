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
    // SOLUCIÓN AL WARNING: Agregamos el índice para la clave foránea
    indices = [Index(value = ["projectId"])]
)
data class MemberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: String, // CAMBIO: De Long a String para coincidir con ProjectEntity
    val fullName: String,
    val role: String,
    val dni: String,
    val phone: String,
    val email: String,
    val displayOrder: Int
)