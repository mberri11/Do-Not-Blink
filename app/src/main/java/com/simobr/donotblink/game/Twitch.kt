package com.simobr.donotblink.game

import kotlin.random.Random

/**
 * The reflex test: the screen sits dark, the ring arrives without warning, and the only thing
 * measured is how long the finger took.
 *
 * It shares no geometry with the main game — no band, no contraction, no plan — so it shares no
 * state machine with it either. What it does share is the timebase: a reaction is the gap between
 * the frame the signal was drawn on and the pointer event's own `uptimeMillis`, both on
 * `SystemClock.uptimeMillis`, exactly as [RoundClock] judges a release.
 *
 * Pure Kotlin: no Android, no Compose, no clock read.
 */
object Twitch {

    /** Attempts in one sitting. Five is enough for a best to mean something and short enough to repeat. */
    const val ATTEMPTS = 5

    /**
     * The wait before the signal, in milliseconds.
     *
     * The floor is high enough that a player cannot simply tap on arrival and the ceiling low enough
     * that waiting is not a punishment. Uniform on purpose: any distribution with a mode gives the
     * player something to learn to anticipate, and anticipation is the one thing this test must not
     * reward.
     */
    const val MIN_WAIT_MS = 1_400L
    const val MAX_WAIT_MS = 4_600L

    fun waitMs(random: Random): Long = random.nextLong(MIN_WAIT_MS, MAX_WAIT_MS + 1)

    /**
     * Human reaction to a visual signal bottoms out around 100ms; anything faster was a guess that
     * happened to land. Reported so the player knows the game noticed, never silently kept.
     */
    const val GUESS_FLOOR_MS = 100L

    fun isGuess(reactionMs: Long): Boolean = reactionMs < GUESS_FLOOR_MS

    /**
     * Above this, the player was not watching the screen — they put the phone down, or a
     * notification took their attention. The slowest genuine reaction to a visual signal is well
     * under a second even when tired, so this ceiling cannot discard a real reading.
     */
    const val ABANDONED_CEILING_MS = 2_000L

    fun isAbandoned(reactionMs: Long): Boolean = reactionMs > ABANDONED_CEILING_MS

    /**
     * Whether a reading counts at all.
     *
     * Both ends matter, and for the same reason: the stored best is a MINIMUM, so a first sitting
     * that produced only one absurd reading would stand as the record until it happened to be
     * beaten. Driving the test with a 17-second tap put `BEST 17376MS` on the home screen, which is
     * how this ceiling got written.
     */
    fun isRecordable(reactionMs: Long): Boolean = !isGuess(reactionMs) && !isAbandoned(reactionMs)

    /**
     * The verdict word. The ladder is deliberately generous at the top: this exists to make a number
     * feel like something, not to tell most players they are slow.
     */
    fun grade(reactionMs: Long): String = when {
        reactionMs < 180L -> "INHUMAN"
        reactionMs < 220L -> "REFLEX"
        reactionMs < 270L -> "SHARP"
        reactionMs < 340L -> "AWAKE"
        reactionMs < 450L -> "SLOW"
        else -> "ASLEEP"
    }

    /** Chirp tiers, fastest first. Three pitches for six grades. */
    const val CHIRP_TIERS = 3

    /**
     * Which reward chirp a reading earns: 0 is the brightest.
     *
     * The boundaries are grade boundaries, so the sound and the word on screen can never disagree —
     * defining them separately in the audio code was two places to drift.
     */
    fun chirpTier(reactionMs: Long): Int = when {
        reactionMs < 220L -> 0
        reactionMs < 340L -> 1
        else -> 2
    }

    /**
     * Best and mean of the attempts that produced a number.
     *
     * A sitting with nothing valid in it has no best, and reports null rather than a zero that would
     * then look like a world record on the home screen.
     */
    fun best(reactionsMs: List<Long>): Long? = reactionsMs.minOrNull()

    fun mean(reactionsMs: List<Long>): Long? =
        if (reactionsMs.isEmpty()) null else reactionsMs.sum() / reactionsMs.size
}
