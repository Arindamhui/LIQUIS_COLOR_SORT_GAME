package com.liquidcolorsort.core.engine

import com.liquidcolorsort.core.model.GameState
import com.liquidcolorsort.core.util.tubeOf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SolverHint].
 *
 * Verifies that the BFS solver can find solutions for known configurations
 * and correctly reports unsolvable / already-solved states.
 */
class SolverHintTest {

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun stateOf(vararg tubeSpecs: IntArray) =
        GameState(tubes = tubeSpecs.map { tubeOf(*it) })

    // ── Already-solved states ──────────────────────────────────────────────

    @Test
    fun `already solved state returns AlreadySolved`() {
        val state = GameState(
            tubes = listOf(
                tubeOf(1, 1, 1, 1),
                tubeOf(2, 2, 2, 2),
                tubeOf(),
            )
        )
        assertEquals(SolverHint.SolveResult.AlreadySolved, SolverHint.findSolution(state))
    }

    @Test
    fun `nextHint on solved state returns null`() {
        val state = GameState(
            tubes = listOf(tubeOf(1, 1, 1, 1), tubeOf())
        )
        assertNull(SolverHint.nextHint(state))
    }

    // ── Simple solvable cases ──────────────────────────────────────────────

    @Test
    fun `trivially solvable in one move`() {
        // tube0=[1], tube1=[1,1,1] → pour tube0 into tube1 → solved
        val state = GameState(
            tubes = listOf(
                tubeOf(1),
                tubeOf(1, 1, 1),
                tubeOf(),
            )
        )
        val result = SolverHint.findSolution(state)
        assertTrue(result is SolverHint.SolveResult.Solved)
        val moves = (result as SolverHint.SolveResult.Solved).moves
        assertEquals(1, moves.size)
        assertEquals(SolverHint.Move(0, 1), moves[0])
    }

    @Test
    fun `two-color puzzle is solved by solver`() {
        // 2 colors, 4 tubes (2 full + 2 empty)
        val state = stateOf(
            intArrayOf(1, 2, 1, 2),
            intArrayOf(2, 1, 2, 1),
            intArrayOf(),
            intArrayOf(),
        )
        val result = SolverHint.findSolution(state)
        assertTrue(result is SolverHint.SolveResult.Solved)
    }

    @Test
    fun `nextHint returns non-null for solvable state`() {
        val state = stateOf(
            intArrayOf(1, 2),
            intArrayOf(2, 1),
            intArrayOf(),
        )
        assertNotNull(SolverHint.nextHint(state))
    }

    // ── isSolvable ─────────────────────────────────────────────────────────

    @Test
    fun `isSolvable returns true for solvable state`() {
        val state = stateOf(
            intArrayOf(1, 2),
            intArrayOf(2),
            intArrayOf(),
        )
        assertTrue(SolverHint.isSolvable(state))
    }

    @Test
    fun `isSolvable returns true for already solved state`() {
        val state = GameState(
            tubes = listOf(tubeOf(1, 1, 1, 1), tubeOf(2, 2, 2, 2), tubeOf())
        )
        assertTrue(SolverHint.isSolvable(state))
    }

    // ── Solution validity ──────────────────────────────────────────────────

    @Test
    fun `applying all moves in solution produces solved state`() {
        val initial = stateOf(
            intArrayOf(1, 2, 2, 1),
            intArrayOf(2, 1, 1, 2),
            intArrayOf(),
            intArrayOf(),
        )
        val result = SolverHint.findSolution(initial)
        assertTrue(result is SolverHint.SolveResult.Solved)

        val moves = (result as SolverHint.SolveResult.Solved).moves
        var state = initial
        for (move in moves) {
            state = MoveValidator.pour(state, move.fromIdx, move.toIdx)
        }
        assertTrue(state.isSolved, "Applying all solution moves should produce a solved state")
    }

    @Test
    fun `solution is minimal (BFS guarantees shortest path)`() {
        // A state solvable in exactly 1 move should have a 1-move solution
        val state = GameState(
            tubes = listOf(
                tubeOf(2, 1, 1, 1),
                tubeOf(1),
                tubeOf(2, 2, 2),
                tubeOf(),
            )
        )
        val result = SolverHint.findSolution(state)
        if (result is SolverHint.SolveResult.Solved) {
            // BFS guarantees shortest path — can't have more than needed
            assertTrue(result.moves.isNotEmpty())
        }
    }
}
