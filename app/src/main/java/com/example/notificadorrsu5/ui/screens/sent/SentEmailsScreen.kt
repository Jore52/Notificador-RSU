package com.example.notificadorrsuv5.ui.screens.sent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.notificadorrsuv5.data.local.SentEmailEntity
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentEmailsScreen(
    navController: NavController,
    viewModel: SentEmailsViewModel = hiltViewModel()
) {
    val emails by viewModel.sentEmails.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Correos") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (emails.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("Aún no se ha enviado ningún correo.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(emails) { email -> EmailLogItem(email) }
            }
        }
    }
}

@Composable
fun EmailLogItem(email: SentEmailEntity) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (email.wasSuccessful) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = if (email.wasSuccessful) "Éxito" else "Fallo",
                tint = if (email.wasSuccessful) Color(0xFF388E3C) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(email.recipientEmail, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(email.subject, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(email.sentAt.format(formatter), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (!email.wasSuccessful && email.errorMessage != null) {
                    Text("Error: ${email.errorMessage}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}