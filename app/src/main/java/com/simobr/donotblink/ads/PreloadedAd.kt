package com.simobr.donotblink.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One ad in hand, at all times, per surface.
 *
 * Backoff is 1s, 2s, 4s, 8s … capped at 64s, and the retry budget is FIVE PER SESSION and does not
 * reset on success. A device with no fill or no network stops being asked after five tries instead
 * of burning the player's battery for the rest of the session.
 */
class PreloadedAd<T : Any>(
    private val scope: CoroutineScope,
    private val unitId: String,
    private val load: (onLoaded: (T) -> Unit, onFailed: () -> Unit) -> Unit,
) {
    private var ad: T? = null
    private var loading = false
    private var retriesUsed = 0

    val isReady: Boolean get() = ad != null
    val retriesSpent: Int get() = retriesUsed

    fun ensureLoaded() {
        if (!AdIds.isConfigured(unitId) || ad != null || loading) return
        loading = true
        load(
            { loaded ->
                ad = loaded
                loading = false
            },
            {
                loading = false
                scheduleRetry()
            },
        )
    }

    /** Takes the ad in hand and immediately starts loading the next one. */
    fun consume(): T? {
        val held = ad
        ad = null
        if (held != null) ensureLoaded()
        return held
    }

    private fun scheduleRetry() {
        if (retriesUsed >= MAX_RETRIES_PER_SESSION) return
        val shift = retriesUsed.coerceAtMost(MAX_BACKOFF_SHIFT)
        val backoffMs = (FIRST_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
        retriesUsed += 1
        scope.launch {
            delay(backoffMs)
            ensureLoaded()
        }
    }

    companion object {
        const val MAX_RETRIES_PER_SESSION = 5
        const val FIRST_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 64_000L
        private const val MAX_BACKOFF_SHIFT = 6
    }
}
