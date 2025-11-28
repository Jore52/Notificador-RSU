package com.example.notificadorrsuv5.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "projects")
data class ProjectEntity(
    // CAMBIO: autoGenerate = false porque usaremos el ID de String de Firebase/UUID
    @PrimaryKey(autoGenerate = false)
    val id: String,
    val name: String,
    val coordinatorName: String,
    val coordinatorEmail: String,
    val school: String,
    val projectType: String = "",
    val executionPlace: String = "",
    val notificationsEnabled: Boolean = true,
    val deadlineCalculationMethod: String = "BUSINESS_DAYS",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val fixedDeadlineDaysForCalculation: Int = 10,
    val attachedFileUris: List<String> = emptyList()
)