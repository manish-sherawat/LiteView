package com.nexus.feature.dashboard.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nexus.feature.dashboard.data.NexusDatabase
import com.nexus.feature.dashboard.data.RecentDocumentDao
import com.nexus.feature.dashboard.data.DocumentBookmarkDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ─── Database DI Module ───────────────────────────────────────────────────────
// Provides the Room database and DAO as Hilt singletons.

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE document_bookmarks ADD COLUMN note TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideNexusDatabase(
        @ApplicationContext context: Context
    ): NexusDatabase = Room.databaseBuilder(
        context,
        NexusDatabase::class.java,
        "nexus_docs.db"
    ).addMigrations(MIGRATION_3_4)
     .fallbackToDestructiveMigration()
     .build()

    @Provides
    fun provideRecentDocumentDao(db: NexusDatabase): RecentDocumentDao =
        db.recentDocumentDao()

    @Provides
    fun provideDocumentBookmarkDao(db: NexusDatabase): DocumentBookmarkDao =
        db.documentBookmarkDao()
}
