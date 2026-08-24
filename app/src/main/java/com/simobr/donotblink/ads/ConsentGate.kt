package com.simobr.donotblink.ads

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * UMP, on the splash, before any ad request exists.
 *
 * Gather, show the form if one is required, and report whether ads may be requested. Every failure
 * path resolves to "no ads" rather than an error: if consent cannot be obtained the app still works
 * completely, and ads simply never load.
 */
object ConsentGate {

    suspend fun gather(activity: Activity): Boolean {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        val updated = suspendCancellableCoroutine { continuation ->
            consentInformation.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                { if (continuation.isActive) continuation.resume(true) },
                { if (continuation.isActive) continuation.resume(false) },
            )
        }

        if (updated) {
            suspendCancellableCoroutine { continuation ->
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    // A form error is not the game's problem; carry on either way.
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }

        return runCatching { consentInformation.canRequestAds() }.getOrDefault(false)
    }

    /** Whether the privacy options entry point should be offered (EEA users who have consented). */
    fun privacyOptionsRequired(activity: Activity): Boolean = runCatching {
        UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }.getOrDefault(false)

    /**
     * Reopens the consent form from SETTINGS.
     *
     * Google's EU user consent policy requires an app that showed a consent form to offer a way
     * back into it. Having [privacyOptionsRequired] and never calling this was a live compliance
     * gap, not a missing nicety: it is an enforcement surface for the whole ads account.
     *
     * A form error is not the game's problem — [onDone] fires either way.
     */
    fun showPrivacyOptions(activity: Activity, onDone: () -> Unit = {}) {
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { onDone() }
        }.onFailure { onDone() }
    }
}
