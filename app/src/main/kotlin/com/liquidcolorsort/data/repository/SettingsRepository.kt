package com.liquidcolorsort.data.repository

import com.liquidcolorsort.data.local.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user settings.
 *
 * Delegates directly to [SettingsDataStore]. The indirection lets us swap
 * the storage backend in tests without touching ViewModels.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val store: SettingsDataStore,
) {
    val soundEnabled: Flow<Boolean> = store.soundEnabled
    val musicEnabled: Flow<Boolean> = store.musicEnabled
    val vibrationEnabled: Flow<Boolean> = store.vibrationEnabled
    val adsRemoved: Flow<Boolean>   = store.adsRemoved
    val lastPlayedLevel: Flow<Int>  = store.lastPlayedLevel

    suspend fun setSoundEnabled(enabled: Boolean) = store.setSoundEnabled(enabled)
    suspend fun setMusicEnabled(enabled: Boolean) = store.setMusicEnabled(enabled)
    suspend fun setVibrationEnabled(enabled: Boolean) = store.setVibrationEnabled(enabled)
    suspend fun setAdsRemoved(removed: Boolean)    = store.setAdsRemoved(removed)
    suspend fun setLastPlayedLevel(id: Int)        = store.setLastPlayedLevel(id)
    suspend fun incrementTotalMoves(n: Long = 1L)  = store.incrementTotalMoves(n)
}
