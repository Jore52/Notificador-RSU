package com.example.notificadorrsuv5.domain.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
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
            // 1. Limpieza y Validación del Destinatario
            val cleanTo = to.trim().replace("\n", "").replace("\r", "")

            if (cleanTo.isBlank() || !cleanTo.contains("@")) {
                Log.e("GmailApiService", "Error: El destinatario '$cleanTo' no es válido.")
                return@withContext Result.failure(IllegalArgumentException("Correo destinatario inválido"))
            }

            // 2. Configurar Credenciales (Gmail + Drive)
            // Se usa DRIVE_READONLY para poder descargar los adjuntos sin modificar nada
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                setOf(GmailScopes.GMAIL_SEND, DriveScopes.DRIVE_READONLY)
            )
            credential.selectedAccount = account.account

            // 3. Inicializar Servicios
            val gmailService = Gmail.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Notificador RSU V5").build()

            val driveService = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Notificador RSU V5").build()

            // 4. Crear Mensaje MIME
            val props = Properties()
            val session = Session.getDefaultInstance(props, null)
            val mimeMessage = MimeMessage(session)

            mimeMessage.setFrom(InternetAddress(account.email))
            mimeMessage.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(cleanTo))
            mimeMessage.subject = subject

            val multipart = MimeMultipart("mixed")

            // Parte Texto
            val messageBodyPart = MimeBodyPart()
            messageBodyPart.setText(body, "utf-8")
            multipart.addBodyPart(messageBodyPart)

            // 5. Procesar Adjuntos (Google Drive)
            attachmentUris.forEach { rawString ->
                try {
                    // --- PARSEO DEL FORMATO "NOMBRE|URL" ---
                    // Si el string contiene '|', tomamos la segunda parte (la URL).
                    // Si no, asumimos que es la URL directa.
                    val url = if (rawString.contains("|")) {
                        rawString.substringAfter("|")
                    } else {
                        rawString
                    }

                    // Extraer ID de archivo de Drive
                    val fileId = extractDriveFileId(url)

                    if (fileId != null) {
                        Log.d("GmailAttach", "Descargando archivo de Drive ID: $fileId")

                        // A) Obtener metadatos (Nombre y Tipo MIME para el adjunto)
                        val driveFile = driveService.files().get(fileId)
                            .setFields("name, mimeType, size")
                            .execute()

                        val fileName = driveFile.name ?: "adjunto_drive"
                        val mimeType = driveFile.mimeType ?: "application/octet-stream"

                        // B) Descargar contenido binario
                        val outputStream = ByteArrayOutputStream()
                        driveService.files().get(fileId)
                            .executeMediaAndDownloadTo(outputStream)

                        val byteArray = outputStream.toByteArray()

                        // C) Crear parte del adjunto
                        val dataSource = ByteArrayDataSource(byteArray, mimeType)
                        val attachmentPart = MimeBodyPart()
                        attachmentPart.dataHandler = DataHandler(dataSource)
                        attachmentPart.fileName = fileName

                        multipart.addBodyPart(attachmentPart)
                        Log.d("GmailAttach", "Adjuntado: $fileName (${byteArray.size} bytes)")

                    } else {
                        // Si no es un enlace de Drive válido, lo agregamos como texto al final del correo
                        val linkPart = MimeBodyPart()
                        linkPart.setText("\n[Enlace externo adjunto: $url]\n")
                        multipart.addBodyPart(linkPart)
                    }
                } catch (e: Exception) {
                    Log.e("GmailAttach", "Error procesando adjunto: $rawString", e)
                    // En caso de error con un adjunto, no fallamos todo el envío, solo notificamos en el cuerpo
                    val errorPart = MimeBodyPart()
                    errorPart.setText("\n[Error al adjuntar archivo: $rawString]\n")
                    multipart.addBodyPart(errorPart)
                }
            }

            // Asignar contenido al mensaje
            mimeMessage.setContent(multipart)

            // 6. Codificar y Enviar
            val buffer = ByteArrayOutputStream()
            mimeMessage.writeTo(buffer)
            val rawMessageBytes = buffer.toByteArray()
            val encodedEmail = Base64.encodeToString(rawMessageBytes, Base64.URL_SAFE or Base64.NO_WRAP)

            val message = Message().setRaw(encodedEmail)
            gmailService.users().messages().send("me", message).execute()

            Log.d("GmailApiService", "Correo enviado exitosamente a $cleanTo")
            Result.success(Unit)

        } catch (e: GoogleJsonResponseException) {
            val errorDetails = e.details
            val errorMessage = errorDetails?.message ?: e.message
            Log.e("GmailApiService", "Error API Google: $errorMessage")
            Result.failure(Exception("Error Gmail API: $errorMessage"))
        } catch (e: Exception) {
            // Aquí se capturará UserRecoverableAuthIOException y se pasará al ViewModel
            Log.e("GmailApiService", "Error general", e)
            Result.failure(e)
        }
    }

    private fun extractDriveFileId(url: String): String? {
        // Patrones comunes de URL de Drive
        val patterns = listOf(
            Pattern.compile("/file/d/([^/]+)"),
            Pattern.compile("id=([^&]+)"),
            Pattern.compile("open\\?id=([^&]+)")
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