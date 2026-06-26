package com.liquidcolorsort.core.level

import com.liquidcolorsort.core.engine.SolverHint
import com.liquidcolorsort.core.model.GameState
import com.liquidcolorsort.core.model.LiquidColor
import com.liquidcolorsort.core.model.Tube
import com.liquidcolorsort.core.util.tubeOf
import kotlin.random.Random

/**
 * Deterministic puzzle level generator.
 *
 * ### Algorithm
 * 1. **Start solved**: create [colorCount] full tubes, each containing one color,
 *    plus [emptyTubeCount] empty tubes.
 * 2. **Reverse shuffle**: apply [shuffleDepth] random *legal reverse-pours*
 *    (i.e., move the top segment(s) from a random tube into a random other tube
 *    that is not empty and whose top matches). This produces a reachable,
 *    not-trivially-solved configuration.
 * 3. **Solvability check**: run [SolverHint.isSolvable] on the resulting state.
 *    Retry with an incremented seed suffix if not solvable (up to [MAX_RETRIES]).
 * 4. **Cache**: cache generated levels by their canonical key so repeated calls
 *    return the same object instantly.
 *
 * All randomness is seeded with [baseSeed] — the same seed always produces the
 * same level, satisfying the determinism contract.
 */
object LevelGenerator {

    private const val MAX_RETRIES = 10

    /** In-memory cache: canonical key → [Level]. */
    private val cache = HashMap<String, Level>()

    /**
     * Generates (or returns from cache) a level with the given parameters.
     *
     * @param levelId     Desired [Level.id].
     * @param baseSeed    Base random seed.
     * @param colorCount  Number of distinct liquid colors.
     * @param tubeCount   Total number of tubes ([colorCount] + [emptyTubeCount]).
     * @param capacity    Tube capacity in liquid segments (usually 4).
     * @param shuffleDepth How many reverse-pour operations to apply.
     *                     Higher = harder puzzle.
     * @param emptyTubeCount Number of initially empty tubes.
     */
    fun generate(
        levelId: Int,
        baseSeed: Long,
        colorCount: Int,
        tubeCount: Int,
        capacity: Int       = Tube.DEFAULT_CAPACITY,
        shuffleDepth: Int   = 20,
        emptyTubeCount: Int = 2,
    ): Level {
        val cacheKey = "$levelId:$baseSeed:$colorCount:$tubeCount:$capacity:$shuffleDepth"
        cache[cacheKey]?.let { return it }

        for (attempt in 0 until MAX_RETRIES) {
            val seed  = baseSeed + attempt.toLong()
            val state = attemptGenerate(seed, colorCount, tubeCount, capacity, shuffleDepth, emptyTubeCount)
                ?: continue

            val solveResult = SolverHint.findSolution(state)
            if (solveResult is SolverHint.SolveResult.Unsolvable) continue

            val optimalMoves = if (solveResult is SolverHint.SolveResult.Solved)
                solveResult.moves.size else 0

            val level = Level(
                id           = levelId,
                colorCount   = colorCount,
                tubeCount    = tubeCount,
                emptyTubeCount = emptyTubeCount,
                initialState = state,
                optimalMoves = optimalMoves,
                seed         = seed,
            )
            cache[cacheKey] = level
            return level
        }

        // Fallback: return a trivially solved level (should never happen in practice)
        return fallbackLevel(levelId, colorCount, tubeCount, capacity, emptyTubeCount)
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun attemptGenerate(
        seed: Long,
        colorCount: Int,
        tubeCount: Int,
        capacity: Int,
        shuffleDepth: Int,
        emptyTubeCount: Int,
    ): GameState? {
        val rng = Random(seed)

        // Step 1: Build a solved configuration
        val solvedTubes = buildList {
            for (color in 1..colorCount) {
                add(Tube(segments = List(capacity) { LiquidColor(color) }, capacity = capacity))
            }
            repeat(emptyTubeCount) { add(Tube(capacity = capacity)) }
        }.toMutableList()

        var tubes = solvedTubes.toList()

        // Step 2: Apply reverse-pours to shuffle
        repeat(shuffleDepth) {
            val candidates = buildList {
                val n = tubes.size
                for (from in 0 until n) {
                    for (to in 0 until n) {
                        if (from != to) {
                            val validKs = getValidReversePourRange(tubes[from], tubes[to])
                            if (validKs.isNotEmpty()) {
                                add(Triple(from, to, validKs))
                            }
                        }
                    }
                }
            }
            if (candidates.isEmpty()) return@repeat
            val (from, to, validKs) = candidates[rng.nextInt(candidates.size)]
            val k = validKs[rng.nextInt(validKs.size)]
            tubes = applyReversePour(tubes, from, to, k)
        }

        // Step 3: Reject trivially solved states
        val state = GameState(tubes = tubes)
        if (state.isSolved) return null

        return state
    }

    private fun getValidReversePourRange(from: Tube, to: Tube): List<Int> {
        if (from.isFull || to.isEmpty) return emptyList()
        val maxK = minOf(to.pourableCount, from.freeSlots)
        return (1..maxK).filter { k ->
            k < to.pourableCount || k == to.size
        }
    }

    private fun applyReversePour(tubes: List<Tube>, fromIdx: Int, toIdx: Int, transferCount: Int): List<Tube> {
        val from = tubes[fromIdx]
        val to   = tubes[toIdx]
        val color = to.topColor
        return tubes.toMutableList().apply {
            this[fromIdx] = from.addSegments(color, transferCount)
            this[toIdx]   = to.removeTopSegments(transferCount)
        }
    }

    private fun fallbackLevel(
        id: Int, colorCount: Int, tubeCount: Int, capacity: Int, emptyTubeCount: Int
    ): Level {
        // Return a 2-color, 1-move-away level as last resort
        val t = listOf(
            tubeOf(1, 2, 1, 2),
            tubeOf(2, 1, 2, 1),
            tubeOf(),
            tubeOf(),
        )
        return Level(
            id           = id,
            colorCount   = 2,
            tubeCount    = 4,
            emptyTubeCount = 2,
            initialState = GameState(tubes = t),
            seed         = 0L,
        )
    }
}
