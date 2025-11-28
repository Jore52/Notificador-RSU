package com.example.notificadorrsuv5.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.DayOfWeek

data class Project(
    val id: String = "", // CORREGIDO: Ahora es String
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
    val attachedFileUris: List<String> = emptyList(),
    val conditions: List<ConditionModel> = emptyList()
) {
    // ... (El resto de la lógica de fechas se mantiene igual) ...
    // Solo asegúrate de copiar la lógica de deadlineDays del archivo original si la borraste
    val fixedDeadlineDaysForCalculation: Int = 10

    val finalReportDate: LocalDate?
        get() {
            val localEndDate = endDate ?: return null
            if (deadlineCalculationMethod == DeadlineCalculationMethod.CALENDAR_DAYS) {
                return localEndDate.plusDays(fixedDeadlineDaysForCalculation.toLong())
            }
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

    val deadlineDays: Long
        get() {
            val finalDate = finalReportDate ?: return Long.MAX_VALUE
            val today = LocalDate.now()
            if (finalDate.isBefore(today)) return ChronoUnit.DAYS.between(today, finalDate)

            if (deadlineCalculationMethod == DeadlineCalculationMethod.CALENDAR_DAYS) {
                return ChronoUnit.DAYS.between(today, finalDate)
            } else {
                var businessDaysLeft: Long = 0
                var currentDate = today
                while (currentDate.isBefore(finalDate) || currentDate.isEqual(finalDate)) {
                    if (currentDate.dayOfWeek != DayOfWeek.SATURDAY && currentDate.dayOfWeek != DayOfWeek.SUNDAY) {
                        businessDaysLeft++
                    }
                    currentDate = currentDate.plusDays(1)
                }
                return if (businessDaysLeft > 0) businessDaysLeft - 1 else 0
            }
        }
}