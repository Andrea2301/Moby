package com.example.moby.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class LibraryViewMode { GRID, LIST, SHELF }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moby_preferences")

class PreferencesManager(private val context: Context) {

    private val IS_ABISAL_KEY = booleanPreferencesKey("is_abisal")
    private val VIEW_MODE_KEY = stringPreferencesKey("library_view_mode")
    
    // --- READER SETTINGS KEYS ---
    private val READER_THEME_KEY = stringPreferencesKey("reader_theme")
    private val FONT_SIZE_KEY = floatPreferencesKey("reader_font_size")
    private val FONT_FAMILY_KEY = stringPreferencesKey("reader_font_family")
    private val LINE_SPACING_KEY = floatPreferencesKey("reader_line_spacing")
    private val BRIGHTNESS_KEY = floatPreferencesKey("reader_brightness")

    val isAbisalFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[IS_ABISAL_KEY] ?: false }

    suspend fun setAbisal(isAbisal: Boolean) {
        context.dataStore.edit { it[IS_ABISAL_KEY] = isAbisal }
    }

    // --- READER SETTINGS FLOWS ---
    val readerThemeFlow: Flow<String> = context.dataStore.data.map { it[READER_THEME_KEY] ?: "ARRECIFE" }
    val fontSizeFlow: Flow<Float> = context.dataStore.data.map { it[FONT_SIZE_KEY] ?: 100f }
    val fontFamilyFlow: Flow<String> = context.dataStore.data.map { it[FONT_FAMILY_KEY] ?: "Serif" }
    val lineSpacingFlow: Flow<Float> = context.dataStore.data.map { it[LINE_SPACING_KEY] ?: 1.6f }
    val brightnessFlow: Flow<Float> = context.dataStore.data.map { it[BRIGHTNESS_KEY] ?: 1.0f }

    suspend fun setReaderSettings(
        theme: String? = null,
        fontSize: Float? = null,
        fontFamily: String? = null,
        lineSpacing: Float? = null,
        brightness: Float? = null
    ) {
        context.dataStore.edit { prefs ->
            theme?.let { prefs[READER_THEME_KEY] = it }
            fontSize?.let { prefs[FONT_SIZE_KEY] = it }
            fontFamily?.let { prefs[FONT_FAMILY_KEY] = it }
            lineSpacing?.let { prefs[LINE_SPACING_KEY] = it }
            brightness?.let { prefs[BRIGHTNESS_KEY] = it }
        }
    }

    val libraryViewModeFlow: Flow<LibraryViewMode> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            val modeStr = preferences[VIEW_MODE_KEY] ?: LibraryViewMode.GRID.name
            try { LibraryViewMode.valueOf(modeStr) } catch(e: Exception) { LibraryViewMode.GRID }
        }

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        context.dataStore.edit { it[VIEW_MODE_KEY] = mode.name }
    }
}
