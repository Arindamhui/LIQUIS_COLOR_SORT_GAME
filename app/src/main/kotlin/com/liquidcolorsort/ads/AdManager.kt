package com.liquidcolorsort.ads

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.liquidcolorsort.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralised manager for all AdMob ad units.
 *
 * ### Ad strategy
 * - **Banner**: shown on Home and LevelSelect screens only.
 * - **Interstitial**: shown every [INTERSTITIAL_FREQUENCY] level completions.
 * - **Rewarded**: shown on user-initiated hint requests.
 *
 * All ad loading is non-blocking. Callbacks are delivered on the main thread.
 *
 * ### Ad unit IDs
 * Test IDs (safe to commit) are used in `debug` builds via [BuildConfig].
 * Real IDs must be set in `app/build.gradle.kts` for the `release` build type.
 */
@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /** Show an interstitial after every N level completions. */
        const val INTERSTITIAL_FREQUENCY = 3
    }

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var completionCount = 0

    // ── Banner ─────────────────────────────────────────────────────────────

    /**
     * Creates and loads a banner ad into [container].
     * Call this in [androidx.fragment.app.Fragment.onViewCreated].
     */
    fun loadBanner(container: ViewGroup) {
        val adView = AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.BANNER)
        }
        container.removeAllViews()
        container.addView(adView)
        adView.loadAd(buildRequest())
    }

    // ── Interstitial ───────────────────────────────────────────────────────

    /**
     * Preloads the interstitial ad. Should be called when the app starts
     * and again after the ad is dismissed.
     */
    fun preloadInterstitial() {
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            buildRequest(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    /**
     * Called each time the player completes a level. Shows an interstitial
     * every [INTERSTITIAL_FREQUENCY] completions if one is ready.
     *
     * @param activity     Foreground activity required by AdMob.
     * @param onDismissed  Called after the ad closes (or immediately if no ad).
     */
    fun onLevelComplete(activity: Activity, onDismissed: () -> Unit) {
        completionCount++
        if (completionCount % INTERSTITIAL_FREQUENCY != 0) {
            onDismissed()
            return
        }
        showInterstitial(activity, onDismissed)
    }

    private fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            onDismissed()
            preloadInterstitial()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                onDismissed()
                preloadInterstitial()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                onDismissed()
                preloadInterstitial()
            }
        }
        ad.show(activity)
    }

    // ── Rewarded ───────────────────────────────────────────────────────────

    /**
     * Preloads the rewarded ad. Should be called proactively so it is ready
     * when the user taps the hint button.
     */
    fun preloadRewarded() {
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_ID,
            buildRequest(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    /**
     * Shows the rewarded ad. The [onRewarded] callback fires only if the
     * user watches the full ad and earns the reward.
     *
     * @param activity    Foreground activity required by AdMob.
     * @param onRewarded  Called with the reward item when the user earns it.
     * @param onDismissed Called when the ad is dismissed (regardless of reward).
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit,
    ) {
        val ad = rewardedAd
        if (ad == null) {
            // Ad not ready — grant the hint anyway rather than blocking UX
            onRewarded()
            onDismissed()
            preloadRewarded()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                onDismissed()
                preloadRewarded()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                onDismissed()
                preloadRewarded()
            }
        }
        ad.show(activity) { onRewarded() }
    }

    /** Returns `true` if a rewarded ad is loaded and ready to display. */
    val isRewardedReady: Boolean get() = rewardedAd != null

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun buildRequest(): AdRequest = AdRequest.Builder().build()
}
