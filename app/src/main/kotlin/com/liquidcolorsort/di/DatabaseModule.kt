package com.liquidcolorsort.di

import android.content.Context
import androidx.room.Room
import com.liquidcolorsort.data.local.AppDatabase
import com.liquidcolorsort.data.local.ProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the Room database and its DAOs.
 *
 * The database is a singleton — one instance per process lifetime.
 * All migrations are enumerated here; never use destructive migration
 * in a shipping build.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "liquid_color_sort.db",
        )
        // Add future migrations here, e.g.:
        // .addMigrations(MIGRATION_1_2)
        .build()

    @Provides
    fun provideProgressDao(db: AppDatabase): ProgressDao = db.progressDao()
}
