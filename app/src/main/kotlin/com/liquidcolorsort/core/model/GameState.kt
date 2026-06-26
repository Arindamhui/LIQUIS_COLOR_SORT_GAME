package com.liquidcolorsort.core.model

/**
 * Complete, immutable snapshot of the game at a single point in time.
 *
 * **Single source of truth**: the [GameViewModel] holds one [StateFlow]<[GameState]>
 * and all UI reads from it. No fragment or view mutates it directly.
 *
 * [history] stores previous tube configurations so that [MoveValidator.undo]
 * can restore the prior state without re-solving. Each entry is the list of
 * [Tube]s *before* the move that produced the current state.
 *
 * @param tubes        Current tube configurations.
 * @param moveCount    Total moves made since the level started.
 * @param history      Stack of prior tube configurations for undo support.
 * @param selectedTube Index of the currently highlighted tube, or null if
 *                     none is selected.
 */
data class GameState(
    val tubes: List<Tube>,
    val moveCount: Int = 0,
    val history: List<List<Tube>> = emptyList(),
    val redoHistory: List<List<Tube>> = emptyList(),
    val selectedTube: Int? = null,
) {
    // ── Derived state ─────────────────────────────────────────────────────

    /**
     * The puzzle is solved when every tube is either empty or fully filled
     * with a single color.
     */
    val isSolved: Boolean
        get() {
            val homogeneous = tubes.all { it.isSolved }
            if (!homogeneous) return false

            val seenColors = mutableSetOf<Int>()
            for (tube in tubes) {
                if (tube.isEmpty) continue
                val colorId = tube.topColor.id
                if (colorId in seenColors) {
                    return false
                }
                seenColors.add(colorId)
            }
            return true
        }

    /** `true` when there is at least one state to undo. */
    val canUndo: Boolean
        get() = history.isNotEmpty()

    /** `true` when there is at least one state to redo. */
    val canRedo: Boolean
        get() = redoHistory.isNotEmpty()

    /** Number of distinct (non-empty) colors currently in play. */
    val activeColorCount: Int
        get() = tubes.flatMap { it.segments }
            .filter { !it.isEmpty }
            .map { it.id }
            .distinct()
            .size

    // ── Validation ─────────────────────────────────────────────────────────

    init {
        require(tubes.isNotEmpty()) { "GameState must have at least one tube" }
    }
}

/**
 * Convenience factory: creates an initial [GameState] from a list of raw
 * segment ID lists (as stored in the level JSON).
 *
 * Each inner list uses bottom-to-top ordering. Color 0 means empty (and will
 * be filtered out).
 */
fun gameStateFrom(rawTubes: List<List<Int>>, capacity: Int = Tube.DEFAULT_CAPACITY): GameState {
    val tubes = rawTubes.map { raw ->
        Tube(
            segments = raw
                .filter { it != 0 }
                .map { LiquidColor(it) },
            capacity = capacity,
        )
    }
    return GameState(tubes = tubes)
}
