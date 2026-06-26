package com.liquidcolorsort.billing

/**
 * Interface for the "Remove Ads" in-app purchase.
 *
 * This is a **stub** for Phase 1. The full Google Play Billing implementation
 * is a stretch goal gated behind a feature flag.
 *
 * To implement:
 *  1. Add `com.android.billingclient:billing-ktx` to dependencies.
 *  2. Create `BillingManagerImpl` implementing this interface.
 *  3. Bind via Hilt in a `BillingModule`.
 *  4. Set `SettingsDataStore.setAdsRemoved(true)` after successful purchase.
 */
interface PurchaseManager {

    /** `true` when the user has purchased "Remove Ads". */
    val isAdsRemoved: Boolean

    /**
     * Launches the in-app purchase flow for the Remove Ads product.
     *
     * @param onSuccess Called if the purchase completes successfully.
     * @param onError   Called with a human-readable error message on failure.
     */
    fun purchaseRemoveAds(onSuccess: () -> Unit, onError: (String) -> Unit)

    /**
     * Queries and restores existing purchases. Should be called on app start
     * to restore entitlements for users who reinstalled the app.
     */
    fun restorePurchases(onRestored: (Boolean) -> Unit)
}

/**
 * No-op implementation used until the full billing integration is built.
 * Always reports ads as present and purchase flow as unavailable.
 */
class StubPurchaseManager : PurchaseManager {
    override val isAdsRemoved: Boolean = false

    override fun purchaseRemoveAds(onSuccess: () -> Unit, onError: (String) -> Unit) {
        onError("In-app purchases are not yet available.")
    }

    override fun restorePurchases(onRestored: (Boolean) -> Unit) {
        onRestored(false)
    }
}
