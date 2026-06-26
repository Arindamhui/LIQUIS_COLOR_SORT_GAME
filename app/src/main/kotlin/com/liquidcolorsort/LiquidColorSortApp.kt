package com.liquidcolorsort

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.liquidcolorsort.util.CrashReporter

/**
 * Application entry point.
 *
 * Responsibilities:
 *  - Triggers Hilt component generation (@HiltAndroidApp).
 *  - Initialises the AdMob SDK on a background thread so the cold-start
 *    latency is not charged to the main thread.
 *  - Provides an application-scoped [CoroutineScope] for fire-and-forget
 *    work that must outlive any single ViewModel.
 */
@HiltAndroidApp
class LiquidColorSortApp : Application() {

    /** App-scoped coroutine scope backed by a [SupervisorJob] so that one
     *  failing child does not cancel siblings. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Set up global exception handler for crash tracking
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashReporter.logException(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Initialise AdMob on a background thread to avoid jank on the
        // first frame. The SDK queues ad requests until init completes.
        applicationScope.launch {
            MobileAds.initialize(this@LiquidColorSortApp)
        }

        // In debug builds, add the test-device fingerprint so that test
        // ad requests are routed to Google's test ad servers.
        if (BuildConfig.DEBUG) {
            val config = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf("EMULATOR"))
                .build()
            MobileAds.setRequestConfiguration(config)
        }
    }
}
