package com.example.notificadorrsuv5.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.notificadorrsuv5.R
import com.example.notificadorrsuv5.data.local.AppDatabase
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes // <--- IMPORTANTE
import com.google.api.services.gmail.GmailScopes
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "notificador_rsu_v5_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideProjectDao(db: AppDatabase) = db.projectDao()

    @Provides
    @Singleton
    fun provideConditionDao(db: AppDatabase) = db.conditionDao()

    @Provides
    @Singleton
    fun provideSentEmailDao(db: AppDatabase) = db.sentEmailDao()

    @Provides
    @Singleton
    fun provideMemberDao(db: AppDatabase) = db.memberDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideGoogleSignInClient(@ApplicationContext context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(context.getString(R.string.google_web_client_id))
            .requestScopes(
                Scope(GmailScopes.GMAIL_SEND),
                Scope(DriveScopes.DRIVE_FILE) // <--- NUEVO PERMISO AGREGADO
            )
            .build()
        return GoogleSignIn.getClient(context, gso)
    }
}