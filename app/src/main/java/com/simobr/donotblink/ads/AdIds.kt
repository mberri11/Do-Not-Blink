package com.simobr.donotblink.ads

import com.simobr.donotblink.BuildConfig

/**
 * Debug builds carry Google's public test units. Release builds carry whatever local.properties
 * holds, and local.properties is git-ignored — a real unit ID is never committed.
 *
 * A blank unit is not an error: that surface simply never loads, and the game does not notice.
 */
object AdIds {
    val banner: String = BuildConfig.AD_UNIT_BANNER
    val interstitial: String = BuildConfig.AD_UNIT_INTERSTITIAL

    fun rewarded(surface: RewardedSurface): String = when (surface) {
        RewardedSurface.CONTINUE -> BuildConfig.AD_UNIT_REWARDED_CONTINUE
        RewardedSurface.PHOSPHOR -> BuildConfig.AD_UNIT_REWARDED_PHOSPHOR
        RewardedSurface.TITLE_REVEAL -> BuildConfig.AD_UNIT_REWARDED_TITLES
    }

    fun isConfigured(unitId: String): Boolean = unitId.isNotBlank()
}
