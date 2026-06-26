package com.liquidcolorsort.core.level

import com.liquidcolorsort.core.model.GameState

/**
 * Represents a single puzzle level.
 *
 * @param id              1-based level number, unique across the entire level set.
 * @param colorCount      Number of distinct liquid colors used in this level.
 * @param tubeCount       Total number of tubes (filled + empty).
 * @param emptyTubeCount  Number of initially empty tubes (always ≥ 1).
 * @param initialState    The [GameState] the player starts from.
 * @param optimalMoves    Minimum number of moves needed to solve (0 = unknown).
 * @param seed            Seed used by [LevelGenerator] for generated levels;
 *                        0 for hand-authored levels.
 */
data class Level(
    val id: Int,
    val colorCount: Int,
    val tubeCount: Int,
    val emptyTubeCount: Int = 2,
    val initialState: GameState,
    val optimalMoves: Int = 0,
    val seed: Long = 0L,
) {
    init {
        require(id > 0)            { "Level id must be positive" }
        require(colorCount in 2..12) { "colorCount must be 2–12" }
        require(tubeCount >= colorCount + emptyTubeCount) {
            "tubeCount must be at least colorCount + emptyTubeCount"
        }
    }

    /** Difficulty band: Easy / Medium / Hard / Expert */
    val difficulty: Difficulty
        get() = when {
            colorCount <= 4 && tubeCount <= 6  -> Difficulty.EASY
            colorCount <= 6 && tubeCount <= 8  -> Difficulty.MEDIUM
            colorCount <= 9 && tubeCount <= 11 -> Difficulty.HARD
            else                               -> Difficulty.EXPERT
        }
}

enum class Difficulty { EASY, MEDIUM, HARD, EXPERT }

// ── Raw data class for JSON deserialization ───────────────────────────────────

/**
 * Mirrors the JSON structure in assets/levels.json.
 * Converted to [Level] by [LevelRepository].
 */
data class LevelDto(
    val id: Int,
    val colorCount: Int,
    val tubeCount: Int,
    val capacity: Int = 4,
    val emptyTubes: Int = 2,
    val optimalMoves: Int = 0,
    /**
     * Tube segment data: outer list = tubes, inner list = segments
     * ordered bottom → top. 0 means empty slot (filtered out).
     */
    val tubes: List<List<Int>>,
)
