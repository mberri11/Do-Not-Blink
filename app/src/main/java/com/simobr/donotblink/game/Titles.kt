package com.simobr.donotblink.game

/** One rung of the ladder. A title is unlocked when the best streak reaches its threshold. */
data class Title(val threshold: Int, val name: String)

/**
 * Twenty pairs. A JSON file and a serialization dependency for twenty pairs would be architecture
 * theatre — this is the whole ladder, in source, where a diff can see it.
 */
val TITLES: List<Title> = listOf(
    Title(1, "EYES OPEN"),
    Title(2, "TWITCH"),
    Title(3, "STILL HAND"),
    Title(5, "SLOW BLINKER"),
    Title(8, "STEADY"),
    Title(12, "COLD NERVE"),
    Title(16, "LIDLESS"),
    Title(20, "HAIRLINE"),
    Title(25, "UNFLINCHING"),
    Title(30, "QUIET PULSE"),
    Title(36, "GLASS EYE"),
    Title(42, "DEAD CALM"),
    Title(50, "THE LONG LOOK"),
    Title(60, "NO TREMOR"),
    Title(70, "SIGHTLINE"),
    Title(85, "HELD BREATH"),
    Title(100, "CENTURY STARE"),
    Title(120, "STONE LENS"),
    Title(150, "SLEEPLESS"),
    Title(200, "DO NOT BLINK"),
)

/** Nothing is stored per title: the best streak is the whole unlock state. */
fun unlockedTitleCount(bestStreak: Int): Int = TITLES.count { bestStreak >= it.threshold }

fun isTitleUnlocked(title: Title, bestStreak: Int): Boolean = bestStreak >= title.threshold

/** The titles a run earned: everything the previous best had not reached yet. */
fun titlesUnlockedBetween(previousBest: Int, newBest: Int): List<Title> =
    TITLES.filter { it.threshold in (previousBest + 1)..newBest }
