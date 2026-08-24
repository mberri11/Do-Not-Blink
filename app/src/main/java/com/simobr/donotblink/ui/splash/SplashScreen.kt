package com.simobr.donotblink.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simobr.donotblink.ui.brand.EyeMark
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.scaled
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/** The arc's own duration. The screen may outlive it while [SplashScreen] waits for startup work. */
const val SPLASH_SWEEP_MS = 900L

/** However slow the startup work is, the player is in the game by here. */
const val SPLASH_CEILING_MS = 3000L

private const val WORDMARK_TOP_DP = 60f
private const val MARK_SIZE_DP = 236f

/**
 * Cold start only, never on resume.
 *
 * This is not decoration: it is the window the ads init and the UMP consent check run in
 * (Stage 5 fills [startupWork]). The arc always gets its full 900ms even if that work finishes
 * first; if the work runs long the splash holds for it, to a 3000ms ceiling, then proceeds anyway.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    startupWork: suspend () -> Unit = {},
) {
    val finished by rememberUpdatedState(onFinished)
    val work by rememberUpdatedState(startupWork)

    val sweep = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (isActive && sweep.floatValue < 1f) {
            withFrameNanos { now ->
                val elapsedMs = (now - startNanos) / 1_000_000f
                sweep.floatValue = (elapsedMs / SPLASH_SWEEP_MS).coerceIn(0f, 1f)
            }
        }
    }

    LaunchedEffect(Unit) {
        // The arc always gets its full sweep; then the splash waits for whatever the startup work
        // still owes it, and gives up at the ceiling. Giving up means it stops WAITING — the work
        // itself lives on elsewhere, so a slow consent round trip does not cost the session its ads.
        withTimeoutOrNull(SPLASH_CEILING_MS) {
            delay(SPLASH_SWEEP_MS)
            work()
        }
        finished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DnbColor.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        EyeMark(
            size = MARK_SIZE_DP.scaled(),
            arcSweep = { sweep.floatValue },
            bloomAlpha = { sweep.floatValue },
        )
        Text(
            text = "DO NOT BLINK",
            style = DnbType.wordmark,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = WORDMARK_TOP_DP.scaled()),
        )
    }
}
