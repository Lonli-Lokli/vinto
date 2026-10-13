package game.vinto.app

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What iOS reserves in the window crosses from Swift as text ([Reserved.parse]): the closed iPhone
 * Duo's clock and camera, measured on the simulator, and the fold while it is half open.
 */
class ReservedRegionsTest {

    @Test
    fun theClosedDuosClockAndCameraArriveAsTwoRegions() {
        val reserved = Reserved.parse("390,10,64,40;386,60,72,90", "")
        assertEquals(
            listOf(DpRect(390.dp, 10.dp, 454.dp, 50.dp), DpRect(386.dp, 60.dp, 458.dp, 150.dp)),
            reserved.occlusions,
        )
        assertNull(reserved.division)
    }

    @Test
    fun theFoldOfAHalfOpenPhoneIsTheDivision() {
        val reserved = Reserved.parse("", "471.5,0,8,669")
        assertEquals(emptyList(), reserved.occlusions)
        assertEquals(DpRect(471.5.dp, 0.dp, 479.5.dp, 669.dp), reserved.division)
    }

    @Test
    fun nothingReservedIsNothing() {
        assertEquals(Reserved.NONE, Reserved.parse("", ""))
    }

    @Test
    fun whatCannotBeReadIsDroppedRatherThanGuessedAt() {
        val reserved = Reserved.parse("1,2,3;a,b,c,d;5,5,0,10;10,20,30,40", "x")
        assertEquals(listOf(DpRect(10.dp, 20.dp, 40.dp, 60.dp)), reserved.occlusions)
        assertNull(reserved.division)
    }
}
