package com.mergepdf.inone.ads

import com.mergepdf.inone.BuildConfig

/**
 * Provides build-specific AdMob configuration.
 *
 * In debug builds:
 * - Uses Google's official Android test ad unit IDs
 * - Never requests live ads to prevent invalid traffic or account strikes
 *
 * In release builds:
 * - Uses verified production ad unit IDs
 */
object AdMobConfig {
    val bannerAdUnitId: String
        get() = BuildConfig.ADMOB_BANNER_AD_UNIT_ID

    val interstitialAdUnitId: String
        get() = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID

    val isProduction: Boolean
        get() = BuildConfig.IS_PRODUCTION_ADS
}
