package com.liquidcolorsort.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcolorsort.ads.AdManager
import com.liquidcolorsort.core.engine.MoveValidator
import com.liquidcolorsort.core.engine.SolverHint
import com.liquidcolorsort.core.level.Level
import com.liquidcolorsort.core.level.LevelRepository
import com.liquidcolorsort.core.model.GameState
import com.liquidcolorsort.core.util.isDeadlocked
import com.liquidcolorsort.data.repository.ProgressRepository
import com.liquidcolorsort.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the game screen.
 *
 * ## State machine
 * ```
 *   Loading ──→ Playing ──→ Won
 *                  ↑           │
 *                  └───────────┘  (restart)
 * ```
 *
 * ## Input locking
 * [isAnimating] is set to `true` by [GameFragment] at the start of a pour
 * animation and back to `false` in the animation's `onEnd` callback. While
 * `true`, [onTubeTapped] is a no-op. This ensures the logical [GameState]
 * and the visual tube positions are always in sync.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val levelRepo:    LevelRepository,
    private val progressRepo: ProgressRepository,
    private val settingsRepo: SettingsRepository,
    private val adManager:    AdManager,
    savedState: SavedStateHandle,
) : ViewModel() {

    // ── Level ──────────────────────────────────────────────────────────────

    val levelId: Int = savedState.get<Int>("levelId") ?: 1

    private val _level = MutableStateFlow<Level?>(null)
    val level: StateFlow<Level?> = _level.asStateFlow()

    // ── Game State ─────────────────────────────────────────────────────────

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    // ── UI State ───────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /**
     * `true` while a tube pour animation is running.
     * Set/cleared by [GameFragment] — not by the ViewModel.
     * While `true`, all taps are ignored.
     */
    private val _isAnimating = MutableStateFlow(false)
    val isAnimating: StateFlow<Boolean> = _isAnimating.asStateFlow()

    private val _hint = MutableStateFlow<SolverHint.Move?>(null)
    val hint: StateFlow<SolverHint.Move?> = _hint.asStateFlow()

    val soundEnabled: StateFlow<Boolean> = settingsRepo.soundEnabled
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    // ── Init ───────────────────────────────────────────────────────────────

    init {
        loadLevel()
        adManager.preloadInterstitial()
        adManager.preloadRewarded()
    }

    private fun loadLevel() {
        viewModelScope.launch {
            _uiState.value = GameUiState.Loading
            levelRepo.getLevel(levelId)
                .catch { e -> _uiState.value = GameUiState.Error(e.message ?: "Unknown error") }
                .collect { level ->
                    _level.value = level
                    _gameState.value = level.initialState
                    _uiState.value = GameUiState.Playing
                    // Unlock this level in DB (idempotent)
                    progressRepo.unlockLevel(levelId)
                }
        }
    }

    // ── User actions ───────────────────────────────────────────────────────

    /**
     * Handles a tap on the tube at [tubeIdx].
     * No-op during animations or when the puzzle is not in [GameUiState.Playing].
     */
    fun onTubeTapped(tubeIdx: Int) {
        if (_isAnimating.value) return
        val currentState = _gameState.value ?: return
        if (_uiState.value !is GameUiState.Playing) return

        val newState = MoveValidator.handleTap(currentState, tubeIdx)
        _gameState.value = newState
        _hint.value = null  // clear any active hint highlight

        if (newState.isSolved) {
            handleWin(newState)
        } else if (newState.isDeadlocked()) {
            _uiState.value = GameUiState.Deadlocked
        }
    }

    /** Called by [GameFragment] when the animation starts. */
    fun onAnimationStarted() { _isAnimating.value = true }

    /** Called by [GameFragment] when the animation completes. */
    fun onAnimationEnded() { _isAnimating.value = false }

    fun onUndoTapped() {
        if (_isAnimating.value) return
        val current = _gameState.value ?: return
        val prev = MoveValidator.undo(current)
        if (prev != null) {
            _gameState.value = prev
            _hint.value = null
            // Restore Playing state if we were deadlocked
            if (_uiState.value is GameUiState.Deadlocked) {
                _uiState.value = GameUiState.Playing
            }
        }
    }

    /**
     * Requests a hint. The [GameFragment] is responsible for calling
     * [AdManager.showRewarded] and then invoking [grantHint] after the
     * user earns the reward.
     */
    fun onHintRequested() {
        // No-op if a hint is already showing
        if (_hint.value != null) return
        _uiState.value = GameUiState.ShowingRewardedAd
    }

    /**
     * Runs the BFS solver and sets the [hint] (the first move in the solution).
     * Should be called after the rewarded ad reward is granted.
     */
    fun grantHint() {
        val state = _gameState.value ?: return
        viewModelScope.launch {
            val move = withContext(Dispatchers.Default) { SolverHint.nextHint(state) }
            _hint.value = move
            _uiState.value = GameUiState.Playing
        }
    }

    fun onHintConsumed() { _hint.value = null }

    fun onRestartTapped() {
        _hint.value = null
        _isAnimating.value = false
        loadLevel()
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun handleWin(finalState: GameState) {
        _uiState.value = GameUiState.Won(moveCount = finalState.moveCount)
        viewModelScope.launch {
            progressRepo.recordCompletion(
                levelId      = levelId,
                moveCount    = finalState.moveCount,
                optimalMoves = _level.value?.optimalMoves ?: 0,
            )
            settingsRepo.incrementTotalMoves(finalState.moveCount.toLong())
        }
    }
}

/** All possible UI states the game screen can be in. */
sealed class GameUiState {
    data object Loading           : GameUiState()
    data object Playing           : GameUiState()
    data object Deadlocked        : GameUiState()
    data object ShowingRewardedAd : GameUiState()
    data class  Won(val moveCount: Int) : GameUiState()
    data class  Error(val message: String) : GameUiState()
}
