package com.liquidcolorsort.core.util

import com.liquidcolorsort.core.model.GameState
import com.liquidcolorsort.core.model.LiquidColor
import com.liquidcolorsort.core.model.Tube

// ── Tube extensions ───────────────────────────────────────────────────────────

/** Creates a [Tube] from a vararg of color IDs (0 = empty/skip). */
fun tubeOf(vararg colorIds: Int, capacity: Int = Tube.DEFAULT_CAPACITY): Tube =
    Tube(
        segments = colorIds.filter { it != 0 }.map { LiquidColor(it) },
        capacity = capacity,
    )

// ── GameState extensions ──────────────────────────────────────────────────────

/**
 * Returns how many stars (0–3) the player earns for completing a level in
 * [moveCount] moves, given the level's [optimalMoveCount].
 *
 * Rating:
 *  - 3 stars → within 100 % of optimal
 *  - 2 stars → within 150 % of optimal
 *  - 1 star  → completed (any move count)
 */
fun starRating(moveCount: Int, optimalMoveCount: Int): Int {
    if (optimalMoveCount <= 0) return 1
    return when {
        moveCount <= optimalMoveCount                          -> 3
        moveCount <= (optimalMoveCount * 1.5).toInt()          -> 2
        else                                                   -> 1
    }
}

/**
 * Returns `true` if this [GameState] has no legal moves left but is not solved.
 * This indicates a deadlock / unsolvable configuration.
 */
fun GameState.isDeadlocked(): Boolean {
    if (isSolved) return false
    val n = tubes.size
    for (from in 0 until n) {
        for (to in 0 until n) {
            if (from == to) continue
            if (com.liquidcolorsort.core.engine.MoveValidator.canPour(tubes[from], tubes[to])) {
                return false
            }
        }
    }
    return true
}
