package com.nexus.nexusdocs

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// ─── Application Entry Point ──────────────────────────────────────────────────
// @HiltAndroidApp triggers Hilt's code generation and initializes the
// application-level dependency injection component.

@HiltAndroidApp
class NexusDocsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Future: Initialize crash reporting, analytics (none for offline app)
    }
}
