package com.nexus.core.util

fun String?.toUserFriendlyMessage(): String {
    if (this == null) return "Something went wrong. The file could not be opened."
    val lower = this.lowercase()
    
    if (lower.contains("permission denied") || lower.contains("enoent") || lower.contains("no such file")) {
        return "This file is no longer accessible. It may have been moved or deleted."
    }
    if (lower.contains("out of memory") || lower.contains("oom")) {
        return "This file is too large to open. Try closing other apps."
    }
    if (lower.contains("format not supported") || lower.contains("unsupported")) {
        return "This file format is not supported."
    }
    if (lower.contains("corrupt") || lower.contains("damaged")) {
        return "This file appears to be corrupted and cannot be opened."
    }
    if (lower.contains("password") || lower.contains("encrypted")) {
        return "This file is protected and cannot be opened."
    }
    
    // Fallback
    return "Something went wrong. The file could not be opened."
}
