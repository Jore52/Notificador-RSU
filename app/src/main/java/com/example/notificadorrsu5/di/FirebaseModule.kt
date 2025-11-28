package com.example.notificadorrsuv5.di

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        val database = FirebaseDatabase.getInstance()
        try {
            // ESTA LÍNEA ES LA CLAVE PARA QUE NO SE CONGELE
            database.setPersistenceEnabled(true)
            Log.d("FirebaseModule", "Persistencia offline activada correctamente")
        } catch (e: Exception) {
            // Si ya estaba activa, ignoramos el error
            Log.w("FirebaseModule", "La persistencia ya estaba activa: ${e.message}")
        }
        return database
    }
}