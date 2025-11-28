package com.example.notificadorrsu5.domain.model

sealed class Response<out T> {
    // Estado de carga
    object Loading : Response<Nothing>()

    // Estado de éxito: contiene los datos (data)
    data class Success<out T>(val data: T) : Response<T>()

    // Estado de error: contiene la excepción (e)
    data class Failure(val e: Exception?) : Response<Nothing>()
}