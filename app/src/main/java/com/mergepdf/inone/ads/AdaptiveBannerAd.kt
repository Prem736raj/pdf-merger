package com.mergepdf.inone.ads

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Anchored Adaptive Banner Ad Composable.
 *
 * Requirements satisfied:
 * - Uses AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize (not fixed 320x50)
 * - Collapses to 0 height if ad fails to load or consent is not given (no blank space)
 * - Safely disposes and destroys the AdView when leaving Compose composition
 * - Loads only once in the factory lambda; never re-triggers ad requests on normal recompositions
 * - Attaches ZERO PDF data or sensitive telemetry to the AdRequest
 */
@Composable
fun AdaptiveBannerAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canRequestAds by ConsentManager.canRequestAdsState.collectAsStateWithLifecycle(
        initialValue = ConsentManager.canRequestAds(context)
    )
    var isAdLoaded by remember { mutableStateOf(false) }
    var adHeightDp by remember { mutableStateOf(0) }

    // If UMP consent does not allow ads, collapse immediately
    if (!canRequestAds) {
        Box(modifier = Modifier.height(0.dp))
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag("admob_adaptive_banner_container"),
        contentAlignment = Alignment.Center
    ) {
        val widthDp = if (maxWidth.value.isFinite() && maxWidth.value > 0) {
            maxWidth.value.toInt()
        } else {
            context.resources.configuration.screenWidthDp
        }.coerceAtLeast(320)
        val adSize = remember(widthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        }

        // Cache the AdView across recompositions; destroy in onDispose
        val adView = remember(adSize) {
            AdView(context).apply {
                this.adUnitId = AdMobConfig.bannerAdUnitId
                this.setAdSize(adSize)
                this.adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        super.onAdLoaded()
                        Log.d("AdaptiveBannerAd", "Banner ad loaded successfully [${adSize.width}x${adSize.height}]")
                        adHeightDp = adSize.height
                        isAdLoaded = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        super.onAdFailedToLoad(error)
                        Log.w("AdaptiveBannerAd", "Banner failed to load [${error.code}]: ${error.message}")
                        isAdLoaded = false
                    }
                }

                // Generic AdRequest with no PDF info
                val adRequest = AdRequest.Builder().build()
                loadAd(adRequest)
            }
        }

        DisposableEffect(adView) {
            onDispose {
                try {
                    adView.destroy()
                } catch (e: Exception) {
                    Log.e("AdaptiveBannerAd", "Error destroying AdView", e)
                }
            }
        }

        AnimatedVisibility(
            visible = isAdLoaded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(adHeightDp.dp)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { adView },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
