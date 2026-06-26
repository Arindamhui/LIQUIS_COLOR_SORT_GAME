package com.liquidcolorsort.di

import com.liquidcolorsort.core.level.LevelRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Application-level Hilt entry point.
 *
 * Provides access to [LevelRepository] from non-Hilt contexts (e.g., a
 * plain Android Service or background worker) without field injection.
 *
 * LevelRepository itself is @Singleton and injected directly into
 * ViewModels via constructor injection — this entry point is a safety
 * hatch for edge cases only.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun levelRepository(): LevelRepository
}
