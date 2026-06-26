package com.liquidcolorsort.ads

import android.app.Activity
import android.view.ViewGroup

/**
 * Interface abstracting all ad-related actions (GDPR consent, banner, interstitial, rewarded).
 */
interface AdService {
    /**
     * Runs User Messaging Platform (UMP) consent flow and initialises MobileAds if allowed.
     */
    fun checkConsentAndInit(activity: Activity, onComplete: () -> Unit)

    /**
     * Loads a banner ad into the container view group.
     */
    fun loadBanner(container: ViewGroup)

    /**
     * Preloads an interstitial ad.
     */
    fun preloadInterstitial()

    /**
     * Called when a level is completed to potentially display an interstitial ad.
     */
    fun onLevelComplete(activity: Activity, completedLevelId: Int, onDismissed: () -> Unit)

    /**
     * Preloads a rewarded ad.
     */
    fun preloadRewarded()

    /**
     * Shows a rewarded ad to the user to grant helpers.
     */
    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onDismissed: () -> Unit)

    /**
     * Boolean check indicating if the rewarded ad is loaded and ready.
     */
    val isRewardedReady: Boolean
}
