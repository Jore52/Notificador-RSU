package com.example.notificadorrsuv5.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.notificadorrsuv5.data.local.MemberEntity
import com.example.notificadorrsuv5.data.local.MemberDao
@Database(
    entities = [ProjectEntity::class, ConditionEntity::class, SentEmailEntity::class, MemberEntity::class], // <-- Añadir MemberEntity
    version = 4, // <-- Incrementar la versión a 3
    exportSchema = false
)
@TypeConverters(com.example.notificadorrsuv5.data.local.TypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun conditionDao(): ConditionDao
    abstract fun sentEmailDao(): SentEmailDao
    abstract fun memberDao(): MemberDao
}