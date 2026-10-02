package com.mergepdf.inone.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Google User Messaging Platform (UMP) consent lifecycle and Mobile Ads SDK initialization.
 *
 * Guarantees:
 * - Ads are only requested when canRequestAds() returns true
 * - SDK initialization is idempotent and thread-safe via AtomicBoolean
 * - Exposes privacy options form requirement status for Settings entry point
 * - ZERO PDF data, document contents, filenames, or user metadata is attached
 */
object ConsentManager {
    private const val TAG = "ConsentManager"

    private val isMobileAdsInitialized = AtomicBoolean(false)
    private val isGatheringConsent = AtomicBoolean(false)

    private val _canRequestAdsState = MutableStateFlow(false)
    val canRequestAdsState: StateFlow<Boolean> = _canRequestAdsState.asStateFlow()

    /**
     * Checks if ads can be requested based on UMP consent status.
     */
    fun canRequestAds(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        val allowed = try {
            UserMessagingPlatform.getConsentInformation(appContext).canRequestAds()
        } catch (e: Exception) {
            false
        }
        _canRequestAdsState.value = allowed
        return allowed
    }

    /**
     * Checks whether UMP requires a Privacy Options entry point in settings.
     */
    fun isPrivacyOptionsRequired(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        return try {
            val consentInfo = UserMessagingPlatform.getConsentInformation(appContext)
            consentInfo.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Initializes MobileAds SDK if canRequestAds allows it and not already initialized.
     */
    fun initializeMobileAdsIfAllowed(context: Context, onInitialized: () -> Unit = {}) {
        val allowed = canRequestAds(context)
        _canRequestAdsState.value = allowed
        if (allowed) {
            if (isMobileAdsInitialized.compareAndSet(false, true)) {
                Log.d(TAG, "Initializing MobileAds SDK (canRequestAds = true)")
                MobileAds.initialize(context.applicationContext) {
                    Log.d(TAG, "MobileAds SDK initialization completed")
                    _canRequestAdsState.value = true
                    onInitialized()
                }
            } else {
                onInitialized()
            }
        }
    }

    /**
     * Requests updated consent information at startup, shows form if required,
     * and initializes MobileAds when allowed.
     */
    fun requestConsentAndInitialize(activity: Activity, onConsentCompleted: () -> Unit = {}) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        if (!isGatheringConsent.compareAndSet(false, true)) {
            Log.d(TAG, "Consent request already in flight; skipping redundant call")
            return
        }

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    isGatheringConsent.set(false)
                    if (formError != null) {
                        Log.w(TAG, "Consent form error [${formError.errorCode}]: ${formError.message}")
                    }
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsIfAllowed(activity) {
                            onConsentCompleted()
                        }
                    } else {
                        onConsentCompleted()
                    }
                }
            },
            { requestConsentError ->
                isGatheringConsent.set(false)
                Log.w(TAG, "Consent info update failed [${requestConsentError.errorCode}]: ${requestConsentError.message}")
                if (consentInformation.canRequestAds()) {
                    initializeMobileAdsIfAllowed(activity) {
                        onConsentCompleted()
                    }
                } else {
                    onConsentCompleted()
                }
            }
        )

        // If ads are already allowed from a previous session, initialize immediately
        if (consentInformation.canRequestAds()) {
            initializeMobileAdsIfAllowed(activity) {
                onConsentCompleted()
            }
        }
    }

    /**
     * Shows the privacy options form from Settings when requested by the user.
     */
    fun showPrivacyOptionsForm(activity: Activity, onDismissed: (FormError?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form error: ${formError.message}")
            }
            onDismissed(formError)
        }
    }
}
