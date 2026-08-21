package com.simobr.donotblink.ui

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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simobr.donotblink.game.GameViewModel
import com.simobr.donotblink.game.Phase
import com.simobr.donotblink.ui.continueoffer.ContinueOfferScreen
import com.simobr.donotblink.ui.fail.FailScreen
import com.simobr.donotblink.ui.phosphor.PhosphorScreen
import com.simobr.donotblink.ui.play.PlayScreen
import com.simobr.donotblink.ui.roundcard.RoundCardScreen
import com.simobr.donotblink.ui.settings.SettingsScreen
import com.simobr.donotblink.ui.splash.SplashScreen
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.phosphorById
import com.simobr.donotblink.ui.titles.TitlesScreen

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
    data object ContinueOffer : Screen { override val id = "continue" }
    data object Fail : Screen { override val id = "fail" }
    data object Titles : Screen { override val id = "titles" }
    data object Settings : Screen { override val id = "settings" }
    data object Phosphor : Screen { override val id = "phosphor" }

    companion object {
        fun fromId(id: String): Screen = when (id) {
            Home.id -> Home
            RoundCard.id -> RoundCard
            Play.id -> Play
            ContinueOffer.id -> ContinueOffer
            Fail.id -> Fail
            Titles.id -> Titles
            Settings.id -> Settings
            Phosphor.id -> Phosphor
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
        Box(modifier.fillMaxSize().background(DnbColor.Black)) {
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

                Screen.Home -> PlayScreen(
                    viewModel = viewModel,
                    homeChrome = true,
                    onRoundStarted = { screen = Screen.Play },
                    onFailed = { screen = afterFail(viewModel) },
                    onOpenTitles = { screen = Screen.Titles },
                    onOpenSettings = { screen = Screen.Settings },
                )

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

                Screen.Play -> {
                    BackHandler {
                        viewModel.startNewRun()
                        screen = Screen.Home
                    }
                    PlayScreen(
                        viewModel = viewModel,
                        onFailed = { screen = afterFail(viewModel) },
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
                    bannerEnabled = viewModel.adsEnabled,
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

                Screen.Settings -> SettingsScreen(
                    hapticsEnabled = state.hapticsEnabled,
                    soundEnabled = state.soundEnabled,
                    bestStreak = state.best,
                    phosphorLabel = palette.label,
                    onSetHaptics = viewModel::setHaptics,
                    onSetSound = viewModel::setSound,
                    onResetBest = viewModel::resetBest,
                    onOpenPhosphor = { screen = Screen.Phosphor },
                    // No policy URL exists yet; the row is drawn and does nothing.
                    onOpenPrivacyPolicy = {},
                    onBack = { screen = Screen.Home },
                )

                Screen.Phosphor -> PhosphorScreen(
                    selectedId = state.selectedPhosphorId,
                    unlockedIds = state.unlockedPaletteIds,
                    onSelect = viewModel::selectPhosphor,
                    onWatchToUnlock = { id -> viewModel.purchasePhosphor(id) },
                    onBack = { screen = Screen.Settings },
                )
            }
        }
    }
}

/** A fail either buys the player an offer, or it is simply over. */
private fun afterFail(viewModel: GameViewModel): Screen =
    if (viewModel.state.value.phase == Phase.ContinueOffer) Screen.ContinueOffer else Screen.Fail
