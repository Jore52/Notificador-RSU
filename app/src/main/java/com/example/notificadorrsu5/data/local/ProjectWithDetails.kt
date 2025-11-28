package com.example.notificadorrsu5.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.example.notificadorrsuv5.data.local.ConditionEntity
import com.example.notificadorrsuv5.data.local.MemberEntity
import com.example.notificadorrsuv5.data.local.ProjectEntity

data class ProjectWithDetails(
    @Embedded val project: ProjectEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val conditions: List<ConditionEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val members: List<MemberEntity>
)