package com.simobr.donotblink.data

import com.simobr.donotblink.game.DailyResult
import com.simobr.donotblink.game.DailyTrial
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The daily result is the only structured value this app puts in DataStore. A corrupt preference
 * must decode to null and hand the player a fresh trial — never crash the app on launch.
 */
class DailyCodecTest {

    private val result = DailyResult(
        epochDay = 20_338L,
        marks = listOf(true, true, false, true, true, true, true, false, true, true),
        meanAbsErrorMs = 23,
    )

    @Test
    fun `a result survives a round trip`() {
        assertEquals(result, DailyCodec.decode(DailyCodec.encode(result)))
    }

    @Test
    fun `the encoding is the documented shape`() {
        assertEquals("20338|1101111011|23", DailyCodec.encode(result))
    }

    @Test
    fun `nothing stored decodes to nothing`() {
        assertNull(DailyCodec.decode(null))
        assertNull(DailyCodec.decode(""))
        assertNull(DailyCodec.decode("   "))
    }

    @Test
    fun `garbage decodes to null rather than throwing`() {
        assertNull(DailyCodec.decode("not a result"))
        assertNull(DailyCodec.decode("20338|1101111011"))          // too few fields
        assertNull(DailyCodec.decode("20338|1101111011|23|extra")) // too many
        assertNull(DailyCodec.decode("abc|1101111011|23"))         // day is not a number
        assertNull(DailyCodec.decode("20338|1101111011|abc"))      // error is not a number
        assertNull(DailyCodec.decode("20338|11011|23"))            // wrong ring count
        assertNull(DailyCodec.decode("20338|110111101X|23"))       // not a mark glyph
    }

    @Test
    fun `a trial of all misses is still a valid result`() {
        val blank = DailyResult(20_338L, List(DailyTrial.RINGS) { false }, 0)
        assertEquals(blank, DailyCodec.decode(DailyCodec.encode(blank)))
        assertEquals(0, blank.hits)
    }

    @Test
    fun `recording rolls the day streak and the best-hits record together`() = runTest {
        val store = InMemoryGameStore()

        store.recordDailyResult(DailyResult(20_337L, marks(hits = 6), 40))
        assertEquals(1, store.dailyDayStreak.value())
        assertEquals(6, store.dailyBestHits.value())

        // The next calendar day extends the streak; a worse score does not lower the record.
        store.recordDailyResult(DailyResult(20_338L, marks(hits = 4), 55))
        assertEquals(2, store.dailyDayStreak.value())
        assertEquals(6, store.dailyBestHits.value())

        // A skipped day resets the streak but keeps the record.
        store.recordDailyResult(DailyResult(20_346L, marks(hits = 9), 18))
        assertEquals(1, store.dailyDayStreak.value())
        assertEquals(9, store.dailyBestHits.value())
    }

    private fun marks(hits: Int) = List(DailyTrial.RINGS) { it < hits }

    private suspend fun <T> Flow<T>.value(): T = first()
}
