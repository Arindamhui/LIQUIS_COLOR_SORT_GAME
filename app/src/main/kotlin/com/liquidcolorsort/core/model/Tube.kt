package com.liquidcolorsort.core.model

/**
 * A single test tube holding up to [capacity] liquid segments.
 *
 * The list [segments] is ordered **bottom → top**: `segments[0]` is the
 * bottom-most liquid unit and `segments[segments.size - 1]` is the topmost
 * (the one that can be poured out).
 *
 * All mutation happens at the data-class level by returning new instances —
 * [Tube] is intentionally immutable so that [GameState] can hold a full
 * copy of history without object aliasing bugs.
 *
 * @param segments Ordered list of liquid colors, bottom to top.
 * @param capacity Maximum number of segments this tube can hold.
 */
data class Tube(
    val segments: List<LiquidColor> = emptyList(),
    val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity in 1..MAX_CAPACITY) {
            "Tube capacity must be 1..$MAX_CAPACITY, got $capacity"
        }
        require(segments.size <= capacity) {
            "Tube overflow: ${segments.size} segments > capacity $capacity"
        }
    }

    // ── Derived properties ────────────────────────────────────────────────

    /** Number of liquid segments currently in the tube. */
    val size: Int get() = segments.size

    /** `true` when the tube contains no liquid. */
    val isEmpty: Boolean get() = segments.isEmpty()

    /** `true` when the tube is filled to [capacity]. */
    val isFull: Boolean get() = segments.size == capacity

    /** How many more segments can be added. */
    val freeSlots: Int get() = capacity - segments.size

    /**
     * The color of the topmost segment, or [LiquidColor.EMPTY] if the tube
     * is empty.
     */
    val topColor: LiquidColor get() = segments.lastOrNull() ?: LiquidColor.EMPTY

    /**
     * Number of contiguous same-color segments at the top of the tube.
     *
     * Example: `[R, G, G, G]` → `pourableCount == 3`
     */
    val pourableCount: Int
        get() {
            if (isEmpty) return 0
            val top = topColor
            return segments.reversed().takeWhile { it == top }.size
        }

    /**
     * `true` when the tube is solved — it is either empty or completely
     * filled with a single color.
     */
    val isSolved: Boolean
        get() = isEmpty || segments.all { it == topColor }

    // ── Mutation helpers (return new instances) ────────────────────────────

    /**
     * Returns a new [Tube] with [count] segments of [color] added to the top.
     * Throws if this would exceed [capacity].
     */
    fun addSegments(color: LiquidColor, count: Int): Tube {
        require(count >= 1) { "count must be >= 1" }
        require(segments.size + count <= capacity) {
            "Cannot add $count segments: would exceed capacity $capacity"
        }
        return copy(segments = segments + List(count) { color })
    }

    /**
     * Returns a new [Tube] with the top [count] segments removed.
     * Throws if [count] > [size].
     */
    fun removeTopSegments(count: Int): Tube {
        require(count in 1..size) {
            "Cannot remove $count segments from a tube of size $size"
        }
        return copy(segments = segments.dropLast(count))
    }

    companion object {
        /** Standard tube capacity used throughout the game. */
        const val DEFAULT_CAPACITY = 4
        const val MAX_CAPACITY = 8
    }
}
