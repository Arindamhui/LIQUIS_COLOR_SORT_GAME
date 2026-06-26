package com.liquidcolorsort.data.repository

import com.liquidcolorsort.core.util.starRating
import com.liquidcolorsort.data.local.LevelProgressEntity
import com.liquidcolorsort.data.local.ProgressDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for level completion data.
 *
 * Abstracts Room DAO behind a clean domain interface so ViewModels never
 * depend on Room types directly.
 */
@Singleton
class ProgressRepository @Inject constructor(
    private val dao: ProgressDao,
) {
    /** Observe live progress for a single level. */
    fun observeLevel(levelId: Int): Flow<LevelProgressEntity?> =
        dao.observeProgress(levelId)

    /** Observe all progress rows (for level-select grid). */
    fun observeAll(): Flow<List<LevelProgressEntity>> =
        dao.observeAllProgress()

    /**
     * Records a level completion. Unlocks the next level automatically.
     *
     * @param levelId       The just-completed level.
     * @param moveCount     Moves the player used.
     * @param optimalMoves  Minimum possible moves (from [Level.optimalMoves]).
     */
    suspend fun recordCompletion(levelId: Int, moveCount: Int, optimalMoves: Int) {
        val stars = starRating(moveCount, optimalMoves)
        val now   = System.currentTimeMillis()

        // Upsert this level's result
        val existing = dao.getProgress(levelId)
        if (existing == null) {
            dao.upsert(
                LevelProgressEntity(
                    levelId     = levelId,
                    stars       = stars,
                    bestMoves   = moveCount,
                    isUnlocked  = true,
                    completedAt = now,
                )
            )
        } else {
            dao.updateBestResult(levelId, stars, moveCount, now)
        }

        // Unlock the next level
        unlockLevel(levelId + 1)
    }

    /** Ensures a level is marked as accessible (called on first app start). */
    suspend fun unlockLevel(levelId: Int) {
        val existing = dao.getProgress(levelId)
        if (existing == null) {
            dao.upsert(LevelProgressEntity(levelId = levelId, isUnlocked = true))
        } else if (!existing.isUnlocked) {
            dao.unlock(levelId)
        }
    }

    suspend fun completedCount(): Int = dao.completedCount()
}
