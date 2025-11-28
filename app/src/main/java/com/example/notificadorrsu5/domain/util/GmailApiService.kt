package com.example.notificadorrsuv5.domain.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
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
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.DataSource
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
            // ... (Configuración de credenciales igual que antes) ...
            val credential = GoogleAccountCredential.usingOAuth2(context, setOf(GmailScopes.GMAIL_SEND))
                .setSelectedAccount(account.account)
            val gmailService = Gmail.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Notificador RSU V5").build()

            val props = Properties()
            val session = Session.getDefaultInstance(props, null)
            val mimeMessage = MimeMessage(session)
            mimeMessage.setFrom(InternetAddress(account.email))
            mimeMessage.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(to))
            mimeMessage.subject = subject

            val multipart = MimeMultipart("mixed")
            val messageBodyPart = MimeBodyPart()

            // --- CAMBIO: Ponemos los enlaces en el texto en vez de adjuntar ---
            var finalBody = body
            if (attachmentUris.isNotEmpty()) {
                finalBody += "\n\n--- Documentos Adjuntos (Enlaces) ---\n"
                attachmentUris.forEachIndexed { index, url ->
                    finalBody += "• Archivo ${index + 1}: $url\n"
                }
            }
            messageBodyPart.setContent(finalBody, "text/plain; charset=utf-8")
            multipart.addBodyPart(messageBodyPart)
            // ----------------------------------------------------------------

            // YA NO HACEMOS EL BUCLE DE "attachmentPart" QUE DESCARGABA Y ADJUNTABA

            mimeMessage.setContent(multipart)

            val buffer = ByteArrayOutputStream()
            mimeMessage.writeTo(buffer)
            val rawMessageBytes = buffer.toByteArray()
            val encodedEmail = Base64.encodeToString(rawMessageBytes, Base64.URL_SAFE or Base64.NO_WRAP)

            val message = Message().setRaw(encodedEmail)
            gmailService.users().messages().send("me", message).execute()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun createDataSourceFromUri(uri: Uri): DataSource {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val inputStream = contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes() ?: byteArrayOf()
        inputStream?.close()
        return ByteArrayDataSource(bytes, mimeType)
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex >= 0) {
                        result = cursor.getString(columnIndex)
                    }
                }
            }
        }
        return result ?: uri.lastPathSegment ?: "unknown_file"
    }
}