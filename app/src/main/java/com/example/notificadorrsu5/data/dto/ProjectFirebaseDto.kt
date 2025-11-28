package com.example.notificadorrsuv5.data.dto

import com.example.notificadorrsuv5.domain.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// --- CLASES ESPEJO (DTOs) ---
// Estas clases solo tienen tipos que Firebase entiende (String, Int, List, Boolean)

data class ProjectFirebaseDto(
    val id: String = "",
    val name: String = "",
    val coordinatorName: String = "",
    val coordinatorEmail: String = "",
    val school: String = "",
    val projectType: String = "",
    val executionPlace: String = "",
    val notificationsEnabled: Boolean = true,
    val deadlineCalculationMethod: String = "BUSINESS_DAYS",
    val startDate: String? = null, // Fecha convertida a Texto
    val endDate: String? = null,   // Fecha convertida a Texto
    val fixedDeadlineDaysForCalculation: Int = 10,
    val attachedFileUris: List<String> = emptyList(),
    val conditions: List<ConditionDto> = emptyList(),
    val members: List<MemberDto> = emptyList()
)

data class ConditionDto(
    val id: String = "",
    val name: String = "",
    val subject: String = "",
    val body: String = "",
    val deadlineDays: Int = 0,
    val operator: String = "EQUAL_TO",
    val frequency: String = "ONCE",
    val attachmentUris: List<String> = emptyList()
)

data class MemberDto(
    val id: String = "",
    val fullName: String = "",
    val role: String = "",
    val dni: String = "",
    val phone: String = "",
    val email: String = ""
)

// --- FUNCIONES DE CONVERSIÓN (MAPPERS) ---

// De DOMINIO (Tu App) -> A FIREBASE (Texto)
fun Project.toFirebaseDto(): ProjectFirebaseDto {
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    return ProjectFirebaseDto(
        id = id,
        name = name,
        coordinatorName = coordinatorName,
        coordinatorEmail = coordinatorEmail,
        school = school,
        projectType = projectType,
        executionPlace = executionPlace,
        notificationsEnabled = notificationsEnabled,
        deadlineCalculationMethod = deadlineCalculationMethod.name,
        startDate = startDate?.format(fmt),
        endDate = endDate?.format(fmt),
        fixedDeadlineDaysForCalculation = fixedDeadlineDaysForCalculation,
        attachedFileUris = attachedFileUris,
        conditions = conditions.map { it.toDto() },
        members = members.map { it.toDto() }
    )
}

// De FIREBASE (Texto) -> A DOMINIO (Tu App)
fun ProjectFirebaseDto.toDomain(): Project {
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    return Project(
        id = id,
        name = name,
        coordinatorName = coordinatorName,
        coordinatorEmail = coordinatorEmail,
        school = school,
        projectType = projectType,
        executionPlace = executionPlace,
        notificationsEnabled = notificationsEnabled,
        deadlineCalculationMethod = try { DeadlineCalculationMethod.valueOf(deadlineCalculationMethod) } catch(e: Exception) { DeadlineCalculationMethod.BUSINESS_DAYS },
        startDate = startDate?.let { try { LocalDate.parse(it, fmt) } catch(e: Exception) { null } },
        endDate = endDate?.let { try { LocalDate.parse(it, fmt) } catch(e: Exception) { null } },
        fixedDeadlineDaysForCalculation = fixedDeadlineDaysForCalculation,
        attachedFileUris = attachedFileUris,
        conditions = conditions.map { it.toDomain() },
        members = members.map { it.toDomain() }
    )
}

// Auxiliares para condiciones y miembros
fun ConditionModel.toDto() = ConditionDto(
    id = id, name = name, subject = subject, body = body,
    deadlineDays = deadlineDays, operator = operator.name, frequency = frequency.name,
    attachmentUris = attachmentUris
)

fun ConditionDto.toDomain() = ConditionModel(
    id = id, name = name, subject = subject, body = body,
    deadlineDays = deadlineDays,
    operator = try { ConditionOperator.valueOf(operator) } catch(e: Exception) { ConditionOperator.EQUAL_TO },
    frequency = try { FrequencyType.valueOf(frequency) } catch(e: Exception) { FrequencyType.ONCE },
    attachmentUris = attachmentUris
)

fun MemberModel.toDto() = MemberDto(id=id, fullName=fullName, role=role, dni=dni, phone=phone, email=email)
fun MemberDto.toDomain() = MemberModel(id=id, fullName=fullName, role=role, dni=dni, phone=phone, email=email)