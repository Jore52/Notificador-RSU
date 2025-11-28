package com.example.notificadorrsuv5.domain.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth // Inyectamos FirebaseAuth
) {
    // 1. Inicializamos el StateFlow directamente con el usuario actual de Firebase (si existe)
    private val _currentUser = MutableStateFlow<FirebaseUser?>(firebaseAuth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        // 2. Escuchamos cambios en la sesión (Login, Logout, reinicio de app)
        // Esto mantiene el repositorio sincronizado automáticamente.
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser
        }
    }

    // Ya no es estrictamente necesario llamar a setUser manualmente desde fuera,
    // pero lo dejamos por si acaso.
    fun setUser(user: FirebaseUser?) {
        _currentUser.value = user
    }

    fun logout() {
        firebaseAuth.signOut() // Cerrar sesión en Firebase
        // El listener del init actualizará _currentUser a null automáticamente
    }
}