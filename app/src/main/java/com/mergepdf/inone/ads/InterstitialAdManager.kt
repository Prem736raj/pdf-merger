package com.mergepdf.inone.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reusable manager for Google AdMob Interstitial Ads.
 *
 * Rules:
 * - Appears only at a natural transition after a PDF has successfully finished merging.
 * - Frequency: skips 1st successful merge, shows on 2nd, skips 3rd, shows on 4th (every 2nd merge).
 * - Only counts completed successful merges (persisted in SharedPreferences).
 * - Preloads the next interstitial in advance.
 * - Never blocks the user if an ad is not ready, fails to load, or fails to show.
 * - ZERO PDF data, filenames, passwords, or document metadata is attached to ad requests.
 */
object InterstitialAdManager {
    private const val TAG = "InterstitialAdManager"
    private const val PREFS_NAME = "pdf_merger_settings_prefs"
    private const val KEY_SUCCESSFUL_MERGE_COUNT = "key_successful_merge_count"

    private var interstitialAd: InterstitialAd? = null
    private val isLoading = AtomicBoolean(false)

    /**
     * Checks if an interstitial ad is currently loaded and ready to show.
     */
    val isAdLoaded: Boolean
        get() = interstitialAd != null

    /**
     * Returns true if an interstitial should be presented for the given successful merge count.
     * Pattern:
     * - Count 1: false
     * - Count 2: true
     * - Count 3: false
     * - Count 4: true
     * - Count N: true if count > 1 && count % 2 == 0
     */
    fun shouldShowInterstitialForCount(count: Int): Boolean {
        return count > 1 && (count % 2 == 0)
    }

    /**
     * Increments the persistent count of completed successful merges and returns the new count.
     */
    fun recordSuccessfulMerge(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_SUCCESSFUL_MERGE_COUNT, 0)
        val updated = current + 1
        prefs.edit().putInt(KEY_SUCCESSFUL_MERGE_COUNT, updated).apply()
        Log.d(TAG, "Recorded successful merge. Total count: $updated")
        return updated
    }

    /**
     * Retrieves the current persisted count of successful merges.
     */
    fun getSuccessfulMergeCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SUCCESSFUL_MERGE_COUNT, 0)
    }

    /**
     * Resets the merge count (useful for testing).
     */
    fun resetMergeCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SUCCESSFUL_MERGE_COUNT).apply()
    }

    /**
     * Preloads an interstitial ad in the background if consent allows it and no ad is ready.
     */
    fun preloadAd(context: Context) {
        if (interstitialAd != null) {
            Log.d(TAG, "Interstitial ad already cached; skipping preload")
            return
        }

        if (!ConsentManager.canRequestAds(context)) {
            Log.d(TAG, "Cannot request ads per UMP consent; skipping preload")
            return
        }

        if (!isLoading.compareAndSet(false, true)) {
            Log.d(TAG, "Interstitial ad load already in progress")
            return
        }

        val adUnitId = AdMobConfig.interstitialAdUnitId
        Log.d(TAG, "Loading interstitial ad with unit ID: $adUnitId (production=${AdMobConfig.isProduction})")

        // Build generic AdRequest with NO PDF/user telemetry
        val adRequest = AdRequest.Builder().build()

        val appContext = context.applicationContext ?: context
        InterstitialAd.load(
            appContext,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading.set(false)
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial ad successfully loaded")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoading.set(false)
                    interstitialAd = null
                    Log.w(TAG, "Interstitial ad failed to load [${loadAdError.code}]: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Shows an interstitial if the current merge count is eligible and an ad is ready.
     * ALWAYS invokes onCompleted() so the user's PDF workflow is never blocked.
     *
     * @param activity The current foreground Activity
     * @param mergeCount The successful merge count just completed
     * @param onCompleted Callback invoked when ad finishes or immediately if no ad shown
     */
    fun showInterstitialIfEligible(
        activity: Activity,
        mergeCount: Int,
        onCompleted: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onCompleted()
            return
        }

        val eligible = shouldShowInterstitialForCount(mergeCount)
        val ad = interstitialAd

        if (!eligible || ad == null) {
            Log.d(TAG, "Not showing interstitial: eligible=$eligible, adLoaded=${ad != null}")
            onCompleted()
            if (ad == null) {
                preloadAd(activity)
            }
            return
        }

        // Ad is eligible and ready: clear reference and show
        interstitialAd = null

        var hasFinished = false
        val finishOnce = {
            if (!hasFinished) {
                hasFinished = true
                onCompleted()
                preloadAd(activity)
            }
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed by user")
                finishOnce()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Interstitial failed to show [${adError.code}]: ${adError.message}")
                finishOnce()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial ad shown successfully")
            }
        }

        try {
            ad.show(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Exception showing interstitial", e)
            finishOnce()
        }
    }
}
