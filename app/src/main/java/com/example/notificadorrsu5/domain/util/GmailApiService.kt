package com.example.notificadorrsuv5.domain.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Properties
import java.util.regex.Pattern
import javax.activation.DataHandler
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

@Singleton
class GmailApiService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun sendEmail(
        account: GoogleSignInAccount,
        to: String,
        subject: String,
        body: String,
        attachmentUris: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanTo = to.trim()

            // 1. Configurar Credenciales (Sirve para Gmail y Drive porque usamos la misma cuenta)
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                setOf(GmailScopes.GMAIL_SEND, DriveScopes.DRIVE_READONLY) // Agregamos permiso de lectura
            )
            credential.selectedAccount = account.account

            // 2. Servicios
            val gmailService = Gmail.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Notificador RSU V5").build()

            val driveService = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Notificador RSU V5").build()

            // 3. Crear Email
            val props = Properties()
            val session = Session.getDefaultInstance(props, null)
            val mimeMessage = MimeMessage(session)
            mimeMessage.setFrom(InternetAddress(account.email))
            mimeMessage.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(cleanTo))
            mimeMessage.subject = subject

            val multipart = MimeMultipart("mixed")

            // Parte Texto
            val textPart = MimeBodyPart()
            textPart.setText(body, "utf-8")
            multipart.addBodyPart(textPart)

            // 4. Procesar Adjuntos (Drive)
            attachmentUris.forEach { url ->
                try {
                    val fileId = extractDriveFileId(url)
                    if (fileId != null) {
                        Log.d("GmailAttach", "Descargando archivo de Drive ID: $fileId")

                        // Obtener metadatos (nombre)
                        val driveFile = driveService.files().get(fileId).setFields("name, mimeType").execute()
                        val fileName = driveFile.name ?: "adjunto_drive"

                        // Descargar contenido
                        val outputStream = ByteArrayOutputStream()
                        driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                        val byteArray = outputStream.toByteArray()

                        // Adjuntar
                        val dataSource = ByteArrayDataSource(byteArray, driveFile.mimeType ?: "application/octet-stream")
                        val attachmentPart = MimeBodyPart()
                        attachmentPart.dataHandler = DataHandler(dataSource)
                        attachmentPart.fileName = fileName
                        multipart.addBodyPart(attachmentPart)

                    } else {
                        // Si no es link de Drive, lo ponemos como texto
                        val linkPart = MimeBodyPart()
                        linkPart.setText("\n[Enlace externo: $url]\n")
                        multipart.addBodyPart(linkPart)
                    }
                } catch (e: Exception) {
                    Log.e("GmailAttach", "Error adjuntando $url", e)
                    val errPart = MimeBodyPart()
                    errPart.setText("\n[Error al adjuntar archivo: $url]\n")
                    multipart.addBodyPart(errPart)
                }
            }

            mimeMessage.setContent(multipart)

            // 5. Enviar
            val buffer = ByteArrayOutputStream()
            mimeMessage.writeTo(buffer)
            val rawMessage = Base64.encodeToString(buffer.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

            val message = Message().setRaw(rawMessage)
            gmailService.users().messages().send("me", message).execute()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GmailApiService", "Error fatal", e)
            Result.failure(e)
        }
    }

    private fun extractDriveFileId(url: String): String? {
        // Patrones comunes de URL de Drive
        val patterns = listOf(
            Pattern.compile("/file/d/([^/]+)"),
            Pattern.compile("id=([^&]+)")
        )
        for (p in patterns) {
            val matcher = p.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
}