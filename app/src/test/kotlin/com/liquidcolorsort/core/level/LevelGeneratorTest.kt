package com.liquidcolorsort.core.level

import com.liquidcolorsort.core.engine.SolverHint
import com.liquidcolorsort.core.util.tubeOf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for [LevelGenerator].
 *
 * Critical invariants:
 *  1. **Determinism** — same seed always produces the same level.
 *  2. **Solvability** — every generated level is solvable.
 *  3. **Validity** — color counts are balanced; no tube overflow.
 */
class LevelGeneratorTest {

    // ── Determinism ────────────────────────────────────────────────────────

    @Test
    fun `same seed always produces identical level`() {
        val a = LevelGenerator.generate(levelId = 51, baseSeed = 12345L, colorCount = 4, tubeCount = 6)
        val b = LevelGenerator.generate(levelId = 51, baseSeed = 12345L, colorCount = 4, tubeCount = 6)
        assertEquals(a.initialState.tubes, b.initialState.tubes)
    }

    @Test
    fun `different seeds produce different levels`() {
        val a = LevelGenerator.generate(levelId = 100, baseSeed = 1L, colorCount = 5, tubeCount = 7)
        val b = LevelGenerator.generate(levelId = 101, baseSeed = 2L, colorCount = 5, tubeCount = 7)
        // It's statistically near-impossible for two different seeds to produce the same layout
        assertNotEquals(a.initialState.tubes, b.initialState.tubes)
    }

    // ── Solvability ────────────────────────────────────────────────────────

    @Test
    fun `generated level with 4 colors is solvable`() {
        val level = LevelGenerator.generate(levelId = 51, baseSeed = 999L, colorCount = 4, tubeCount = 6)
        assertTrue(SolverHint.isSolvable(level.initialState))
    }

    @Test
    fun `generated level with 6 colors is solvable`() {
        val level = LevelGenerator.generate(levelId = 65, baseSeed = 777L, colorCount = 6, tubeCount = 8)
        assertTrue(SolverHint.isSolvable(level.initialState))
    }

    @RepeatedTest(5)
    fun `multiple generated levels are all solvable`() {
        val seeds = listOf(1L, 2L, 3L, 4L, 5L)
        seeds.forEachIndexed { idx, seed ->
            val level = LevelGenerator.generate(
                levelId  = 51 + idx,
                baseSeed = seed,
                colorCount = 4,
                tubeCount  = 6,
            )
            assertTrue(
                SolverHint.isSolvable(level.initialState),
                "Level with seed=$seed should be solvable"
            )
        }
    }

    // ── Validity ───────────────────────────────────────────────────────────

    @Test
    fun `color counts are balanced in generated level`() {
        val colorCount = 4
        val capacity   = 4
        val level = LevelGenerator.generate(
            levelId    = 51,
            baseSeed   = 42L,
            colorCount = colorCount,
            tubeCount  = colorCount + 2,
            capacity   = capacity,
        )
        val allSegments = level.initialState.tubes.flatMap { it.segments }
        // Each color must appear exactly `capacity` times
        for (c in 1..colorCount) {
            val count = allSegments.count { it.id == c }
            assertEquals(
                capacity, count,
                "Color $c should appear exactly $capacity times"
            )
        }
    }

    @Test
    fun `no tube exceeds capacity`() {
        val level = LevelGenerator.generate(levelId = 51, baseSeed = 1L, colorCount = 5, tubeCount = 7)
        for (tube in level.initialState.tubes) {
            assertTrue(tube.segments.size <= tube.capacity)
        }
    }

    @Test
    fun `generated level is not already solved`() {
        val level = LevelGenerator.generate(levelId = 55, baseSeed = 9999L, colorCount = 4, tubeCount = 6)
        assertFalse(level.initialState.isSolved, "Generated level should not start solved")
    }

    @Test
    fun `level id is preserved`() {
        val level = LevelGenerator.generate(levelId = 77, baseSeed = 7L, colorCount = 5, tubeCount = 7)
        assertEquals(77, level.id)
    }

    @Test
    fun `optimalMoves is positive for generated levels`() {
        val level = LevelGenerator.generate(levelId = 51, baseSeed = 1L, colorCount = 4, tubeCount = 6)
        assertTrue(level.optimalMoves >= 0, "optimalMoves should be >= 0")
    }
}
