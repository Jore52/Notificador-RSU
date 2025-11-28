package com.example.notificadorrsuv5.di

import android.content.Context
import com.cloudinary.android.MediaManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CloudinaryModule {

    @Provides
    @Singleton
    fun provideCloudinary(@ApplicationContext context: Context): MediaManager {
        val config = mapOf(
            "cloud_name" to "dgwqs6ykp", // Reemplaza con tus datos de Cloudinary
            "api_key" to "823363882872646",
            "api_secret" to "QFy6A-zqBtKCj_EPU6TuHVrPw-c"
        )

        try {
            MediaManager.init(context, config)
        } catch (e: IllegalStateException) {
            // Ya estaba inicializado, ignoramos el error
        }

        return MediaManager.get()
    }
}