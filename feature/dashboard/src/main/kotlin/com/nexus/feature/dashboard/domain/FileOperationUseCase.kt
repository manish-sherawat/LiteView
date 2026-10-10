package com.nexus.feature.dashboard.domain

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class FileOperationUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun renameDocument(uriStr: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriStr)
            var newUriStr = uriStr
            var success = false
            
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    val newFile = File(file.parentFile, newName)
                    success = file.renameTo(newFile)
                    if (success) {
                        newUriStr = Uri.fromFile(newFile).toString()
                    }
                }
            } else if (uri.scheme == "content") {
                try {
                    val newUri = DocumentsContract.renameDocument(context.contentResolver, uri, newName)
                    if (newUri != null) {
                        success = true
                        newUriStr = newUri.toString()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FileOperationUseCase", "Content URI rename failed", e)
                }
            }
            
            if (success) {
                Result.success(newUriStr)
            } else {
                Result.failure(Exception("Rename failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePhysicalFile(uriStr: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriStr)
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    file.delete()
                } else {
                    true
                }
            } else if (uri.scheme == "content") {
                val deletedRows = context.contentResolver.delete(uri, null, null)
                deletedRows > 0
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("FileOperationUseCase", "Physical delete failed", e)
            false
        }
    }
}
