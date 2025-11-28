package com.example.notificadorrsuv5.utils

import com.example.notificadorrsuv5.data.local.ProjectEntity
import com.example.notificadorrsuv5.domain.model.DeadlineCalculationMethod
import com.example.notificadorrsuv5.domain.model.Project

fun ProjectEntity.toDomainModel(): Project {
    return Project(
        id = this.id,
        name = this.name,
        coordinatorName = this.coordinatorName,
        coordinatorEmail = this.coordinatorEmail,
        school = this.school,

        projectType = this.projectType,
        executionPlace = this.executionPlace,
        notificationsEnabled = this.notificationsEnabled,
        deadlineCalculationMethod = try {
            DeadlineCalculationMethod.valueOf(this.deadlineCalculationMethod)
        } catch (e: IllegalArgumentException) {
            DeadlineCalculationMethod.BUSINESS_DAYS
        },
        startDate = this.startDate,
        endDate = this.endDate,
        attachedFileUris = this.attachedFileUris
    )
}