package com.liquidcolorsort.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "liquid_color_sort_prefs")

/**
 * DataStore wrapper for app-level user preferences.
 *
 * Preferences:
 *  - [soundEnabled]   — whether SFX/music is on (default true).
 *  - [adsRemoved]     — whether the user purchased "Remove Ads" IAP (default false).
 *  - [totalMoves]     — cumulative move count across all sessions (for analytics).
 *  - [lastPlayedLevel]— last level ID the player was on (for resume prompt).
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_SOUND_ENABLED    = booleanPreferencesKey("sound_enabled")
        private val KEY_ADS_REMOVED      = booleanPreferencesKey("ads_removed")
        private val KEY_TOTAL_MOVES      = longPreferencesKey("total_moves")
        private val KEY_LAST_PLAYED_LEVEL = intPreferencesKey("last_played_level")
    }

    val soundEnabled: Flow<Boolean> = context.dataStore.data
        .catchIOException()
        .map { it[KEY_SOUND_ENABLED] ?: true }

    val adsRemoved: Flow<Boolean> = context.dataStore.data
        .catchIOException()
        .map { it[KEY_ADS_REMOVED] ?: false }

    val totalMoves: Flow<Long> = context.dataStore.data
        .catchIOException()
        .map { it[KEY_TOTAL_MOVES] ?: 0L }

    val lastPlayedLevel: Flow<Int> = context.dataStore.data
        .catchIOException()
        .map { it[KEY_LAST_PLAYED_LEVEL] ?: 1 }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOUND_ENABLED] = enabled }
    }

    suspend fun setAdsRemoved(removed: Boolean) {
        context.dataStore.edit { it[KEY_ADS_REMOVED] = removed }
    }

    suspend fun incrementTotalMoves(count: Long = 1L) {
        context.dataStore.edit {
            it[KEY_TOTAL_MOVES] = (it[KEY_TOTAL_MOVES] ?: 0L) + count
        }
    }

    suspend fun setLastPlayedLevel(levelId: Int) {
        context.dataStore.edit { it[KEY_LAST_PLAYED_LEVEL] = levelId }
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private fun Flow<Preferences>.catchIOException(): Flow<Preferences> =
        catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
}
