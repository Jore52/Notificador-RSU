package com.example.notificadorrsuv5.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Project(
    val id: String = "",
    val name: String = "",
    val coordinatorName: String = "",
    val coordinatorEmail: String = "",
    val school: String = "",
    val projectType: String = "",
    val executionPlace: String = "",
    val notificationsEnabled: Boolean = true,
    val deadlineCalculationMethod: DeadlineCalculationMethod = DeadlineCalculationMethod.BUSINESS_DAYS,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    // Días fijos para el informe final (por defecto 10, pero ahora es editable si lo necesitas)
    val fixedDeadlineDaysForCalculation: Int = 10,
    val attachedFileUris: List<String> = emptyList(),
    val conditions: List<ConditionModel> = emptyList(),
    // [NUEVO] Lista de integrantes agregada al dominio
    val members: List<MemberModel> = emptyList()
) {

    // Calcula la fecha del informe final basándose en la fecha de fin y el método de cálculo
    val finalReportDate: LocalDate?
        get() {
            val localEndDate = endDate ?: return null

            if (deadlineCalculationMethod == DeadlineCalculationMethod.CALENDAR_DAYS) {
                return localEndDate.plusDays(fixedDeadlineDaysForCalculation.toLong())
            }

            // Cálculo de días hábiles (excluye sábados y domingos)
            var tempDate = localEndDate
            var businessDaysToAdd = fixedDeadlineDaysForCalculation
            while (businessDaysToAdd > 0) {
                tempDate = tempDate.plusDays(1)
                if (tempDate.dayOfWeek != DayOfWeek.SATURDAY && tempDate.dayOfWeek != DayOfWeek.SUNDAY) {
                    businessDaysToAdd--
                }
            }
            return tempDate
        }

    // Calcula cuántos días faltan para la entrega del informe
    val deadlineDays: Long
        get() {
            val finalDate = finalReportDate ?: return Long.MAX_VALUE
            val today = LocalDate.now()

            // Si la fecha final ya pasó o es hoy
            if (finalDate.isBefore(today)) return ChronoUnit.DAYS.between(today, finalDate)

            if (deadlineCalculationMethod == DeadlineCalculationMethod.CALENDAR_DAYS) {
                return ChronoUnit.DAYS.between(today, finalDate)
            } else {
                // Conteo de días hábiles restantes
                var businessDaysLeft: Long = 0
                var currentDate = today
                while (currentDate.isBefore(finalDate) || currentDate.isEqual(finalDate)) {
                    if (currentDate.dayOfWeek != DayOfWeek.SATURDAY && currentDate.dayOfWeek != DayOfWeek.SUNDAY) {
                        businessDaysLeft++
                    }
                    currentDate = currentDate.plusDays(1)
                }
                // Restamos 1 porque el between suele ser exclusivo/inclusivo dependiendo de la lógica exacta deseada,
                // pero si 'today' cuenta, el cálculo ajustado es:
                return if (businessDaysLeft > 0) businessDaysLeft - 1 else 0
            }
        }
}