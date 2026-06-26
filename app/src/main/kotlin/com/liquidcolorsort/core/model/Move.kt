package com.liquidcolorsort.core.model

/**
 * Represents a single move from a source tube to a destination tube.
 *
 * @param fromIdx 0-based index of the source tube.
 * @param toIdx 0-based index of the destination tube.
 */
data class Move(
    val fromIdx: Int,
    val toIdx: Int,
)
