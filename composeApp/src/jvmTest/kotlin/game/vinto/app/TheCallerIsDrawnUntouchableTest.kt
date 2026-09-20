package game.vinto.app

import androidx.compose.ui.graphics.Color
import game.vinto.app.game.callersEdge
import game.vinto.app.theme.Slate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Vinto caller, and their cards, inside one border.
 *
 * Reported from a phone: *"We need some indication that this person is vinto. We have mark but
 * visually it's not enough. What about drawing border around him and his cards? It will even
 * show that you cannot touch his cards."*
 *
 * The crown on the plate said who called; it did not say what that *means*, which is that the
 * hand beside it is out of reach for the rest of the round — no Jack, no Queen, no Ace, and
 * nothing the coalition's plan may name. The model has always known it (`coalitionCards` skips
 * the caller); the felt only whispered it.
 *
 * The border and the room it needs are on **every** seat and only the colour changes, which is
 * why this is a colour rather than a shape: a border that *appears* is a seat that grows, and a
 * seat that grows re-pitches the hand beside it — the fault `SteadyPlateTest` holds the plate
 * still for, reported once already.
 */
class TheCallerIsDrawnUntouchableTest {

    @Test
    fun onlyTheCallerIsRinged() {
        assertEquals(Slate.gold, callersEdge(isCaller = true), "the caller wore no ring")
        assertEquals(
            Color.Transparent,
            callersEdge(isCaller = false),
            "a seat that did not call Vinto was ringed as though it had",
        )
    }
}
