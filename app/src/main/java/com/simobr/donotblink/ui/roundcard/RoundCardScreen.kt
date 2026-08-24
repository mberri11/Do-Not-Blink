package com.simobr.donotblink.ui.roundcard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalLayoutScale
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.LocalRingScale
import com.simobr.donotblink.ui.theme.scaled
import com.simobr.donotblink.ui.theme.scaledType
import com.simobr.donotblink.ui.theme.tinted
import kotlinx.coroutines.delay

object RoundCardTags {
    const val ROUND = "roundcard-round"
}

/** Card in, hold, interstitial, card out, first ring. */
const val ROUND_CARD_AD_HOLD_MS = 300L

/** No ad this round: the card holds and passes straight through. */
const val ROUND_CARD_PLAIN_HOLD_MS = 600L

private const val LABEL_TOP_DP = 340f
private const val NUMERAL_TOP_DP = 366f
private const val TICKS_TOP_DP = 506f
private const val GLOW_CENTRE_Y_DP = 440f
private const val GLOW_RADIUS_DP = 170f

/**
 * The card the player sees immediately before an interstitial and immediately after it. On rounds
 * with no ad it is just a beat before the ring.
 *
 * [showInterstitial] is handed a callback it MUST eventually call. If the ad fails, is not there,
 * or the SDK never answers, the caller's own implementation calls back immediately and the round
 * starts anyway — nothing here can strand the player.
 */
@Composable
fun RoundCardScreen(
    roundNumber: Int,
    runsTowardInterstitial: Int,
    withInterstitial: Boolean,
    showInterstitial: (onFinished: () -> Unit) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val finished by rememberUpdatedState(onFinished)
    val show by rememberUpdatedState(showInterstitial)

    LaunchedEffect(roundNumber, withInterstitial) {
        if (withInterstitial) {
            delay(ROUND_CARD_AD_HOLD_MS)
            show { finished() }
        } else {
            delay(ROUND_CARD_PLAIN_HOLD_MS)
            finished()
        }
    }

    val scale = LocalLayoutScale.current
    val ringScale = LocalRingScale.current

    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = (GLOW_RADIUS_DP * ringScale).dp.toPx()
            val centre = Offset(size.width / 2f, (GLOW_CENTRE_Y_DP * scale).dp.toPx())
            drawCircle(
                brush = Brush.radialGradient(
                    0f to palette.phosphor.copy(alpha = 0.10f),
                    1f to Color.Transparent,
                    center = centre,
                    radius = radius,
                ),
                radius = radius,
                center = centre,
            )
        }

        Text(
            text = "ROUND",
            style = DnbType.microLabel.copy(letterSpacing = 0.5.em, color = DnbColor.LabelMid),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = LABEL_TOP_DP.scaled()),
        )
        Text(
            text = roundNumber.toString().padStart(2, '0'),
            style = DnbType.roundNumeral.tinted(palette).scaledType(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = NUMERAL_TOP_DP.scaled())
                .testTag(RoundCardTags.ROUND),
        )

        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = TICKS_TOP_DP.scaled()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(3) { index ->
                val lit = index < runsTowardInterstitial.coerceIn(0, 3)
                Canvas(Modifier.width(26.dp).height(1.dp)) {
                    drawRect(color = if (lit) palette.phosphor else palette.dim)
                }
            }
        }
    }
}
