package com.example.notificadorrsuv5.domain.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

@Singleton
class EmailSender @Inject constructor() {

    private val username = "gabrielsebastiandiazgalvez259@gmail.com"
    private val password = "qfxa iath ncrm kflc"

    suspend fun sendEmail(to: String, subject: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        val props = Properties().apply {
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.socketFactory.port", "465")
            put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            put("mail.smtp.auth", "true")
            put("mail.smtp.port", "465")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password)
            }
        })

        try {
            Log.d("EmailSender", "Preparando para enviar correo a: $to")
            MimeMessage(session).apply {
                setFrom(InternetAddress(username))
                addRecipient(Message.RecipientType.TO, InternetAddress(to))
                setSubject(subject)
                setText(body)
            }.let {
                Transport.send(it)
            }
            Log.d("EmailSender", "Correo enviado exitosamente a: $to")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EmailSender", "Error al enviar correo a: $to", e)
            Result.failure(e)
        }
    }
}