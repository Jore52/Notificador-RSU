package com.example.notificadorrsuv5.domain.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.notificadorrsuv5.data.local.ConditionDao
import com.example.notificadorrsuv5.data.local.ConditionEntity
import com.example.notificadorrsuv5.data.local.ProjectDao
import com.example.notificadorrsuv5.data.local.ProjectEntity // No se usa explícitamente pero no estorba
import com.example.notificadorrsuv5.data.local.SentEmailDao
import com.example.notificadorrsuv5.data.local.SentEmailEntity
import com.example.notificadorrsuv5.domain.model.ConditionOperator
import com.example.notificadorrsuv5.domain.model.FrequencyType
import com.example.notificadorrsuv5.domain.model.Project
import com.example.notificadorrsuv5.domain.util.EmailSender
import com.example.notificadorrsuv5.utils.toDomainModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

@HiltWorker
class ConditionCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val projectDao: ProjectDao,
    private val conditionDao: ConditionDao,
    private val sentEmailDao: SentEmailDao,
    private val emailSender: EmailSender
) : CoroutineWorker(appContext, workerParams) {

    companion object { const val WORK_NAME = "ConditionCheckWorker" }

    override suspend fun doWork(): Result {
        Log.d(WORK_NAME, "Worker iniciado.")
        try {
            // Obtenemos la lista de proyectos (Flow -> first() para snapshot actual)
            val projects = projectDao.getAllProjects().first()
            if (projects.isEmpty()) {
                Log.d(WORK_NAME, "No hay proyectos, finalizando worker.")
                return Result.success()
            }

            projects.forEach { projectEntity ->

                if (!projectEntity.notificationsEnabled) {
                    Log.d(WORK_NAME, "Saltando proyecto '${projectEntity.name}' porque las notificaciones están deshabilitadas.")
                    return@forEach
                }

                val project = projectEntity.toDomainModel()
                val currentDeadlineDays = project.deadlineDays

                // Iteramos sobre las condiciones del proyecto
                conditionDao.getConditionsForProject(project.id).first().forEach { condition ->

                    if (isConditionMet(condition, currentDeadlineDays)) {
                        Log.d(WORK_NAME, "Condición #${condition.id} CUMPLIDA para '${project.name}'.")

                        // CORRECCIÓN 1: Convertir condition.id (Long) a String
                        val alreadySent = if (condition.frequency == FrequencyType.ONCE) {
                            sentEmailDao.hasEmailBeenSentForCondition(condition.id.toString())
                        } else false

                        if (!alreadySent) {
                            Log.d(WORK_NAME, "Enviando notificación personalizada para la condición '${condition.name}'.")
                            val subject = condition.subject
                            val body = condition.body.replacePlaceholders(project)

                            val emailResult = emailSender.sendEmail(project.coordinatorEmail, subject, body)

                            // CORRECCIÓN 2: Convertir condition.id (Long) a String al guardar
                            val sentEmail = SentEmailEntity(
                                projectId = project.id,
                                conditionId = condition.id.toString(), // <--- AQUÍ EL CAMBIO
                                recipientEmail = project.coordinatorEmail,
                                subject = subject,
                                body = body,
                                sentAt = LocalDateTime.now(),
                                wasSuccessful = emailResult.isSuccess,
                                errorMessage = emailResult.exceptionOrNull()?.message
                            )
                            sentEmailDao.insertSentEmail(sentEmail)
                            Log.d(WORK_NAME, "Registro de envío guardado. Éxito: ${sentEmail.wasSuccessful}")
                        } else {
                            Log.d(WORK_NAME, "Notificación (ONCE) ya enviada. Saltando.")
                        }
                    }
                }
            }
            Log.d(WORK_NAME, "Worker finalizado exitosamente.")
            return Result.success()
        } catch (e: Exception) {
            Log.e(WORK_NAME, "Worker falló.", e)
            return Result.failure()
        }
    }

    private fun String.replacePlaceholders(project: Project): String {
        return this.replace("{nombreCoordinador}", project.coordinatorName)
            .replace("{nombreProyecto}", project.name)
            .replace("{diasPlazo}", project.deadlineDays.toString())
            // Manejo seguro por si finalReportDate es null, aunque en dominio debería estar gestionado
            .replace("{fechaInformeFinal}", project.finalReportDate?.toString() ?: "N/A")
    }

    private fun isConditionMet(condition: ConditionEntity, currentDays: Long): Boolean {
        return when (condition.operator) {
            ConditionOperator.EQUAL_TO -> currentDays == condition.deadlineDays.toLong()
            ConditionOperator.LESS_THAN -> currentDays < condition.deadlineDays
            ConditionOperator.GREATER_THAN -> currentDays > condition.deadlineDays
            ConditionOperator.LESS_THAN_OR_EQUAL_TO -> currentDays <= condition.deadlineDays
            ConditionOperator.GREATER_THAN_OR_EQUAL_TO -> currentDays >= condition.deadlineDays
        }
    }
}