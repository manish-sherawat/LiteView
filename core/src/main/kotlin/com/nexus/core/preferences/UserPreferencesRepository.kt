package com.nexus.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ─── Theme Mode Enum ──────────────────────────────────────────────────────────

enum class ThemeMode { LIGHT, DARK, AMOLED, SYSTEM, SEPIA, FOREST, SUNSET }

// ─── Home Screen Style Enum ───────────────────────────────────────────────────
enum class HomeStyle { APPLE_GLASSMORPHIC, MINIMAL, CLASSIC }

// ─── DataStore Extension ──────────────────────────────────────────────────────

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nexus_user_preferences"
)

// ─── User Preferences Repository ─────────────────────────────────────────────
// Single source of truth for persisted user settings.
// All reads return reactive Flows; writes are suspend functions.

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_FONT_SIZE = floatPreferencesKey("default_font_size")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val REMEMBER_POSITION = booleanPreferencesKey("remember_position")
        val DEFAULT_IS_GRID_VIEW = booleanPreferencesKey("default_is_grid_view")
        val HOME_STYLE = stringPreferencesKey("home_style")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val PERMISSION_RATIONALE_SHOWN = booleanPreferencesKey("permission_rationale_shown")
        val PERMISSION_BANNER_DISMISSED = booleanPreferencesKey("permission_banner_dismissed")
        val STARRED_URIS = stringSetPreferencesKey("starred_uris")
        val READER_THEME = stringPreferencesKey("reader_theme")
        val WORD_WRAP_ENABLED = booleanPreferencesKey("word_wrap_enabled")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
    }

    val homeStyle: Flow<HomeStyle> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.HOME_STYLE]) {
            HomeStyle.APPLE_GLASSMORPHIC.name -> HomeStyle.APPLE_GLASSMORPHIC
            HomeStyle.MINIMAL.name -> HomeStyle.MINIMAL
            HomeStyle.CLASSIC.name -> HomeStyle.CLASSIC
            else -> HomeStyle.APPLE_GLASSMORPHIC
        }
    }

    suspend fun setHomeStyle(style: HomeStyle) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOME_STYLE] = style.name
        }
    }

    /** Reactive stream of the current theme preference. */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            ThemeMode.AMOLED.name -> ThemeMode.AMOLED
            ThemeMode.SEPIA.name -> ThemeMode.SEPIA
            ThemeMode.FOREST.name -> ThemeMode.FOREST
            ThemeMode.SUNSET.name -> ThemeMode.SUNSET
            else -> ThemeMode.SYSTEM
        }
    }

    /** Reactive stream of the default reader font size (in sp). */
    val defaultFontSize: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_FONT_SIZE] ?: 14f
    }

    /** Persist a new theme mode choice. */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    /** Persist a new default font size. */
    suspend fun setDefaultFontSize(size: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_FONT_SIZE] = size.coerceIn(10f, 28f)
        }
    }

    val keepScreenAwake: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[Keys.KEEP_SCREEN_AWAKE] ?: false }
    suspend fun setKeepScreenAwake(value: Boolean) { context.dataStore.edit { prefs -> prefs[Keys.KEEP_SCREEN_AWAKE] = value } }

    val rememberReadingPosition: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[Keys.REMEMBER_POSITION] ?: true }
    suspend fun setRememberReadingPosition(value: Boolean) { context.dataStore.edit { prefs -> prefs[Keys.REMEMBER_POSITION] = value } }

    val defaultIsGridView: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[Keys.DEFAULT_IS_GRID_VIEW] ?: false }
    suspend fun setDefaultIsGridView(value: Boolean) { context.dataStore.edit { prefs -> prefs[Keys.DEFAULT_IS_GRID_VIEW] = value } }

    val sortAscending: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[Keys.SORT_ASCENDING] ?: false }
    suspend fun setSortAscending(value: Boolean) { context.dataStore.edit { prefs -> prefs[Keys.SORT_ASCENDING] = value } }

    val permissionRationaleShown: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[Keys.PERMISSION_RATIONALE_SHOWN] ?: false }
    suspend fun setPermissionRationaleShown(value: Boolean) { context.dataStore.edit { prefs -> prefs[Keys.PERMISSION_RATIONALE_SHOWN] = value } }

    val permissionBannerDismissed: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[Keys.PERMISSION_BANNER_DISMISSED] ?: false }
    suspend fun setPermissionBannerDismissed(value: Boolean) { context.dataStore.edit { prefs -> prefs[Keys.PERMISSION_BANNER_DISMISSED] = value } }

    val starredUris: Flow<Set<String>> = context.dataStore.data.map { prefs -> prefs[Keys.STARRED_URIS] ?: emptySet() }
    suspend fun setStarredUris(uris: Set<String>) { context.dataStore.edit { prefs -> prefs[Keys.STARRED_URIS] = uris } }
    suspend fun toggleStarredUri(uri: String): Boolean {
        var isStarred = false
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.STARRED_URIS] ?: emptySet()
            if (current.contains(uri)) {
                prefs[Keys.STARRED_URIS] = current - uri
                isStarred = false
            } else {
                prefs[Keys.STARRED_URIS] = current + uri
                isStarred = true
            }
        }
        return isStarred
    }

    val readerTheme: Flow<String> = context.dataStore.data.map { prefs -> prefs[Keys.READER_THEME] ?: "LIGHT" }
    suspend fun setReaderTheme(theme: String) { context.dataStore.edit { prefs -> prefs[Keys.READER_THEME] = theme } }

    val wordWrapEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[Keys.WORD_WRAP_ENABLED] ?: true }
    suspend fun setWordWrapEnabled(value: Boolean) { context.dataStore.edit { prefs -> prefs[Keys.WORD_WRAP_ENABLED] = value } }

    val hapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[Keys.HAPTIC_FEEDBACK] ?: true }
    suspend fun setHapticFeedbackEnabled(value: Boolean) { context.dataStore.edit { prefs -> prefs[Keys.HAPTIC_FEEDBACK] = value } }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[Keys.IS_FIRST_LAUNCH] ?: true }
    suspend fun completeFirstLaunch() { context.dataStore.edit { prefs -> prefs[Keys.IS_FIRST_LAUNCH] = false } }

    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            // Clear settings, keep user data like permissions, stars, recents
            prefs.remove(Keys.THEME_MODE)
            prefs.remove(Keys.DEFAULT_FONT_SIZE)
            prefs.remove(Keys.KEEP_SCREEN_AWAKE)
            prefs.remove(Keys.REMEMBER_POSITION)
            prefs.remove(Keys.DEFAULT_IS_GRID_VIEW)
            prefs.remove(Keys.HOME_STYLE)
            prefs.remove(Keys.SORT_ASCENDING)
            prefs.remove(Keys.READER_THEME)
            prefs.remove(Keys.WORD_WRAP_ENABLED)
            prefs.remove(Keys.HAPTIC_FEEDBACK)
        }
    }
}
