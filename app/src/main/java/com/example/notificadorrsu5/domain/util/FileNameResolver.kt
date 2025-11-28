package com.example.notificadorrsuv5.domain.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileNameResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if(columnIndex >= 0) {
                        result = cursor.getString(columnIndex)
                    }
                }
            }
        }
        return result ?: uri.path?.substringAfterLast('/') ?: "Archivo desconocido"
    }
}