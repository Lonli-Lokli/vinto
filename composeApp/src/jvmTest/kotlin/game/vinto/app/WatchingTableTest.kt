package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A table the viewer is not sitting at draws, rather than ending the app.
 *
 * `FeltTable` reached its own seat with `players.first { it.id == view.viewerId }`, which
 * throws when there is no such seat — while `tableFor`, one function away, opens with the same
 * lookup as a `firstOrNull` and answers `Ask.Watching`. So the model handled the case
 * deliberately and the felt crashed on it a moment later, with nothing between the exception
 * and the launcher.
 *
 * A solo game always seats you. This could only ever have gone wrong **online**, where the
 * room decides who is seated and a view arrives over a wire — which is also the one place
 * nothing catches it.
 *
 * It is a compile-time fix rather than a check: the seat is reached through
 * `PlayerView.mySeat`, which is nullable, so every call site is asked the question by the
 * compiler. `first {}` is total in Kotlin's type system and partial in the world, and this is
 * the only lever the language offers against that.
 */
@OptIn(ExperimentalTestApi::class)
class WatchingTableTest {

    /** A real dealt view, re-addressed to somebody who is not at the table. */
    private fun watching(): PlayerView =
        teachingSession().view.value.copy(viewerId = "nobody-here")

    @Test
    fun aViewWithNoSeatForTheViewerStillDrawsTheTable() = runComposeUiTest {
        val view = watching()
        val cards = cardsOn(view)

        // Every seat is somebody else's now, and all four are still on the felt — the fourth
        // takes the chair the viewer's own hand would have used, because the felt has exactly
        // four places and a player with nowhere to sit simply disappears from the game.
        //
        // The seat *named* "You" is still there and still named that: it is a nickname the
        // deal chose, not a claim about who is looking. What matters is that all four are
        // drawn and nothing threw.
        val hands = cards.map { it.substringBefore(", card ") }.distinct()
        assertEquals(
            view.players.map { it.nickname }.toSet(),
            hands.toSet(),
            "a watcher should see the whole table, and saw: $hands",
        )
    }

    /** And the ordinary case is unchanged: a seated view still draws your own hand. */
    @Test
    fun aSeatedViewStillHasAHandOfItsOwn() = runComposeUiTest {
        val view = teachingSession().view.value
        val hands = cardsOn(view).map { it.substringBefore(", card ") }.distinct()

        assertTrue(
            hands.any { it.startsWith("You") },
            "the player's own hand went missing from their own table: $hands",
        )
    }

    private fun ComposeUiTest.cardsOn(view: PlayerView): List<String> {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, emptyList(), 1),
                        layout = TableLayout.forScreen(PHONE_H),
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                        onReport = {},
                        onDeck = {},
                    )
                }
            }
        }
        waitForIdle()
        return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
            .filter { it.contains(", card ") }
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
