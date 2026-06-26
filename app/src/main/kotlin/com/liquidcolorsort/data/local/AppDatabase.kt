package com.liquidcolorsort.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for Liquid Color Sort.
 *
 * ### Migration policy
 * All schema changes must be accompanied by a [androidx.room.migration.Migration]
 * added to the [AppModule] builder. **Never** use `fallbackToDestructiveMigration`
 * in release builds.
 *
 * Current schema version: **1**
 */
@Database(
    entities = [LevelProgressEntity::class],
    version  = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
}
