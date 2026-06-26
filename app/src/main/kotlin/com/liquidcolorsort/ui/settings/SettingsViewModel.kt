package com.liquidcolorsort.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcolorsort.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val soundEnabled: StateFlow<Boolean> = settingsRepo.soundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setSoundEnabled(enabled) }
    }
}
