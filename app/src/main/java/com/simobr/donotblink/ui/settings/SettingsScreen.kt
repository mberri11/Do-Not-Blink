package com.simobr.donotblink.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simobr.donotblink.BuildConfig
import com.simobr.donotblink.ui.common.noRippleClickable
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbDim
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.PhosphorPalette
import com.simobr.donotblink.ui.theme.tinted

object SettingsTags {
    const val HAPTICS = "settings-haptics"
    const val SOUND = "settings-sound"
    const val RESET = "settings-reset"
    const val RESET_YES = "settings-reset-yes"
    const val RESET_NO = "settings-reset-no"
    const val PHOSPHOR = "settings-phosphor"
    const val PRIVACY = "settings-privacy"
    const val BUILD = "settings-build"
}

/** Mockup styles with no frozen token: derived from the nearest token, never added to DnbType. */
private val ValueOnStyle = DnbType.settingsRow.copy(
    fontSize = 11.sp,
    fontWeight = FontWeight.W500,
    color = DnbColor.Hot,
)
private val ValueOffStyle = DnbType.settingsRow.copy(fontSize = 11.sp, color = DnbColor.Dim)
private val ValueQuietStyle = DnbType.settingsRow.copy(fontSize = 11.sp, color = DnbColor.Dim)
private val OpenStyle = DnbType.settingsRow.copy(fontSize = 10.sp, color = DnbColor.Dim)
private val BuildStyle = DnbType.microLabel.copy(fontSize = 9.sp, letterSpacing = 0.28.em)

private const val TITLE_TOP_DP = 78f
private const val ROWS_TOP_DP = 150f
private const val BUILD_BOTTOM_DP = 46f
private const val CROSSFADE_MS = 90

/**
 * Text and clickable boxes. No Switch, no ripple, no Material row component — the design has never
 * been made of Material.
 */
@Composable
fun SettingsScreen(
    hapticsEnabled: Boolean,
    soundEnabled: Boolean,
    bestStreak: Int,
    phosphorLabel: String,
    onSetHaptics: (Boolean) -> Unit,
    onSetSound: (Boolean) -> Unit,
    onResetBest: () -> Unit,
    onOpenPhosphor: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    BackHandler(onBack = onBack)
    var confirmingReset by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(DnbColor.Black)) {
        Text(
            text = "SETTINGS",
            style = DnbType.sectionTitle.tinted(palette),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = TITLE_TOP_DP.dp, start = DnbDim.screenPadH),
        )

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = ROWS_TOP_DP.dp, start = DnbDim.screenPadH, end = DnbDim.screenPadH),
        ) {
            SettingRow(
                label = "HAPTICS",
                tag = SettingsTags.HAPTICS,
                palette = palette,
                onClick = { onSetHaptics(!hapticsEnabled) },
            ) {
                StateWord(if (hapticsEnabled) "ON" else "OFF", hapticsEnabled, palette)
            }

            SettingRow(
                label = "SOUND",
                tag = SettingsTags.SOUND,
                palette = palette,
                onClick = { onSetSound(!soundEnabled) },
            ) {
                StateWord(if (soundEnabled) "ON" else "OFF", soundEnabled, palette)
            }

            SettingRow(
                label = if (confirmingReset) "forget it?" else "RESET BEST",
                tag = SettingsTags.RESET,
                palette = palette,
                onClick = { if (!confirmingReset) confirmingReset = true },
            ) {
                Crossfade(targetState = confirmingReset, animationSpec = tween(CROSSFADE_MS), label = "reset") { asking ->
                    if (asking) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "YES",
                                style = ValueOnStyle.tinted(palette),
                                modifier = Modifier
                                    .testTag(SettingsTags.RESET_YES)
                                    .noRippleClickable {
                                        onResetBest()
                                        confirmingReset = false
                                    },
                            )
                            Text(
                                text = "NO",
                                style = ValueOffStyle.tinted(palette),
                                modifier = Modifier
                                    .testTag(SettingsTags.RESET_NO)
                                    .noRippleClickable { confirmingReset = false },
                            )
                        }
                    } else {
                        Text(text = bestStreak.toString(), style = ValueQuietStyle.tinted(palette))
                    }
                }
            }

            SettingRow(
                label = "PHOSPHOR",
                tag = SettingsTags.PHOSPHOR,
                palette = palette,
                onClick = onOpenPhosphor,
            ) {
                Text(text = phosphorLabel, style = ValueQuietStyle.tinted(palette))
            }

            SettingRow(
                label = "PRIVACY POLICY",
                tag = SettingsTags.PRIVACY,
                palette = palette,
                onClick = onOpenPrivacyPolicy,
            ) {
                Text(text = "OPEN", style = OpenStyle.tinted(palette))
            }
        }

        Text(
            text = "BUILD ${BuildConfig.VERSION_NAME}",
            style = BuildStyle.tinted(palette),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = DnbDim.screenPadH, bottom = BUILD_BOTTOM_DP.dp)
                .testTag(SettingsTags.BUILD),
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    tag: String,
    palette: PhosphorPalette,
    onClick: () -> Unit,
    value: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(DnbDim.settingsRowHeight)
            .testTag(tag)
            .noRippleClickable(onClick = onClick),
    ) {
        Text(text = label, style = DnbType.settingsRow.tinted(palette), modifier = Modifier.align(Alignment.CenterStart))
        Box(Modifier.align(Alignment.CenterEnd)) { value() }
    }
}

@Composable
private fun StateWord(word: String, on: Boolean, palette: PhosphorPalette) {
    Crossfade(targetState = word, animationSpec = tween(CROSSFADE_MS), label = "state-word") { text ->
        Text(text = text, style = if (on) ValueOnStyle else ValueOffStyle)
    }
}
