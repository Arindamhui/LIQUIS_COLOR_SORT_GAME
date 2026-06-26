package com.liquidcolorsort.ads

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.liquidcolorsort.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [AdService] coordinating Google AdMob ads and GDPR/UMP consent flow.
 * Frequency capping and retention-protection policies are centralized here.
 */
@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : AdService {

    companion object {
        /** Show an interstitial after every N level completions. */
        const val INTERSTITIAL_FREQUENCY = 3
        
        /** Levels before which NO interstitials are displayed to protect D1 user retention. */
        const val RETENTION_SAFE_LEVEL_THRESHOLD = 5
    }

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var completionCount = 0
    private var isInitialized = false

    // ── Consent & Initialization ───────────────────────────────────────────

    override fun checkConsentAndInit(activity: Activity, onComplete: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (consentInformation.canRequestAds()) {
                        initAdMob()
                    }
                    onComplete()
                }
            },
            { requestConsentError ->
                // Fallback: initialisation attempt on network or configuration issues
                if (consentInformation.canRequestAds()) {
                    initAdMob()
                }
                onComplete()
            }
        )
    }

    private fun initAdMob() {
        if (isInitialized) return
        isInitialized = true
        
        MobileAds.initialize(context) {
            preloadInterstitial()
            preloadRewarded()
        }

        if (BuildConfig.DEBUG) {
            val config = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf("EMULATOR"))
                .build()
            MobileAds.setRequestConfiguration(config)
        }
    }

    // ── Banner ─────────────────────────────────────────────────────────────

    override fun loadBanner(container: ViewGroup) {
        // If UMP consent is not ready, do not load ads to avoid compliance issues
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)
        if (!consentInformation.canRequestAds()) return

        val adView = AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.BANNER)
        }
        container.removeAllViews()
        container.addView(adView)
        adView.loadAd(buildRequest())
    }

    // ── Interstitial ───────────────────────────────────────────────────────

    override fun preloadInterstitial() {
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)
        if (!consentInformation.canRequestAds()) return

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

    override fun onLevelComplete(activity: Activity, completedLevelId: Int, onDismissed: () -> Unit) {
        // Protect D1 retention: Avoid interstitials during the first levels
        if (completedLevelId < RETENTION_SAFE_LEVEL_THRESHOLD) {
            onDismissed()
            return
        }

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

    override fun preloadRewarded() {
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)
        if (!consentInformation.canRequestAds()) return

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

    override fun showRewarded(activity: Activity, onRewarded: () -> Unit, onDismissed: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            // Ad not ready: grant reward immediately rather than hurting gameplay UX
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

    override val isRewardedReady: Boolean get() = rewardedAd != null

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun buildRequest(): AdRequest = AdRequest.Builder().build()
}
