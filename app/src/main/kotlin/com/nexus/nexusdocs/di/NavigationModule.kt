package com.nexus.nexusdocs.di

import com.nexus.core.navigation.DocumentReaderRouter
import com.nexus.nexusdocs.navigation.DocumentReaderRouterImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ─── Navigation DI Module ──────────────────────────────────────────────────────
// Binds the concrete DocumentReaderRouterImpl to the DocumentReaderRouter interface.
// Scoped as Singleton so the same NavController binding is reused across the app.

@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {

    @Binds
    @Singleton
    abstract fun bindDocumentReaderRouter(
        impl: DocumentReaderRouterImpl
    ): DocumentReaderRouter
}
