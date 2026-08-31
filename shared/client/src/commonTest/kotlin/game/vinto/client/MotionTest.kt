package game.vinto.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reduced motion is a *choice about a choice*: the player's setting resolved against the
 * platform's accessibility preference. The resolution is the one piece of logic, so it is
 * the one piece pinned — and the settings file from before the field existed must still
 * decode, because a preference must never be lost to an upgrade.
 */
class MotionTest {

    @Test
    fun theChoiceResolvesAgainstThePlatform() {
        // SYSTEM defers; the explicit choices override in both directions.
        assertTrue(MotionChoice.SYSTEM.reduced(systemSaysReduce = true))
        assertFalse(MotionChoice.SYSTEM.reduced(systemSaysReduce = false))
        assertTrue(MotionChoice.REDUCED.reduced(systemSaysReduce = false))
        assertFalse(MotionChoice.FULL.reduced(systemSaysReduce = true))
    }

    @Test
    fun aSettingsFileFromBeforeTheFieldStillDecodes() {
        val vault = MemoryVault()
        vault.write(
            "vinto.settings",
            """{"version":1,"difficulty":"moderate","pace":"steady","theme":"system","haptics":true}""",
        )

        val loaded = vault.loadSettings()
        assertEquals(MotionChoice.SYSTEM, loaded.motion, "the default fills the missing field")
        assertEquals(Pace.STEADY, loaded.pace, "and nothing else was disturbed")
    }

    @Test
    fun theChoiceSurvivesARoundTrip() {
        val vault = MemoryVault()
        vault.saveSettings(Settings(motion = MotionChoice.REDUCED))
        assertEquals(MotionChoice.REDUCED, vault.loadSettings().motion)
    }
}
