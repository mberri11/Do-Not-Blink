package com.simobr.donotblink.ui.phosphor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simobr.donotblink.ui.common.noRippleClickable
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbDim
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.scaled
import com.simobr.donotblink.ui.theme.PHOSPHORS
import com.simobr.donotblink.ui.theme.PhosphorPalette
import com.simobr.donotblink.ui.theme.tinted

object PhosphorTags {
    const val ROW_PREFIX = "phosphor-row-"
    const val SWATCH_PREFIX = "phosphor-swatch-"
}

private const val TITLE_TOP_DP = 78f
private const val ROWS_TOP_DP = 150f
private const val SWATCH_DP = 10f

/**
 * Five alternates, one rewarded ad each, unlocked permanently. Nothing here touches the run: the
 * best streak is the only number this app has, and it stays earned.
 */
@Composable
fun PhosphorScreen(
    selectedId: String,
    unlockedIds: Set<String>,
    onSelect: (String) -> Unit,
    onWatchToUnlock: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    BackHandler(onBack = onBack)

    Box(modifier.fillMaxSize().background(DnbColor.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(
            text = "PHOSPHOR",
            style = DnbType.sectionTitle.tinted(palette),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = TITLE_TOP_DP.scaled(), start = DnbDim.screenPadH),
        )

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = ROWS_TOP_DP.scaled(), start = DnbDim.screenPadH, end = DnbDim.screenPadH),
        ) {
            PHOSPHORS.forEach { entry ->
                val unlocked = entry.free || entry.id in unlockedIds
                PhosphorRow(
                    entry = entry,
                    unlocked = unlocked,
                    selected = entry.id == selectedId || (selectedId.isEmpty() && entry.free),
                    onClick = { if (unlocked) onSelect(entry.id) else onWatchToUnlock(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun PhosphorRow(
    entry: PhosphorPalette,
    unlocked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(DnbDim.settingsRowHeight)
            .testTag(PhosphorTags.ROW_PREFIX + entry.id)
            .noRippleClickable(onClick = onClick),
    ) {
        // The row is printed in its own phosphor, lit if owned and dim if not: the swatch IS the row.
        Text(
            text = entry.label,
            style = DnbType.settingsRow.copy(color = if (unlocked) entry.phosphor else entry.dim),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp),
        )
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(SWATCH_DP.dp)
                .testTag(PhosphorTags.SWATCH_PREFIX + entry.id),
        ) {
            drawCircle(color = if (unlocked) entry.phosphor else entry.dim)
        }

        Crossfade(
            targetState = when {
                selected -> "ON"
                unlocked -> "SELECT"
                else -> "WATCH"
            },
            animationSpec = tween(90),
            label = "phosphor-state",
            modifier = Modifier.align(Alignment.CenterEnd),
        ) { word ->
            Text(
                text = word,
                style = DnbType.settingsRow.copy(
                    fontSize = 11.sp,
                    fontWeight = if (word == "ON") FontWeight.W500 else FontWeight.W400,
                    color = when (word) {
                        "ON" -> entry.hot
                        "SELECT" -> entry.phosphor
                        else -> entry.dim
                    },
                ),
            )
        }
    }
}
