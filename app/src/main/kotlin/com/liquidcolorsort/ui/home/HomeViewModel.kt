package com.liquidcolorsort.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcolorsort.data.repository.ProgressRepository
import com.liquidcolorsort.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val progressRepo: ProgressRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    /** ID of the last played level, used for the "Continue" button. */
    val lastPlayedLevel: StateFlow<Int> = settingsRepo.lastPlayedLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    /** Total levels completed. */
    val completedCount: StateFlow<Int> = flow {
        emit(progressRepo.completedCount())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        // Ensure level 1 is always unlocked
        viewModelScope.launch { progressRepo.unlockLevel(1) }
    }
}
