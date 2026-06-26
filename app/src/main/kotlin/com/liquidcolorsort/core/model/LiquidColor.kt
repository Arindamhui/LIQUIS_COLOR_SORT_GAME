package com.liquidcolorsort.core.model

/**
 * Represents a single liquid color by a stable integer identifier.
 *
 * Using an inline value class gives us type-safety over a plain [Int]
 * with zero allocation overhead at runtime.
 *
 * Color IDs are 1-indexed (1…12). ID 0 is reserved for EMPTY / no color.
 */
@JvmInline
value class LiquidColor(val id: Int) {

    init {
        require(id in 0..MAX_COLOR_ID) {
            "LiquidColor id must be in 0..$MAX_COLOR_ID, got $id"
        }
    }

    val isEmpty: Boolean get() = id == 0

    companion object {
        /** Sentinel for an "empty" slot — no liquid present. */
        val EMPTY = LiquidColor(0)

        /** Maximum number of distinct colors supported. */
        const val MAX_COLOR_ID = 12
    }
}
