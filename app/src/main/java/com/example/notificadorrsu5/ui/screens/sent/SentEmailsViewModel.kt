package com.example.notificadorrsuv5.ui.screens.sent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notificadorrsuv5.data.local.SentEmailDao
import com.example.notificadorrsuv5.data.local.SentEmailEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SentEmailsViewModel @Inject constructor(sentEmailDao: SentEmailDao) : ViewModel() {
    val sentEmails: StateFlow<List<SentEmailEntity>> = sentEmailDao.getAllSentEmails()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}