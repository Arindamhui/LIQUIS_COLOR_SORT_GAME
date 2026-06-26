package com.liquidcolorsort.core.engine

import com.liquidcolorsort.core.model.GameState
import com.liquidcolorsort.core.model.LiquidColor
import com.liquidcolorsort.core.util.tubeOf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Exhaustive unit tests for [MoveValidator].
 *
 * These tests intentionally have no Android dependencies — they run on
 * any JVM without a device or Robolectric. Each test group is nested
 * so failures are easy to trace in CI reports.
 */
class MoveValidatorTest {

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Builds a minimal GameState from tube shorthand strings. */
    private fun stateOf(vararg tubeSpecs: IntArray): GameState {
        val tubes = tubeSpecs.map { tubeOf(*it) }
        return GameState(tubes = tubes)
    }

    // ── canPour ────────────────────────────────────────────────────────────

    @Nested
    inner class CanPourTests {

        @Test
        fun `empty source tube cannot pour`() {
            val from = tubeOf()          // empty
            val to   = tubeOf(1, 2)
            assertFalse(MoveValidator.canPour(from, to))
        }

        @Test
        fun `full destination tube cannot receive`() {
            val from = tubeOf(1)
            val to   = tubeOf(2, 2, 2, 2)  // full (capacity 4)
            assertFalse(MoveValidator.canPour(from, to))
        }

        @Test
        fun `matching top colors can pour`() {
            val from = tubeOf(1, 2)
            val to   = tubeOf(3, 2)
            assertTrue(MoveValidator.canPour(from, to))
        }

        @Test
        fun `mismatched top colors cannot pour`() {
            val from = tubeOf(1, 2)
            val to   = tubeOf(3, 4)
            assertFalse(MoveValidator.canPour(from, to))
        }

        @Test
        fun `pouring into empty tube is legal when source is not monochrome`() {
            val from = tubeOf(1, 2)   // top is 2, not all same color
            val to   = tubeOf()
            assertTrue(MoveValidator.canPour(from, to))
        }

        @Test
        fun `pouring monochrome tube into empty tube is illegal (no-op guard)`() {
            val from = tubeOf(1, 1, 1, 1)  // all-red monochrome full tube
            val to   = tubeOf()
            assertFalse(MoveValidator.canPour(from, to))
        }

        @Test
        fun `partially monochrome tube can pour into empty tube`() {
            val from = tubeOf(2, 1, 1)   // not fully monochrome
            val to   = tubeOf()
            assertTrue(MoveValidator.canPour(from, to))
        }
    }

    // ── pour ───────────────────────────────────────────────────────────────

    @Nested
    inner class PourTests {

        @Test
        fun `pour transfers matching top segments`() {
            //  from: [1, 2, 2]  →  to: [3, 2]
            //  Expected: from=[1], to=[3, 2, 2, 2] (if capacity allows 2 more)
            val state = stateOf(
                intArrayOf(1, 2, 2),
                intArrayOf(3, 2),
            )
            val result = MoveValidator.pour(state, fromIdx = 0, toIdx = 1)

            assertEquals(listOf(LiquidColor(1)), result.tubes[0].segments)
            assertEquals(
                listOf(LiquidColor(3), LiquidColor(2), LiquidColor(2), LiquidColor(2)),
                result.tubes[1].segments
            )
        }

        @Test
        fun `pour is limited by destination free slots`() {
            // from: [1, 2, 2, 2], to: [3, 2]  (to has 2 free slots)
            val state = stateOf(
                intArrayOf(1, 2, 2, 2),
                intArrayOf(3, 2),
            )
            val result = MoveValidator.pour(state, fromIdx = 0, toIdx = 1)

            // Only 2 slots free in 'to', so only 2 segments transferred
            assertEquals(listOf(LiquidColor(1), LiquidColor(2)), result.tubes[0].segments)
            assertTrue(result.tubes[1].isFull)
        }

        @Test
        fun `pour increments moveCount`() {
            val state = stateOf(
                intArrayOf(1, 2),
                intArrayOf(2),
            )
            val result = MoveValidator.pour(state, 0, 1)
            assertEquals(1, result.moveCount)
        }

        @Test
        fun `pour pushes previous tubes to history`() {
            val state = stateOf(intArrayOf(1, 2), intArrayOf(2))
            val result = MoveValidator.pour(state, 0, 1)
            assertEquals(1, result.history.size)
            assertEquals(state.tubes, result.history[0])
        }

        @Test
        fun `pour clears selectedTube`() {
            val state = stateOf(intArrayOf(1, 2), intArrayOf(2))
                .copy(selectedTube = 0)
            val result = MoveValidator.pour(state, 0, 1)
            assertNull(result.selectedTube)
        }

        @Test
        fun `pour onto itself throws`() {
            val state = stateOf(intArrayOf(1, 2), intArrayOf(2))
            assertThrows(IllegalArgumentException::class.java) {
                MoveValidator.pour(state, 0, 0)
            }
        }

        @Test
        fun `illegal pour throws`() {
            // top colors differ — should throw
            val state = stateOf(intArrayOf(1), intArrayOf(2))
            assertThrows(IllegalArgumentException::class.java) {
                MoveValidator.pour(state, 0, 1)
            }
        }
    }

    // ── undo ───────────────────────────────────────────────────────────────

    @Nested
    inner class UndoTests {

        @Test
        fun `undo with empty history returns null`() {
            val state = stateOf(intArrayOf(1, 2), intArrayOf(2))
            assertNull(MoveValidator.undo(state))
        }

        @Test
        fun `undo restores previous tube configuration`() {
            val state  = stateOf(intArrayOf(1, 2), intArrayOf(2))
            val poured = MoveValidator.pour(state, 0, 1)
            val undone = MoveValidator.undo(poured)!!

            assertEquals(state.tubes, undone.tubes)
        }

        @Test
        fun `undo decrements moveCount`() {
            val state  = stateOf(intArrayOf(1, 2), intArrayOf(2))
            val poured = MoveValidator.pour(state, 0, 1)
            val undone = MoveValidator.undo(poured)!!

            assertEquals(0, undone.moveCount)
        }

        @Test
        fun `double undo restores original state`() {
            val state   = stateOf(intArrayOf(1, 2, 3), intArrayOf(3), intArrayOf())
            val move1   = MoveValidator.pour(state, 0, 1)   // pour 3 from tube0 into tube1
            val move2   = MoveValidator.pour(move1, 0, 2)   // pour 2 from tube0 into tube2
            val undone1 = MoveValidator.undo(move2)!!
            val undone2 = MoveValidator.undo(undone1)!!

            assertEquals(state.tubes, undone2.tubes)
            assertEquals(0, undone2.moveCount)
        }
    }

    // ── handleTap ──────────────────────────────────────────────────────────

    @Nested
    inner class HandleTapTests {

        @Test
        fun `tapping empty tube when nothing selected does nothing`() {
            val state = stateOf(intArrayOf(1), intArrayOf())
            val result = MoveValidator.handleTap(state, tubeIdx = 1)
            assertNull(result.selectedTube)
        }

        @Test
        fun `tapping non-empty tube selects it`() {
            val state  = stateOf(intArrayOf(1), intArrayOf())
            val result = MoveValidator.handleTap(state, tubeIdx = 0)
            assertEquals(0, result.selectedTube)
        }

        @Test
        fun `tapping selected tube deselects it`() {
            val state  = stateOf(intArrayOf(1), intArrayOf()).copy(selectedTube = 0)
            val result = MoveValidator.handleTap(state, tubeIdx = 0)
            assertNull(result.selectedTube)
        }

        @Test
        fun `tapping valid destination executes pour`() {
            val state = stateOf(intArrayOf(1, 2), intArrayOf(2))
                .copy(selectedTube = 0)
            val result = MoveValidator.handleTap(state, tubeIdx = 1)
            // Pour should have happened
            assertEquals(1, result.moveCount)
            assertNull(result.selectedTube)
        }

        @Test
        fun `tapping invalid destination re-selects new tube`() {
            val state = stateOf(intArrayOf(1, 2), intArrayOf(3))
                .copy(selectedTube = 0)
            val result = MoveValidator.handleTap(state, tubeIdx = 1)
            // Pour not possible (top colors differ), re-select tube 1
            assertEquals(1, result.selectedTube)
            assertEquals(0, result.moveCount)  // no move occurred
        }
    }

    // ── Win detection ──────────────────────────────────────────────────────

    @Nested
    inner class WinDetectionTests {

        @Test
        fun `solved state detected correctly`() {
            val state = GameState(
                tubes = listOf(
                    tubeOf(1, 1, 1, 1),
                    tubeOf(2, 2, 2, 2),
                    tubeOf(),
                )
            )
            assertTrue(state.isSolved)
        }

        @Test
        fun `unsolved state not detected as solved`() {
            val state = GameState(
                tubes = listOf(
                    tubeOf(1, 2),
                    tubeOf(2, 1),
                )
            )
            assertFalse(state.isSolved)
        }

        @Test
        fun `pour that completes level produces solved state`() {
            // tube0=[2], tube1=[2,2,2] → pour tube0 into tube1
            val state = GameState(
                tubes = listOf(
                    tubeOf(1, 1, 1, 1),
                    tubeOf(2),
                    tubeOf(2, 2, 2),
                    tubeOf(),
                )
            )
            val result = MoveValidator.pour(state, fromIdx = 1, toIdx = 2)
            assertTrue(result.isSolved)
        }
    }

    // ── Parameterized canPour edge cases ───────────────────────────────────

    @ParameterizedTest(name = "from=[{0}] to=[{1}] → canPour={2}")
    @CsvSource(
        "''       , '1'      , false",   // empty source
        "'1,2,3,4', '5'      , false",   // full destination
        "'1,2'    , '3,2'    , true",    // matching top
        "'1,2'    , '3,4'    , false",   // mismatching top
    )
    fun `canPour edge cases`(fromSpec: String, toSpec: String, expected: Boolean) {
        fun specToTube(spec: String): com.liquidcolorsort.core.model.Tube {
            if (spec.isBlank()) return tubeOf()
            val ids = spec.split(",").map { it.trim().toInt() }
            return tubeOf(*ids.toIntArray())
        }
        assertEquals(expected, MoveValidator.canPour(specToTube(fromSpec), specToTube(toSpec)))
    }
}
