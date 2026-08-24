package com.simobr.donotblink.ui.licences

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simobr.donotblink.ui.common.noRippleClickable
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbDim
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.scaled
import com.simobr.donotblink.ui.theme.tinted

object LicencesTags {
    const val BODY = "licences-body"
    const val BACK = "licences-back"
    const val HEADER = "licences-header"
}

/** The asset that ships inside the APK alongside the two .ttf files it covers. */
const val OFL_ASSET_PATH = "licenses/JetBrainsMono-OFL.txt"

/**
 * Mockup styles with no frozen token: derived from the nearest token, never added to DnbType.
 *
 * The body is [DnbType.microLabel] — the smallest monospace role there is — with its tracking
 * dropped to zero and a real line height added. microLabel's 0.45em is a LABEL's tracking; applied
 * to 4400 characters of licence prose it would run every line off the side of the screen.
 */
private val LicenceBodyStyle = DnbType.microLabel.copy(
    letterSpacing = 0.em,
    lineHeight = 15.sp,
    color = DnbColor.LabelMid,
)

/** Tappable, so LabelMid rather than Dim — the Stage 5.5 role table. */
private val BackLinkStyle = DnbType.microLabel.copy(letterSpacing = 0.28.em, color = DnbColor.LabelMid)

private const val HEADER_TOP_DP = 78f
private const val BODY_TOP_DP = 128f
private const val BODY_BOTTOM_PAD_DP = 48f

/**
 * The bundled fonts' licence, shown to the person who has the fonts.
 *
 * `jetbrains_mono_{regular,medium}.ttf` ship inside the APK, and redistributing them under the SIL
 * Open Font License requires the copyright notice and the licence text to travel with them. A file
 * sitting in the repo does not travel with anything, so the text is an asset and this screen reads
 * it. No WebView, no new dependency: it is plain text in a scrolling Column.
 */
@Composable
fun LicencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    // Read once. 4.4KB off the asset manager is not worth a coroutine, and a failure here must not
    // take the screen down — an empty licence is a bug to see, not a crash to report.
    val licenceText = remember(context) { readOflAsset(context) }

    Box(
        modifier
            .fillMaxSize()
            .background(DnbColor.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = HEADER_TOP_DP.scaled(), start = DnbDim.screenPadH, end = DnbDim.screenPadH)
        ) {
            Text(
                text = "LICENCES",
                style = DnbType.sectionTitle.tinted(palette),
                modifier = Modifier.align(Alignment.CenterStart).testTag(LicencesTags.HEADER),
            )
            // Back is the system gesture on every other screen in this app and it is here too;
            // this label is the same action made visible, because a long scroll is the one place
            // an invisible way out is worth doubting.
            Text(
                text = "BACK",
                style = BackLinkStyle,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .testTag(LicencesTags.BACK)
                    .noRippleClickable(onClick = onBack),
            )
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .padding(
                    top = BODY_TOP_DP.scaled(),
                    start = DnbDim.screenPadH,
                    end = DnbDim.screenPadH,
                    bottom = BODY_BOTTOM_PAD_DP.dp,
                )
                .verticalScroll(rememberScrollState())
                .testTag(LicencesTags.BODY),
        ) {
            Text(text = licenceText, style = LicenceBodyStyle)
        }
    }
}

private fun readOflAsset(context: Context): String = runCatching {
    context.assets.open(OFL_ASSET_PATH).bufferedReader().use { it.readText() }
}.getOrDefault("")
