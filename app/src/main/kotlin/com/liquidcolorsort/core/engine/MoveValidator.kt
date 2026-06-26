package com.liquidcolorsort.core.engine

import com.liquidcolorsort.core.model.GameState
import com.liquidcolorsort.core.model.Tube

/**
 * Pure-function game rule engine.
 *
 * All functions are stateless and free of Android dependencies, making them
 * trivially unit-testable with plain JUnit + no mocking.
 *
 * ### Pour Rules
 * A pour from tube *A* to tube *B* is **legal** if and only if:
 *  1. *A* is not empty.
 *  2. *B* is not full.
 *  3. Either *B* is empty, OR the top color of *B* equals the top color of *A*.
 *  4. The resulting move is not a no-op (pouring a monochrome full tube
 *     onto an empty tube produces an identical state and wastes a move).
 */
object MoveValidator {

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns `true` if pouring from [from] into [to] is a legal game move.
     */
    fun canPour(from: Tube, to: Tube): Boolean {
        if (from.isEmpty) return false
        if (to.isFull) return false
        if (to.isEmpty) {
            // Pouring a monochrome tube into an empty one is pointless.
            return !from.isMonochrome()
        }
        return from.topColor == to.topColor
    }

    /**
     * Applies a pour from tube at [fromIdx] to tube at [toIdx] and returns
     * the resulting [GameState].
     *
     * Pushes the current tube list onto [GameState.history] before mutating
     * so that [undo] can reverse the operation.
     *
     * @throws IllegalArgumentException if the move is illegal.
     */
    fun pour(state: GameState, fromIdx: Int, toIdx: Int): GameState {
        require(fromIdx != toIdx) { "Cannot pour a tube into itself" }
        val from = state.tubes[fromIdx]
        val to   = state.tubes[toIdx]
        require(canPour(from, to)) {
            "Illegal pour from tube $fromIdx (top=${from.topColor}) " +
            "to tube $toIdx (top=${to.topColor}, full=${to.isFull})"
        }

        // How many segments can actually be transferred?
        val transferCount = minOf(from.pourableCount, to.freeSlots)
        val color         = from.topColor

        val newTubes = state.tubes.toMutableList().apply {
            this[fromIdx] = from.removeTopSegments(transferCount)
            this[toIdx]   = to.addSegments(color, transferCount)
        }

        return state.copy(
            tubes        = newTubes,
            moveCount    = state.moveCount + 1,
            history      = state.history + listOf(state.tubes),
            selectedTube = null,
        )
    }

    /**
     * Reverts the most recent move by popping [GameState.history].
     *
     * Returns null if there is nothing to undo.
     */
    fun undo(state: GameState): GameState? {
        if (!state.canUndo) return null
        val previous = state.history.last()
        return state.copy(
            tubes        = previous,
            moveCount    = (state.moveCount - 1).coerceAtLeast(0),
            history      = state.history.dropLast(1),
            selectedTube = null,
        )
    }

    /**
     * Selects or deselects a tube.
     *
     * - If no tube is selected, [tubeIdx] becomes selected.
     * - If [tubeIdx] equals the currently selected tube, it is deselected.
     * - If another tube is selected and the pour is legal, the pour is
     *   executed and the result is returned.
     * - If another tube is selected but the pour is illegal, the selection
     *   moves to [tubeIdx] (re-select).
     *
     * @return Updated [GameState] (never null — worst case returns same state
     *         with updated [GameState.selectedTube]).
     */
    fun handleTap(state: GameState, tubeIdx: Int): GameState {
        val selected = state.selectedTube

        return when {
            // Nothing selected → select this tube (if it has liquid)
            selected == null -> {
                if (state.tubes[tubeIdx].isEmpty) state
                else state.copy(selectedTube = tubeIdx)
            }

            // Tapped the already-selected tube → deselect
            selected == tubeIdx -> state.copy(selectedTube = null)

            // Legal pour
            canPour(state.tubes[selected], state.tubes[tubeIdx]) ->
                pour(state, selected, tubeIdx)

            // Illegal pour — re-select the new tube if it has liquid
            else -> {
                if (state.tubes[tubeIdx].isEmpty) state.copy(selectedTube = null)
                else state.copy(selectedTube = tubeIdx)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /** Returns `true` when every segment in the tube is the same color. */
    private fun Tube.isMonochrome(): Boolean =
        segments.isNotEmpty() && segments.all { it == topColor }
}
