package com.liquidcolorsort.ui.levelselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcolorsort.core.level.LevelRepository
import com.liquidcolorsort.core.level.LevelSummary
import com.liquidcolorsort.data.local.LevelProgressEntity
import com.liquidcolorsort.data.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LevelSelectViewModel @Inject constructor(
    private val levelRepo:    LevelRepository,
    private val progressRepo: ProgressRepository,
) : ViewModel() {

    data class LevelItem(
        val summary: LevelSummary,
        val progress: LevelProgressEntity,
    )

    private val _levels = MutableStateFlow<List<LevelItem>>(emptyList())
    val levels: StateFlow<List<LevelItem>> = _levels.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _isLoading.value = true
            val summaries = levelRepo.getAllLevelSummaries()

            // Observe all progress rows and merge with summaries
            progressRepo.observeAll().collect { progressList ->
                val progressMap = progressList.associateBy { it.levelId }
                _levels.value = summaries.map { s ->
                    LevelItem(
                        summary  = s,
                        progress = progressMap[s.id] ?: LevelProgressEntity(
                            levelId    = s.id,
                            isUnlocked = s.id == 1,
                        )
                    )
                }
                _isLoading.value = false
            }
        }
    }
}
