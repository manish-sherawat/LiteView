package com.nexus.feature.dashboard.tools.protect_pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.max

object ProtectPdfEngine {

    private const val PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*?"

    /**
     * Formats bytes into a human-readable string:
     * - < 1000 B: "N B"
     * - < 1 MB: "N.1 kB"
     * - >= 1 MB: "N.2 MB"
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> String.format(Locale.US, "%.1f kB", bytes / 1000.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / 1_000_000.0)
        }
    }

    /**
     * Evaluates password strength score matching the reference specification.
     */
    fun scorePassword(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.EMPTY

        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isLowerCase() } && password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() }) score++

        return when (score.coerceIn(1, 4)) {
            1 -> PasswordStrength.WEAK
            2 -> PasswordStrength.FAIR
            3 -> PasswordStrength.GOOD
            4 -> PasswordStrength.STRONG
            else -> PasswordStrength.WEAK
        }
    }

    /**
     * Generates a cryptographically strong random password.
     */
    fun generateStrongPassword(length: Int = 16): String {
        val random = SecureRandom()
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            val idx = random.nextInt(PASSWORD_CHARS.length)
            sb.append(PASSWORD_CHARS[idx])
        }
        return sb.toString()
    }

    /**
     * Reads document metadata and renders a thumbnail of the first page.
     */
    suspend fun readPdfDocInfo(context: Context, uri: Uri): Pair<ProtectPdfDocInfo, Bitmap?> =
        withContext(Dispatchers.IO) {
            var fileName = "Document.pdf"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { fileName = it }
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            val baseName = fileName.replace(Regex("(?i)\\.pdf$"), "").ifBlank { "document" }

            var pageCount = 0
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Could not open input PDF.")

            PDDocument.load(inputStream).use { doc ->
                if (doc.isEncrypted) {
                    throw IllegalStateException("This PDF is already password-protected.")
                }
                pageCount = max(1, doc.numberOfPages)
            }

            // Render first page thumbnail
            var thumbnail: Bitmap? = null
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount > 0) {
                            renderer.openPage(0).use { page ->
                                val renderW = 450
                                val aspect = page.width.toFloat() / page.height.toFloat()
                                val renderH = (renderW / aspect).toInt().coerceAtLeast(100)

                                val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                thumbnail = bitmap
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            val docInfo = ProtectPdfDocInfo(
                uri = uri,
                name = fileName,
                sizeBytes = fileSize,
                pageCount = pageCount,
                baseName = baseName
            )

            Pair(docInfo, thumbnail)
        }

    /**
     * Encrypts the PDF with user password and owner permissions using PDFBox.
     */
    suspend fun protectPdfAndSave(
        context: Context,
        inputUri: Uri,
        userPassword: String,
        permissions: ProtectPdfPermissions,
        outputStream: OutputStream
    ): ProtectPdfResult = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(inputUri)
            ?: throw IllegalStateException("Could not open input PDF.")

        PDDocument.load(inputStream).use { doc ->
            val ap = AccessPermission().apply {
                setCanPrint(permissions.allowPrint)
                setCanExtractContent(permissions.allowCopy)
                setCanModify(permissions.allowEdit)
                setCanModifyAnnotations(permissions.allowEdit)
                setCanFillInForm(permissions.allowEdit)
                setCanAssembleDocument(permissions.allowEdit)
            }

            // A separate random owner password ensures that permissions cannot be bypassed
            val ownerPassword = generateStrongPassword(24)
            val spp = StandardProtectionPolicy(ownerPassword, userPassword, ap).apply {
                encryptionKeyLength = 128
            }

            doc.protect(spp)
            doc.save(outputStream)

            ProtectPdfResult(
                fileSizeBytes = 0L,
                pageCount = doc.numberOfPages
            )
        }
    }
}

