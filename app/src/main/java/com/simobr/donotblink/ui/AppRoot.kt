package com.simobr.donotblink.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.simobr.donotblink.BuildConfig
import com.simobr.donotblink.ads.showsPrivacyOptionsRow
import com.simobr.donotblink.game.DailyResult
import com.simobr.donotblink.game.GameViewModel
import com.simobr.donotblink.game.Mode
import com.simobr.donotblink.game.Phase
import com.simobr.donotblink.ui.daily.DailyResultScreen
import com.simobr.donotblink.ui.daily.ShareCard
import com.simobr.donotblink.ui.theme.PhosphorPalette
import com.simobr.donotblink.ui.continueoffer.ContinueOfferScreen
import com.simobr.donotblink.ui.fail.FailScreen
import com.simobr.donotblink.ui.licences.LicencesScreen
import com.simobr.donotblink.ui.phosphor.PhosphorScreen
import com.simobr.donotblink.ui.play.PlayScreen
import com.simobr.donotblink.ui.roundcard.RoundCardScreen
import com.simobr.donotblink.ui.settings.SettingsScreen
import com.simobr.donotblink.ui.splash.SplashScreen
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.ScaledLayout
import com.simobr.donotblink.ui.theme.phosphorById
import com.simobr.donotblink.ui.titles.TitlesScreen
import com.simobr.donotblink.ui.twitch.TwitchScreen

/**
 * The whole navigation model. A handful of screens with no deep links do not justify
 * Navigation-Compose, its dependency, or its back-stack semantics: back is one `when`.
 */
sealed interface Screen {
    val id: String

    data object Splash : Screen { override val id = "splash" }
    data object Home : Screen { override val id = "home" }
    data object RoundCard : Screen { override val id = "roundcard" }
    data object Play : Screen { override val id = "play" }

    /** The trial's play surface. Same surface as [Play]; the view model is in Daily mode. */
    data object Daily : Screen { override val id = "daily" }
    data object DailyResult : Screen { override val id = "dailyresult" }
    data object Twitch : Screen { override val id = "twitch" }
    data object ContinueOffer : Screen { override val id = "continue" }
    data object Fail : Screen { override val id = "fail" }
    data object Titles : Screen { override val id = "titles" }
    data object Settings : Screen { override val id = "settings" }
    data object Phosphor : Screen { override val id = "phosphor" }
    data object Licences : Screen { override val id = "licences" }

    companion object {
        fun fromId(id: String): Screen = when (id) {
            Home.id -> Home
            RoundCard.id -> RoundCard
            Play.id -> Play
            Daily.id -> Daily
            DailyResult.id -> DailyResult
            Twitch.id -> Twitch
            ContinueOffer.id -> ContinueOffer
            Fail.id -> Fail
            Titles.id -> Titles
            Settings.id -> Settings
            Phosphor.id -> Phosphor
            Licences.id -> Licences
            else -> Splash
        }
    }
}

/** Survives rotation and process death, so the splash is genuinely cold-start only. */
private val ScreenSaver: Saver<Screen, String> =
    Saver(save = { it.id }, restore = { Screen.fromId(it) })

@Composable
fun AppRoot(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    var screen by rememberSaveable(stateSaver = ScreenSaver) {
        mutableStateOf<Screen>(Screen.Splash)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = phosphorById(state.selectedPhosphorId)

    // Full-screen ad formats need a live Activity. The host outlives it; this is the handover.
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        viewModel.attachAdActivity(activity)
        onDispose { viewModel.attachAdActivity(null) }
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        // The window is measured ONCE, here, and every screen inside reads the factor it produced.
        // Nothing below this point knows how big the device is.
        Box(modifier.fillMaxSize().background(DnbColor.Black)) {
            ScaledLayout {
                when (screen) {
                    Screen.Splash -> SplashScreen(
                        onFinished = { screen = Screen.Home },
                        // Consent, then initialize, then preload. The work belongs to the view model;
                        // the splash only waits for it, and only up to its own ceiling.
                        startupWork = {
                            activity?.let { viewModel.beginAdStartup(it) }
                            viewModel.awaitAdStartup()
                        },
                    )

                    // ONE call site for Home and Play. They are not two screens: Home is the idle face
                    // of the play surface. Two branches meant the press that starts a round disposed
                    // the very node handling it — the gesture was cancelled mid-hold and the first
                    // round of every session was unwinnable. Here `screen` only flips `homeChrome`,
                    // which is a plain recomposition: the Canvas, its pointerInput keys and the
                    // running gesture coroutine all survive, so the hold that began on Home is the
                    // hold that gets judged.
                    // Screen.Daily joins this branch deliberately: the trial is the SAME surface in
                    // a different mode, so it must be the same call site. Giving it its own branch
                    // would dispose and rebuild the Canvas and its pointerInput on entry — the exact
                    // bug that once made the first round of every session unwinnable.
                    Screen.Home, Screen.Play, Screen.Daily -> {
                        // Back leaves a run; on Home it is the system's to handle, so it is disabled
                        // rather than conditionally registered — an unconditional call site keeps the
                        // composition structure identical either side of the flip.
                        BackHandler(enabled = screen != Screen.Home) {
                            if (screen == Screen.Daily) {
                                viewModel.abandonDailyTrial()
                            } else {
                                viewModel.startNewRun()
                            }
                            screen = Screen.Home
                        }

                        // The trial ends on its tenth ring, inside the frame loop — there is no tap
                        // to hang the navigation off, so the phase is what moves the screen.
                        LaunchedEffect(state.phase, state.mode, screen) {
                            when {
                                screen != Screen.Daily -> Unit
                                state.phase == Phase.DailyDone -> screen = Screen.DailyResult
                                // `screen` survives process death in rememberSaveable; the view
                                // model does not. Coming back from a kill mid-trial would restore
                                // the Daily screen around a freshly Endless view model, and the
                                // player would be playing the ordinary game under the trial's
                                // chrome. The attempt was never recorded, so Home is the honest
                                // place to land — the trial is still there to start properly.
                                state.mode != Mode.Daily -> screen = Screen.Home
                            }
                        }

                        PlayScreen(
                            viewModel = viewModel,
                            homeChrome = screen == Screen.Home,
                            // A press during the trial must not flip the screen to Play: that would
                            // take the surface out of Daily mode's chrome mid-trial.
                            onRoundStarted = { if (screen == Screen.Home) screen = Screen.Play },
                            onFailed = { screen = afterFail(viewModel) },
                            onOpenTitles = { screen = Screen.Titles },
                            onOpenSettings = { screen = Screen.Settings },
                            onOpenDaily = {
                                // startDailyTrial refuses a second attempt and says so, so the
                                // decision of where to go lives with the rule, not with the UI.
                                screen = if (viewModel.startDailyTrial()) {
                                    Screen.Daily
                                } else {
                                    Screen.DailyResult
                                }
                            },
                            onOpenTwitch = { screen = Screen.Twitch },
                        )
                    }

                    Screen.Twitch -> TwitchScreen(
                        bestMs = state.twitchBestMs,
                        onReaction = viewModel::recordTwitchReaction,
                        onHome = { screen = Screen.Home },
                        hapticsEnabled = state.hapticsEnabled,
                        soundEnabled = state.soundEnabled,
                        onSittingCompleted = viewModel::onReflexSittingCompleted,
                    )

                    Screen.DailyResult -> {
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()
                        val result = state.dailyResult
                        val leave = {
                            viewModel.leaveDailyTrial()
                            screen = Screen.Home
                        }
                        // A result that is somehow absent cannot be rendered, and stranding the
                        // player on an empty screen is worse than sending them home.
                        if (result == null) {
                            LaunchedEffect(Unit) { leave() }
                        } else {
                            DailyResultScreen(
                                result = result,
                                dayStreak = state.dailyDayStreak,
                                bestHits = state.dailyBestHits,
                                onShare = {
                                    scope.launch { shareDailyResult(context, result, palette) }
                                },
                                onHome = leave,
                            )
                        }
                    }

                    Screen.RoundCard -> {
                        val withInterstitial = remember(state.roundId) { viewModel.shouldShowInterstitialNow() }
                        RoundCardScreen(
                            roundNumber = state.lifetimeRuns + 1,
                            runsTowardInterstitial = if (withInterstitial) 3 else state.lifetimeRuns % 3,
                            withInterstitial = withInterstitial,
                            showInterstitial = { onFinished -> viewModel.showInterstitial(onFinished) },
                            onFinished = { screen = Screen.Play },
                        )
                    }

                    Screen.ContinueOffer -> {
                        // The outcome arrives asynchronously from the SDK; the phase is what decides.
                        LaunchedEffect(state.phase) {
                            when (state.phase) {
                                Phase.Idle -> screen = Screen.Play
                                Phase.Failed -> screen = Screen.Fail
                                else -> Unit
                            }
                        }
                        ContinueOfferScreen(
                            streak = state.lastRunStreak,
                            onWatch = viewModel::acceptContinue,
                            onDecline = viewModel::declineContinue,
                        )
                    }

                    Screen.Fail -> FailScreen(
                        streak = state.lastRunStreak,
                        best = state.best,
                        newlyUnlocked = state.newlyUnlockedTitles,
                        lastRelease = state.lastRelease,
                        bannerEnabled = state.adsEnabled,
                        onAgain = {
                            viewModel.startNewRun()
                            screen = Screen.RoundCard
                        },
                        onHome = {
                            viewModel.startNewRun()
                            screen = Screen.Home
                        },
                    )

                    Screen.Titles -> TitlesScreen(
                        bestStreak = state.best,
                        revealLockedNames = state.titlesRevealed,
                        onBack = { screen = Screen.Home },
                        onWatchToReveal = { viewModel.purchaseTitleReveal() },
                    )

                    Screen.Settings -> {
                        val context = LocalContext.current
                        SettingsScreen(
                            hapticsEnabled = state.hapticsEnabled,
                            soundEnabled = state.soundEnabled,
                            bestStreak = state.best,
                            phosphorLabel = palette.label,
                            onSetHaptics = viewModel::setHaptics,
                            onSetSound = viewModel::setSound,
                            onResetBest = viewModel::resetBest,
                            onOpenPhosphor = { screen = Screen.Phosphor },
                            // UMP's entry point. The row does not exist outside the EEA, which is
                            // correct: a row that opens nothing is worse than no row.
                            showPrivacyOptions = showsPrivacyOptionsRow(state.privacyOptionsRequired),
                            onOpenPrivacyOptions = viewModel::showPrivacyOptions,
                            onOpenPrivacyPolicy = { openUrl(context, BuildConfig.PRIVACY_POLICY_URL) },
                            onOpenLicences = { screen = Screen.Licences },
                            onBack = { screen = Screen.Home },
                            bannerEnabled = state.adsEnabled,
                        )
                    }

                    Screen.Phosphor -> PhosphorScreen(
                        selectedId = state.selectedPhosphorId,
                        unlockedIds = state.unlockedPaletteIds,
                        onSelect = viewModel::selectPhosphor,
                        onWatchToUnlock = { id -> viewModel.purchasePhosphor(id) },
                        onBack = { screen = Screen.Settings },
                    )

                    Screen.Licences -> LicencesScreen(onBack = { screen = Screen.Settings })
                }
            }
        }
    }
}

/** A fail either buys the player an offer, or it is simply over. */
private fun afterFail(viewModel: GameViewModel): Screen =
    if (viewModel.state.value.phase == Phase.ContinueOffer) Screen.ContinueOffer else Screen.Fail

/**
 * Renders the share card off the main thread, then hands it to the system chooser.
 *
 * Every step is allowed to fail without consequence: a card that cannot be drawn or written still
 * shares as text, and a device with nothing to share to simply does nothing. A share button is never
 * worth a crash.
 */
private suspend fun shareDailyResult(
    context: Context,
    result: DailyResult,
    palette: PhosphorPalette,
) {
    val uri = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = ShareCard.render(
                context = context,
                result = result,
                phosphor = palette.phosphor.toArgb(),
                hot = palette.hot.toArgb(),
                dim = palette.dim.toArgb(),
            )
            ShareCard.writeForSharing(context, bitmap, result)
        }.getOrNull()
    }
    runCatching { context.startActivity(ShareCard.shareIntent(result, uri)) }
}

/**
 * Hands a URL to whatever the device uses for the web. A plain [Intent.ACTION_VIEW] — Custom Tabs
 * would mean androidx.browser, and the dependency list in CLAUDE.md is exact.
 *
 * A device with no browser at all throws [ActivityNotFoundException]; the row then does nothing,
 * which is the specified behaviour. Nothing here is worth a crash.
 */
private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
