package com.liquidcolorsort.core.engine

import com.liquidcolorsort.core.model.GameState
import com.liquidcolorsort.core.model.Tube

/**
 * BFS-based hint and solvability checker.
 *
 * Uses breadth-first search over game states to find the shortest solution.
 * The search is bounded by [MAX_STATES] to prevent OOM on degenerate inputs.
 *
 * This class is Android-free and runs on any JVM — it is designed to execute
 * on a background [kotlinx.coroutines.Dispatchers.Default] thread from the
 * [com.liquidcolorsort.ui.game.GameViewModel].
 */
object SolverHint {

    /** Safety cap on the number of states explored per hint request. */
    private const val MAX_STATES = 50_000

    /**
     * Represents a single pour move (source index → destination index).
     */
    data class Move(val fromIdx: Int, val toIdx: Int)

    /**
     * Result of a [findSolution] call.
     */
    sealed class SolveResult {
        /** A solution path was found. [moves] is the sequence of moves from
         *  the *current* state to the solved state. */
        data class Solved(val moves: List<Move>) : SolveResult()

        /** No solution could be found within [MAX_STATES] explored states. */
        data object Unsolvable : SolveResult()

        /** The state is already solved. */
        data object AlreadySolved : SolveResult()
    }

    /**
     * Returns the next best [Move] to make from [state], or null if the
     * puzzle is already solved or no solution exists within the search cap.
     */
    fun nextHint(state: GameState): Move? {
        return when (val result = findSolution(state)) {
            is SolveResult.Solved       -> result.moves.firstOrNull()
            SolveResult.AlreadySolved   -> null
            SolveResult.Unsolvable      -> null
        }
    }

    /**
     * Runs a BFS to find a solution for [initialState].
     *
     * Returns a [SolveResult] describing whether a solution was found and
     * what moves it contains.
     */
    fun findSolution(initialState: GameState): SolveResult {
        if (initialState.isSolved) return SolveResult.AlreadySolved

        // BFS: queue holds (current tube list, path of moves taken)
        val queue    = ArrayDeque<Pair<List<Tube>, List<Move>>>()
        val visited  = HashSet<List<Tube>>()

        queue.addLast(initialState.tubes to emptyList())
        visited.add(initialState.tubes)

        var exploredCount = 0

        while (queue.isNotEmpty()) {
            if (exploredCount++ > MAX_STATES) return SolveResult.Unsolvable

            val (tubes, path) = queue.removeFirst()
            val tubeCount     = tubes.size

            for (from in 0 until tubeCount) {
                for (to in 0 until tubeCount) {
                    if (from == to) continue
                    if (!MoveValidator.canPour(tubes[from], tubes[to])) continue

                    // Simulate the pour
                    val simState = GameState(tubes = tubes)
                    val nextState = MoveValidator.pour(simState, from, to)

                    if (nextState.tubes in visited) continue
                    visited.add(nextState.tubes)

                    val newPath = path + Move(from, to)

                    if (nextState.isSolved) return SolveResult.Solved(newPath)

                    queue.addLast(nextState.tubes to newPath)
                }
            }
        }

        return SolveResult.Unsolvable
    }

    /**
     * Checks whether [state] is solvable from its current configuration.
     * Useful for validating generated levels.
     */
    fun isSolvable(state: GameState): Boolean =
        findSolution(state) != SolveResult.Unsolvable

}
