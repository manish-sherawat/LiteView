package com.nexus.feature.dashboard.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nexus.feature.dashboard.data.NexusDatabase
import com.nexus.feature.dashboard.data.RecentDocumentDao
import com.nexus.feature.dashboard.data.DocumentBookmarkDao
import com.nexus.feature.dashboard.data.DocumentTagDao
import com.nexus.feature.dashboard.data.TagDefinitionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ─── Database DI Module ───────────────────────────────────────────────────────
// Provides the Room database and DAOs as Hilt singletons.

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE document_bookmarks ADD COLUMN note TEXT")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS document_tags (
                    documentUri TEXT NOT NULL,
                    tag TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    PRIMARY KEY(documentUri, tag)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_document_tags_tag ON document_tags(tag)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_document_tags_documentUri ON document_tags(documentUri)")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS tag_definitions (
                    name TEXT NOT NULL PRIMARY KEY,
                    colorHex TEXT NOT NULL,
                    emoji TEXT,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
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
    ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
     .fallbackToDestructiveMigration()
     .build()

    @Provides
    fun provideRecentDocumentDao(db: NexusDatabase): RecentDocumentDao =
        db.recentDocumentDao()

    @Provides
    fun provideDocumentBookmarkDao(db: NexusDatabase): DocumentBookmarkDao =
        db.documentBookmarkDao()

    @Provides
    fun provideDocumentTagDao(db: NexusDatabase): DocumentTagDao =
        db.documentTagDao()

    @Provides
    fun provideTagDefinitionDao(db: NexusDatabase): TagDefinitionDao =
        db.tagDefinitionDao()
}
