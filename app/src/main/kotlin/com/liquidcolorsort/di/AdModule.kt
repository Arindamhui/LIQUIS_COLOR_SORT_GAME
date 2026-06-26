package com.liquidcolorsort.di

import com.liquidcolorsort.ads.AdManager
import com.liquidcolorsort.ads.AdService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding [AdManager] concrete implementation to [AdService] interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AdModule {

    @Binds
    @Singleton
    abstract fun bindAdService(adManager: AdManager): AdService
}
