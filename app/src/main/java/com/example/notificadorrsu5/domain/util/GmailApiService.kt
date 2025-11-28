package com.example.notificadorrsuv5.domain.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.Properties
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

            // 2. Configuración del Servicio Gmail
            val credential = GoogleAccountCredential.usingOAuth2(context, setOf(GmailScopes.GMAIL_SEND))
                .setSelectedAccount(account.account)
            val gmailService = Gmail.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Notificador RSU V5").build()

            // 3. Creación del Mensaje MIME
            val props = Properties()
            val session = Session.getDefaultInstance(props, null)
            val mimeMessage = MimeMessage(session)

            mimeMessage.setFrom(InternetAddress(account.email))
            mimeMessage.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(cleanTo))
            mimeMessage.subject = subject

            // 4. Estructura Multipart (Para Texto + Adjuntos)
            val multipart = MimeMultipart("mixed")

            // A) Parte del Texto
            val messageBodyPart = MimeBodyPart()
            messageBodyPart.setText(body, "utf-8")
            multipart.addBodyPart(messageBodyPart)

            // B) Parte de Adjuntos (Descarga y Adjunta)
            attachmentUris.forEachIndexed { index, urlString ->
                try {
                    Log.d("GmailApiService", "Descargando adjunto: $urlString")

                    // Descargamos el archivo desde la URL a un ByteArray
                    val url = URL(urlString)
                    val byteArray = url.openStream().use { it.readBytes() }

                    // Intentamos deducir el nombre del archivo de la URL
                    val fileName = urlString.substringAfterLast('/').takeIf { it.isNotEmpty() } ?: "archivo_adjunto_$index"

                    // Creamos el DataSource con los bytes descargados
                    val dataSource = ByteArrayDataSource(byteArray, "application/octet-stream")

                    val attachmentBodyPart = MimeBodyPart()
                    attachmentBodyPart.dataHandler = DataHandler(dataSource)
                    attachmentBodyPart.fileName = fileName

                    multipart.addBodyPart(attachmentBodyPart)
                    Log.d("GmailApiService", "Adjunto '$fileName' agregado correctamente.")

                } catch (e: Exception) {
                    Log.e("GmailApiService", "Error al descargar/adjuntar archivo: $urlString", e)
                    // Agregamos una nota al texto si falla un adjunto específico, para que el usuario sepa
                    val errorPart = MimeBodyPart()
                    errorPart.setText("\n[Error al adjuntar archivo: $urlString]\n")
                    multipart.addBodyPart(errorPart)
                }
            }

            // Asignamos el contenido Multipart al mensaje
            mimeMessage.setContent(multipart)

            // 5. Codificación y Envío
            val buffer = ByteArrayOutputStream()
            mimeMessage.writeTo(buffer)
            val rawMessageBytes = buffer.toByteArray()
            val encodedEmail = Base64.encodeToString(rawMessageBytes, Base64.URL_SAFE or Base64.NO_WRAP)

            val message = Message().setRaw(encodedEmail)

            gmailService.users().messages().send("me", message).execute()

            Log.d("GmailApiService", "Correo con adjuntos enviado exitosamente a $cleanTo")
            Result.success(Unit)

        } catch (e: GoogleJsonResponseException) {
            val errorDetails = e.details
            val errorMessage = errorDetails?.message ?: e.message

            Log.e("GmailApiService", "--- ERROR GMAIL API ---")
            Log.e("GmailApiService", "Código: ${e.statusCode}")
            Log.e("GmailApiService", "Mensaje: $errorMessage")

            Result.failure(Exception("Error Gmail API: $errorMessage"))

        } catch (e: Exception) {
            Log.e("GmailApiService", "Error general enviando correo", e)
            Result.failure(e)
        }
    }
}