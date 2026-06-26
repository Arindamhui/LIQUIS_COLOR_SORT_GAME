package com.liquidcolorsort.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for player progress.
 *
 * All queries are suspend functions or return [Flow] — never blocking.
 */
@Dao
interface ProgressDao {

    @Query("SELECT * FROM level_progress WHERE levelId = :levelId")
    fun observeProgress(levelId: Int): Flow<LevelProgressEntity?>

    @Query("SELECT * FROM level_progress ORDER BY levelId ASC")
    fun observeAllProgress(): Flow<List<LevelProgressEntity>>

    @Query("SELECT * FROM level_progress WHERE levelId = :levelId")
    suspend fun getProgress(levelId: Int): LevelProgressEntity?

    /** Inserts or replaces on conflict. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: LevelProgressEntity)

    /** Updates stars and bestMoves only if the new values are improvements. */
    @Query("""
        UPDATE level_progress
        SET stars     = MAX(stars, :stars),
            bestMoves = MIN(bestMoves, :bestMoves),
            completedAt = CASE WHEN completedAt = 0 THEN :completedAt ELSE completedAt END
        WHERE levelId = :levelId
    """)
    suspend fun updateBestResult(levelId: Int, stars: Int, bestMoves: Int, completedAt: Long)

    @Query("UPDATE level_progress SET isUnlocked = 1 WHERE levelId = :levelId")
    suspend fun unlock(levelId: Int)

    @Query("SELECT COUNT(*) FROM level_progress WHERE stars > 0")
    suspend fun completedCount(): Int
}
