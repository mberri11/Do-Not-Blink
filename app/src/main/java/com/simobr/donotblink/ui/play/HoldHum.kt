package com.simobr.donotblink.ui.play

/**
 * The hold hum's frequency curve — the audio version of the ring.
 *
 * Pure Kotlin on purpose: no Android, no AudioTrack, no clock. The mapping is the only part worth
 * a unit test, and the synthesiser that consumes it lives in GameFeedback.kt.
 */
object HoldHum {

    /** Pitch at the start radius, where the ring is widest. */
    const val START_HZ = 220f

    /** Pitch at the target radius, where the line is. */
    const val TARGET_HZ = 440f

    /** Quiet. This plays under a game, not over one. */
    const val AMPLITUDE = 0.18f

    /** Attack and release ramp. Anything shorter is an audible click on the way in or out. */
    const val RAMP_MS = 12

    /**
     * Linear in contraction progress: [START_HZ] at [startRadiusDp], [TARGET_HZ] at
     * [targetRadiusDp]. A round that overshoots the line holds [TARGET_HZ] rather than climbing
     * past it — the pitch says "the line", and the line does not move.
     */
    fun frequencyHz(radiusDp: Float, startRadiusDp: Float, targetRadiusDp: Float): Float {
        val travelDp = startRadiusDp - targetRadiusDp
        if (travelDp <= 0f) return TARGET_HZ
        val progress = ((startRadiusDp - radiusDp) / travelDp).coerceIn(0f, 1f)
        return START_HZ + (TARGET_HZ - START_HZ) * progress
    }
}
