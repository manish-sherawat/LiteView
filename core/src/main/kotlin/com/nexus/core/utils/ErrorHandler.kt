package com.nexus.core.utils

import java.io.FileNotFoundException

object ErrorHandler {
    fun getUserFriendlyMessage(e: Throwable): String {
        return when {
            e is FileNotFoundException                         -> "File not found or moved"
            e is SecurityException                            -> "Permission denied to access file"
            e is OutOfMemoryError                             -> "File is too large to open"
            e.message?.contains("not a valid OOXML", ignoreCase = true) == true ||
            e.message?.contains("not a valid OLE2", ignoreCase = true) == true ||
            e.message?.contains("No valid entries", ignoreCase = true) == true
                                                              -> "Invalid or corrupted file format"
            e.message?.contains("ZipException", ignoreCase = true) == true ||
            e.javaClass.simpleName.contains("Zip", ignoreCase = true)
                                                              -> "File appears to be corrupted (ZIP error)"
            e.javaClass.simpleName.contains("XMLStream", ignoreCase = true) ||
            e.message?.contains("XMLStream", ignoreCase = true) == true ||
            e.message?.contains("Unexpected character", ignoreCase = true) == true
                                                              -> "XML parsing failed — file may be malformed"
            else -> "Could not open: ${e.javaClass.simpleName} — ${e.message?.take(80) ?: "unknown error"}"
        }
    }
}

