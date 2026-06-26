package com.liquidcolorsort.core.level

import android.content.Context
import com.liquidcolorsort.core.model.GameState
import com.liquidcolorsort.core.model.LiquidColor
import com.liquidcolorsort.core.model.Tube
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source for all game levels.
 *
 * Levels 1–50 are loaded from `assets/levels.json` (hand-authored).
 * Levels 51+ are produced by [LevelGenerator] with fixed seeds so they
 * remain reproducible across app versions.
 *
 * **Thread-safety**: All parsing and generation run on [Dispatchers.IO] /
 * [Dispatchers.Default] — callers receive results via [Flow].
 */
@Singleton
class LevelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val HAND_AUTHORED_COUNT = 50
        private const val TOTAL_LEVELS        = 100

        // Difficulty ramp for generated levels (51–100)
        private data class GenParams(
            val colorCount: Int,
            val tubeCount: Int,
            val shuffleDepth: Int,
        )

        private fun paramsForLevel(levelId: Int): GenParams = when {
            levelId <= 55  -> GenParams(colorCount = 5, tubeCount = 7,  shuffleDepth = 18)
            levelId <= 65  -> GenParams(colorCount = 6, tubeCount = 8,  shuffleDepth = 22)
            levelId <= 75  -> GenParams(colorCount = 7, tubeCount = 9,  shuffleDepth = 26)
            levelId <= 85  -> GenParams(colorCount = 8, tubeCount = 10, shuffleDepth = 30)
            levelId <= 95  -> GenParams(colorCount = 9, tubeCount = 11, shuffleDepth = 34)
            else           -> GenParams(colorCount = 10, tubeCount = 12, shuffleDepth = 38)
        }
    }

    // In-memory cache populated lazily on first access
    private var handAuthoredCache: List<Level>? = null

    /**
     * Emits a [Level] for the given [levelId].
     *
     * @throws IllegalArgumentException if [levelId] < 1.
     */
    fun getLevel(levelId: Int): Flow<Level> = flow {
        require(levelId >= 1) { "levelId must be >= 1" }
        emit(loadLevel(levelId))
    }.flowOn(Dispatchers.IO)

    /**
     * Returns the total number of available levels.
     */
    fun getTotalLevelCount(): Int = TOTAL_LEVELS

    /**
     * Returns all levels as a list, loading hand-authored levels from JSON
     * and generating the rest. Useful for the level-select screen.
     *
     * This is a suspend function rather than a Flow because the level-select
     * screen needs the full list at once.
     */
    suspend fun getAllLevelSummaries(): List<LevelSummary> = withContext(Dispatchers.IO) {
        (1..TOTAL_LEVELS).map { id ->
            val level = loadLevel(id)
            LevelSummary(id = id, difficulty = level.difficulty, colorCount = level.colorCount)
        }
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private suspend fun loadLevel(levelId: Int): Level {
        return if (levelId <= HAND_AUTHORED_COUNT) {
            getHandAuthoredLevels()[levelId - 1]
        } else {
            val p = paramsForLevel(levelId)
            LevelGenerator.generate(
                levelId      = levelId,
                baseSeed     = levelId.toLong() * 0xDEAD_BEEF,
                colorCount   = p.colorCount,
                tubeCount    = p.tubeCount,
                shuffleDepth = p.shuffleDepth,
            )
        }
    }

    private fun getHandAuthoredLevels(): List<Level> {
        handAuthoredCache?.let { return it }
        val levels = parseJson()
        handAuthoredCache = levels
        return levels
    }

    private fun parseJson(): List<Level> {
        val json = context.assets.open("levels.json")
            .bufferedReader()
            .use { it.readText() }
        val root   = JSONObject(json)
        val arr    = root.getJSONArray("levels")
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).toLevel() }
    }

    private fun JSONObject.toLevel(): Level {
        val id          = getInt("id")
        val colorCount  = getInt("colorCount")
        val tubeCount   = getInt("tubeCount")
        val capacity    = optInt("capacity", Tube.DEFAULT_CAPACITY)
        val emptyTubes  = optInt("emptyTubes", 2)
        val optimal     = optInt("optimalMoves", 0)
        val tubesArr    = getJSONArray("tubes")

        val tubes = (0 until tubesArr.length()).map { t ->
            val segArr   = tubesArr.getJSONArray(t)
            val segments = (0 until segArr.length())
                .map { LiquidColor(segArr.getInt(it)) }
                .filter { !it.isEmpty }
            Tube(segments = segments, capacity = capacity)
        }

        return Level(
            id             = id,
            colorCount     = colorCount,
            tubeCount      = tubeCount,
            emptyTubeCount = emptyTubes,
            initialState   = GameState(tubes = tubes),
            optimalMoves   = optimal,
            seed           = 0L,
        )
    }
}

/** Lightweight summary used for the level-select grid (avoids loading full GameState). */
data class LevelSummary(
    val id: Int,
    val difficulty: Difficulty,
    val colorCount: Int,
)
