package com.simobr.donotblink.game

import java.io.File
import kotlin.math.round
import kotlin.math.roundToLong
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTest {

    // 1 ------------------------------------------------------------------------------------------

    @Test
    fun half_window_never_drops_below_the_human_floor() {
        for (streak in 0..500) {
            val window = Difficulty.halfWindowMs(streak)
            assertTrue(
                "streak $streak has a half-window of ${window}ms, below the ${Difficulty.HUMAN_FLOOR_MS}ms floor",
                window >= Difficulty.HUMAN_FLOOR_MS,
            )
        }
    }

    // 2 ------------------------------------------------------------------------------------------

    @Test
    fun speed_is_monotonically_non_decreasing() {
        for (streak in 0..499) {
            val here = Difficulty.speedDpPerSec(streak)
            val next = Difficulty.speedDpPerSec(streak + 1)
            assertTrue(
                "speed dropped between streak $streak ($here dp/s) and ${streak + 1} ($next dp/s)",
                next >= here,
            )
        }
    }

    // 3 ------------------------------------------------------------------------------------------

    @Test
    fun stroke_stays_positive() {
        for (streak in 0..500) {
            val stroke = Difficulty.strokeDp(streak)
            assertTrue("streak $streak has stroke ${stroke}dp", stroke > 0f)
        }
    }

    // 4 ------------------------------------------------------------------------------------------

    @Test
    fun travel_floors_at_exactly_480ms() {
        for (streak in 0..500) {
            assertTrue(
                "streak $streak travels in ${Difficulty.travelMs(streak)}ms",
                Difficulty.travelMs(streak) >= 480f,
            )
        }
        assertEquals(480f, Difficulty.travelMs(17), 0f)
        assertEquals(480f, Difficulty.travelMs(500), 0f)
        assertTrue(
            "the floor should not be engaged at streak 16",
            Difficulty.travelMs(16) > 480f,
        )
    }

    // 5 ------------------------------------------------------------------------------------------

    /**
     * The four sanity rows, to 1 decimal place.
     *
     * These are the values the FORMULAS in the brief produce. Three of the four rows as written in
     * the brief do not follow from those formulas — see the stage report. Stated vs derived:
     *
     *   s=0   stated travel 1560.0 speed  46.2 band 7.8 window 169.0
     *         derived travel 1560.0 speed  46.2 band 9.5 window 205.8   <- band/window differ
     *   s=8   stated travel  866.0 speed  83.1 band 7.3 window  88.0
     *         derived travel  872.9 speed  82.5 band 7.3 window  88.0   <- travel/speed differ
     *   s=16  stated travel  480.0 speed 150.0 band 7.8 window  52.0
     *         derived travel  488.5 speed 147.4 band 7.7 window  52.0   <- floor engages at s=17
     *   s=40  stated travel  480.0 speed 150.0 band 7.8 window  52.0
     *         derived travel  480.0 speed 150.0 band 7.8 window  52.0   <- matches exactly
     */
    @Test
    fun sanity_rows_to_one_decimal_place() {
        assertRow(streak = 0, travelMs = 1560.0f, speedDpPerSec = 46.2f, bandDp = 9.5f, halfWindowMs = 205.8f, clamped = false)
        assertRow(streak = 8, travelMs = 872.9f, speedDpPerSec = 82.5f, bandDp = 7.3f, halfWindowMs = 88.0f, clamped = false)
        assertRow(streak = 16, travelMs = 488.5f, speedDpPerSec = 147.4f, bandDp = 7.7f, halfWindowMs = 52.0f, clamped = true)
        assertRow(streak = 40, travelMs = 480.0f, speedDpPerSec = 150.0f, bandDp = 7.8f, halfWindowMs = 52.0f, clamped = true)
    }

    // 6 ------------------------------------------------------------------------------------------

    @Test
    fun release_at_exactly_travel_ms_is_perfect() {
        for (streak in UNJITTERED_STREAKS) {
            val clock = RoundClock.start(streak, seed = 1L, downUptimeMs = DOWN)
            assertEquals(
                "an unjittered round's own travel must equal the curve's travel",
                Difficulty.travelMs(streak),
                clock.plan.perfectElapsedMs,
                0.001f,
            )
            val release = clock.judgeRelease(DOWN + Difficulty.travelMs(streak).roundToLong())
            assertEquals("streak $streak: $release", Verdict.PERFECT, release.verdict)
        }
    }

    // 7 ------------------------------------------------------------------------------------------

    @Test
    fun release_one_ms_past_the_window_is_a_fail() {
        for (streak in UNJITTERED_STREAKS) {
            val clock = RoundClock.start(streak, seed = 1L, downUptimeMs = DOWN)
            val elapsed = Difficulty.travelMs(streak) + Difficulty.halfWindowMs(streak) + 1f
            val release = clock.judgeRelease(DOWN + elapsed.roundToLong())
            assertTrue("streak $streak: $release", release.verdict.isFail)
            assertEquals("streak $streak: $release", Verdict.LATE, release.verdict)
        }
    }

    // 8 ------------------------------------------------------------------------------------------

    @Test
    fun release_one_ms_inside_the_early_edge_is_perfect() {
        for (streak in UNJITTERED_STREAKS) {
            val clock = RoundClock.start(streak, seed = 1L, downUptimeMs = DOWN)
            val elapsed = Difficulty.travelMs(streak) - Difficulty.halfWindowMs(streak) + 1f
            val release = clock.judgeRelease(DOWN + elapsed.roundToLong())
            assertEquals("streak $streak: $release", Verdict.PERFECT, release.verdict)
        }
    }

    // 9 ------------------------------------------------------------------------------------------

    @Test
    fun blackout_never_overlaps_the_final_fifth_of_travel() {
        val seeds = Random(9_001)
        var sampled = 0
        repeat(5_000) { index ->
            val streak = Difficulty.BLACKOUT_FROM_STREAK + (index % 25)
            val plan = Difficulty.planRound(streak, seeds.nextLong())
            val blackout = plan.blackout ?: return@repeat
            sampled++
            val travel = plan.perfectElapsedMs
            assertTrue(
                "blackout started at ${blackout.startMs}ms, before 20% of ${travel}ms",
                blackout.startMs >= 0.20f * travel - EPSILON,
            )
            assertTrue(
                "blackout ended at ${blackout.endMs}ms, inside the final fifth of ${travel}ms",
                blackout.endMs <= 0.80f * travel + EPSILON,
            )
        }
        assertTrue("no blackouts were sampled at all", sampled > 100)
    }

    // 10 -----------------------------------------------------------------------------------------

    @Test
    fun blackout_fires_on_about_18_percent_of_rounds_at_streak_45() {
        val seeds = Random(20_260_820)
        val blackouts = (1..1_000).count { Difficulty.planRound(45, seeds.nextLong()).blackout != null }
        assertTrue("blackout count was $blackouts, outside 150..220", blackouts in 150..220)
    }

    // beyond the ten: the purity the stage is built on ------------------------------------------

    @Test
    fun game_sources_import_neither_android_nor_compose() {
        for (name in listOf("Difficulty.kt", "RoundClock.kt")) {
            val source = sourceFile(name)
            assertNotNull("could not locate game/$name from ${File(".").absolutePath}", source)
            val offenders = source!!.readLines()
                .map { it.trim() }
                .filter { it.startsWith("import ") }
                .filter { it.contains("android") || it.contains("compose") }
            assertEquals("game/$name imports platform code: $offenders", emptyList<String>(), offenders)
        }
    }

    // helpers ------------------------------------------------------------------------------------

    private fun assertRow(
        streak: Int,
        travelMs: Float,
        speedDpPerSec: Float,
        bandDp: Float,
        halfWindowMs: Float,
        clamped: Boolean,
    ) {
        assertEquals("travelMs($streak)", travelMs, round1(Difficulty.travelMs(streak)), 0f)
        assertEquals("speedDpPerSec($streak)", speedDpPerSec, round1(Difficulty.speedDpPerSec(streak)), 0f)
        assertEquals("bandDp($streak)", bandDp, round1(Difficulty.bandDp(streak)), 0f)
        assertEquals("halfWindowMs($streak)", halfWindowMs, round1(Difficulty.halfWindowMs(streak)), 0f)
        assertEquals("isFairnessClamped($streak)", clamped, Difficulty.isFairnessClamped(streak))
    }

    private fun round1(value: Float): Float = round(value * 10f) / 10f

    private fun sourceFile(name: String): File? =
        listOf(
            "src/main/java/com/simobr/donotblink/game/$name",
            "app/src/main/java/com/simobr/donotblink/game/$name",
        ).map(::File).firstOrNull { it.isFile }

    private companion object {
        /** Streaks below the jitter ladder, where a round's travel is the curve's travel. */
        val UNJITTERED_STREAKS = listOf(0, 8, 16)
        const val DOWN = 10_000L
        const val EPSILON = 0.001f
    }
}
