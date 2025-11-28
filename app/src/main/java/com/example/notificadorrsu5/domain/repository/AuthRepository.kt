package com.example.notificadorrsuv5.domain.repository

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {
    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    fun setUser(user: FirebaseUser?) {
        _currentUser.value = user
    }

    fun logout() {
        _currentUser.value = null
    }
}