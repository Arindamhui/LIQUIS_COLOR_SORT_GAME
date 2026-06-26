package com.liquidcolorsort.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the player's best result for a single puzzle level.
 *
 * @param levelId   The level's unique ID (1-based).
 * @param stars     Stars earned (1–3). 0 = never completed.
 * @param bestMoves Fewest moves the player has completed this level in.
 * @param isUnlocked Whether the level is accessible.
 * @param completedAt Unix timestamp (ms) of the first completion, or 0.
 */
@Entity(tableName = "level_progress")
data class LevelProgressEntity(
    @PrimaryKey val levelId: Int,
    val stars: Int = 0,
    val bestMoves: Int = Int.MAX_VALUE,
    val isUnlocked: Boolean = false,
    val completedAt: Long = 0L,
)
