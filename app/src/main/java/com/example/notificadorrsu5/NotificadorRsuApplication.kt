package com.example.notificadorrsuv5

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.* // Importar todo WorkManager
import com.example.notificadorrsuv5.domain.worker.ConditionCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NotificadorRsuApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        setupRecurringWork()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun setupRecurringWork() {
        // 1. DEFINIR RESTRICCIONES: Requiere internet y batería no baja
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // CRÍTICO: Solo ejecuta si hay internet
            .setRequiresBatteryNotLow(true)
            .build()

        // 2. CONFIGURAR LA PETICIÓN
        // Nota: El intervalo mínimo en Android es 15 minutos.
        // Cámbialo a 15 minutos para probar, luego vuelve a 1 día (1, TimeUnit.DAYS) para producción.
        val repeatingRequest = PeriodicWorkRequestBuilder<ConditionCheckWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints) // Aplicar restricciones
            .build()

        // 3. ENCOLAR EL TRABAJO
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            ConditionCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // Cambia a UPDATE para que aplique los cambios nuevos
            repeatingRequest
        )
    }
}