package com.liquidcolorsort.util

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centrally manages all telemetry, gameplay metrics, and analytics events.
 * Surfaces developer hooks for easy logging integration (e.g., Firebase Analytics).
 */
@Singleton
class AnalyticsManager @Inject constructor() {
    companion object {
        private const val TAG = "Analytics"
    }

    /** Log event when player launches a level. */
    fun logLevelStart(levelId: Int) {
        Log.d(TAG, "EVENT: level_start | levelId = $levelId")
        // Future hook: firebaseAnalytics.logEvent("level_start", bundle)
    }

    /** Log event when player successfully completes/solves a level. */
    fun logLevelComplete(levelId: Int, moveCount: Int, optimalMoves: Int) {
        Log.d(TAG, "EVENT: level_complete | levelId = $levelId | movesCount = $moveCount | optimalMoves = $optimalMoves")
        // Future hook: firebaseAnalytics.logEvent("level_complete", bundle)
    }

    /** Log ad impressions for tracking monetization triggers. */
    fun logAdImpression(adType: String) {
        Log.d(TAG, "EVENT: ad_impression | adType = $adType")
        // Future hook: firebaseAnalytics.logEvent("ad_impression", bundle)
    }

    /** Log player settings adjustments to track engagement/retention signals. */
    fun logSettingChanged(name: String, enabled: Boolean) {
        Log.d(TAG, "EVENT: setting_changed | name = $name | enabled = $enabled")
        // Future hook: firebaseAnalytics.logEvent("setting_changed", bundle)
    }
}
