package com.mergepdf.inone.ads

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdMobIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        InterstitialAdManager.resetMergeCount(context)
    }

    @Test
    fun testInterstitialFrequencySequence() {
        // First successful merge: NO interstitial
        assertFalse("Merge 1 should NOT show interstitial", InterstitialAdManager.shouldShowInterstitialForCount(1))

        // Second successful merge: SHOW interstitial
        assertTrue("Merge 2 SHOULD show interstitial", InterstitialAdManager.shouldShowInterstitialForCount(2))

        // Third successful merge: NO interstitial
        assertFalse("Merge 3 should NOT show interstitial", InterstitialAdManager.shouldShowInterstitialForCount(3))

        // Fourth successful merge: SHOW interstitial
        assertTrue("Merge 4 SHOULD show interstitial", InterstitialAdManager.shouldShowInterstitialForCount(4))

        // Fifth: NO, Sixth: SHOW, Seventh: NO, Eighth: SHOW
        assertFalse("Merge 5 should NOT show interstitial", InterstitialAdManager.shouldShowInterstitialForCount(5))
        assertTrue("Merge 6 SHOULD show interstitial", InterstitialAdManager.shouldShowInterstitialForCount(6))
        assertFalse("Merge 7 should NOT show interstitial", InterstitialAdManager.shouldShowInterstitialForCount(7))
        assertTrue("Merge 8 SHOULD show interstitial", InterstitialAdManager.shouldShowInterstitialForCount(8))
    }

    @Test
    fun testRecordSuccessfulMergePersistence() {
        assertEquals(0, InterstitialAdManager.getSuccessfulMergeCount(context))

        val count1 = InterstitialAdManager.recordSuccessfulMerge(context)
        assertEquals(1, count1)
        assertEquals(1, InterstitialAdManager.getSuccessfulMergeCount(context))
        assertFalse(InterstitialAdManager.shouldShowInterstitialForCount(count1))

        val count2 = InterstitialAdManager.recordSuccessfulMerge(context)
        assertEquals(2, count2)
        assertEquals(2, InterstitialAdManager.getSuccessfulMergeCount(context))
        assertTrue(InterstitialAdManager.shouldShowInterstitialForCount(count2))

        val count3 = InterstitialAdManager.recordSuccessfulMerge(context)
        assertEquals(3, count3)
        assertEquals(3, InterstitialAdManager.getSuccessfulMergeCount(context))
        assertFalse(InterstitialAdManager.shouldShowInterstitialForCount(count3))

        val count4 = InterstitialAdManager.recordSuccessfulMerge(context)
        assertEquals(4, count4)
        assertEquals(4, InterstitialAdManager.getSuccessfulMergeCount(context))
        assertTrue(InterstitialAdManager.shouldShowInterstitialForCount(count4))
    }

    @Test
    fun testDebugAdUnitIdsUseGoogleTestIds() {
        // Debug build must strictly resolve Google's official Android test ad unit IDs
        assertEquals("ca-app-pub-3940256099942544/9214589741", AdMobConfig.bannerAdUnitId)
        assertEquals("ca-app-pub-3940256099942544/1033173712", AdMobConfig.interstitialAdUnitId)
        assertFalse("Debug builds must never use production ads flag", AdMobConfig.isProduction)
    }

    @Test
    fun testFailSafeShowInterstitialWhenNotLoadedDoesNotBlock() {
        var completed = false
        val activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        // Simulating when interstitial is not loaded
        InterstitialAdManager.showInterstitialIfEligible(
            activity = activity,
            mergeCount = 2
        ) {
            completed = true
        }

        assertTrue("onCompleted must be invoked even when ad is not loaded", completed)
    }
}
