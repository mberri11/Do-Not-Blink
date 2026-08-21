package com.simobr.donotblink.ads

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * The app's ONLY banner. It exists on the fail screen and nowhere else: it is created when that
 * screen enters, and destroyed the moment the player leaves it. It never appears during a run.
 */
@Composable
fun AdaptiveAnchoredBanner(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled || !AdIds.isConfigured(AdIds.banner)) return

    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp

    val adView = remember(widthDp) {
        runCatching { buildAdView(context, widthDp) }.getOrNull()
    } ?: return

    DisposableEffect(adView) {
        onDispose { runCatching { adView.destroy() } }
    }

    AndroidView(factory = { adView }, modifier = modifier.fillMaxWidth())
}

private fun buildAdView(context: Context, widthDp: Int): AdView = AdView(context).apply {
    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
    adUnitId = AdIds.banner
    loadAd(AdRequest.Builder().build())
}
