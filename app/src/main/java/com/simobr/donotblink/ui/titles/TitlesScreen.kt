package com.simobr.donotblink.ui.titles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simobr.donotblink.game.TITLES
import com.simobr.donotblink.game.Title
import com.simobr.donotblink.game.isTitleUnlocked
import com.simobr.donotblink.game.unlockedTitleCount
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbDim
import com.simobr.donotblink.ui.common.noRippleClickable
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.PhosphorPalette
import com.simobr.donotblink.ui.theme.tinted

object TitlesTags {
    const val LIST = "titles-list"
    const val COUNT = "titles-count"
    const val REVEAL = "titles-reveal"
}

/** Mockup styles with no frozen token: derived from the nearest token, never added to DnbType. */
private val CountStyle = DnbType.microLabel.copy(fontSize = 10.sp, letterSpacing = 0.2.em)
private val ThresholdUnlockedStyle =
    DnbType.microLabel.copy(fontSize = 10.sp, letterSpacing = 0.16.em, color = DnbColor.Hot)
private val ThresholdLockedStyle =
    DnbType.microLabel.copy(fontSize = 10.sp, letterSpacing = 0.16.em, color = DnbColor.Dim)

private const val HEADER_TOP_DP = 78f
private const val LIST_TOP_DP = 140f

/**
 * Locked rows show the threshold and nothing else. The name is withheld on purpose — that is what
 * makes the reveal worth an ad.
 */
@Composable
fun TitlesScreen(
    bestStreak: Int,
    revealLockedNames: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onWatchToReveal: () -> Unit = {},
) {
    val palette = LocalPalette.current
    BackHandler(onBack = onBack)

    Box(modifier.fillMaxSize().background(DnbColor.Black)) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = HEADER_TOP_DP.dp, start = DnbDim.screenPadH, end = DnbDim.screenPadH)
        ) {
            Text("TITLES", style = DnbType.sectionTitle.tinted(palette), modifier = Modifier.align(Alignment.CenterStart))
            Text(
                text = "${unlockedTitleCount(bestStreak)} / ${TITLES.size}",
                style = CountStyle.tinted(palette),
                modifier = Modifier.align(Alignment.CenterEnd).testTag(TitlesTags.COUNT),
            )
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = LIST_TOP_DP.dp, start = DnbDim.screenPadH, end = DnbDim.screenPadH)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .testTag(TitlesTags.LIST),
        ) {
            TITLES.forEach { title ->
                TitleRow(
                    title = title,
                    unlocked = isTitleUnlocked(title, bestStreak),
                    revealLockedNames = revealLockedNames,
                    palette = palette,
                )
            }

            // One rewarded ad, one permanent unlock: the names become readable but stay dim until
            // they are actually earned. Pure curiosity — nothing about the ladder changes.
            if (!revealLockedNames && unlockedTitleCount(bestStreak) < TITLES.size) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(DnbDim.settingsRowHeight)
                        .testTag(TitlesTags.REVEAL)
                        .noRippleClickable(onClick = onWatchToReveal),
                ) {
                    Text(
                        text = "REVEAL THE LOCKED NAMES",
                        style = DnbType.settingsRow.copy(fontSize = 10.sp, letterSpacing = 0.24.em)
                            .tinted(palette),
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    Text(
                        text = "WATCH",
                        style = ThresholdLockedStyle.tinted(palette),
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleRow(
    title: Title,
    unlocked: Boolean,
    revealLockedNames: Boolean,
    palette: PhosphorPalette,
) {
    Box(Modifier.fillMaxWidth().height(DnbDim.titleRowHeight)) {
        if (unlocked) {
            Text(title.name, style = DnbType.titleRowName.tinted(palette), modifier = Modifier.align(Alignment.CenterStart))
        } else if (revealLockedNames) {
            Text(
                text = title.name,
                style = DnbType.titleRowName.copy(color = palette.dim),
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        Text(
            text = title.threshold.toString().padStart(3, '0'),
            style = (if (unlocked) ThresholdUnlockedStyle else ThresholdLockedStyle).tinted(palette),
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
