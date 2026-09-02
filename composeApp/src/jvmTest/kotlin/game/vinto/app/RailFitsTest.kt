package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Say
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every choice on the rail is on the screen, whole, without scrolling.
 *
 * The rail is a fixed share of the screen and its tenants — a prompt, the box of recent
 * moves, and the buttons — used to be one scrolling column. On a phone taller than the one
 * the rail was drawn on that put the second button half under the edge of the screen, in
 * three screenshots in a row: "Leave them" was a control a player had to go looking for. The
 * buttons are pinned to the foot of the rail now and the log takes what is left, and this
 * measures the *clipped* bounds on purpose — a button under the edge reports less than its
 * own height, which is exactly the failure.
 *
 * Two screens: the phone the suites are drawn on, and one shaped like the phone that reported
 * it. Then the doubled system font, where the buttons still may not leave the screen — the
 * prompt scrolls within its own room instead.
 */
@OptIn(ExperimentalTestApi::class)
class RailFitsTest {

    @Test
    fun bothChoicesAreWhollyOnTheTestPhone() = everyChoiceIsWhole(PHONE_W, PHONE_H)

    @Test
    fun bothChoicesAreWhollyOnATallPhone() = everyChoiceIsWhole(PHONE_W, TALL_H)

    @Test
    fun bothChoicesAreWhollyOnScreenAtADoubledFont() = everyChoiceIsWhole(PHONE_W, PHONE_H, fontScale = 2f)

    /**
     * The card being decided about is in the rail, beside the prompt, at a size its face can
     * be read at — and both choices are still whole under it.
     */
    @Test
    fun theCardInPlayIsDrawnBesideThePrompt() = runComposeUiTest {
        val (view, said) = drawn()
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, said, 1),
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

        val rank = (view.pendingAction?.card as game.vinto.engine.CardView.Visible).card.rank.serialName
        onNodeWithContentDescription("in play: $rank").assertIsDisplayed()
    }

    /**
     * The card's column is there for the whole of your turn — before the draw it holds the
     * deck's back — and not at all on a bot's turn or during setup, where the words take the
     * width. A column that came and went within a turn was the layout jumping.
     */
    @Test
    fun theColumnIsThereForYourTurnAndNotForABots() = runComposeUiTest {
        val (setup, yourTurn) = beforeTheDraw()
        val botsTurn = yourTurn.copy(currentPlayerIndex = (yourTurn.currentPlayerIndex + 1) % yourTurn.players.size)

        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) { Rail(yourTurn) }
            }
        }
        waitForIdle()
        onNodeWithContentDescription("the deck").assertIsDisplayed()

        for (view in listOf(setup, botsTurn)) {
            setContent {
                VintoTheme {
                    Box(modifier = Modifier.size(PHONE_W, PHONE_H)) { Rail(view) }
                }
            }
            waitForIdle()
            val cards = onAllNodes(
                hasContentDescription("the deck") or hasContentDescription("in play: ", substring = true),
            ).fetchSemanticsNodes()
            assertTrue(cards.isEmpty(), "no card column on somebody else's turn or in setup")
        }
    }

    @Composable
    private fun Rail(view: PlayerView) {
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

    /** The view during setup, and the view at the top of your first turn, before the draw. */
    private fun beforeTheDraw(): Pair<PlayerView, PlayerView> {
        lateinit var setup: PlayerView
        lateinit var turn: PlayerView
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            setup = session.view.value
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            turn = session.view.value
        }
        return setup to turn
    }

    private fun everyChoiceIsWhole(width: Dp, height: Dp, fontScale: Float = 1f) = runComposeUiTest {
        val (view, said) = drawn()
        setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                VintoTheme {
                    Box(modifier = Modifier.size(width, height)) {
                        TableScreen(
                            state = TableState(view, tableFor(view), null, said, 1),
                            layout = TableLayout.forScreen(height),
                            onMove = {},
                            onHelp = {},
                            onSettings = {},
                            onReport = {},
                            onDeck = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        val tappable = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .fetchSemanticsNodes()
        val choices = tappable.filter { it.name() in CHOICES }
        assertTrue(
            choices.size == CHOICES.size,
            "the two choices are drawn: ${choices.map { it.name() }} among ${tappable.map { it.name() }}",
        )

        choices.forEach { node ->
            val shown = node.boundsInRoot
            assertTrue(
                shown.height >= node.size.height - 1 && shown.bottom <= height.value * node.density(),
                "${node.name()} is not wholly on screen: showing ${shown.height} of ${node.size.height}",
            )
        }
    }

    private fun androidx.compose.ui.semantics.SemanticsNode.name() =
        config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() ?: "?"

    private fun androidx.compose.ui.semantics.SemanticsNode.density() = layoutInfo.density.density

    /** A plain card drawn — swap or discard, two full-width buttons — with a full log under it. */
    private fun drawn(): Pair<PlayerView, List<Say>> {
        lateinit var view: PlayerView
        lateinit var said: List<Say>
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            view = session.view.value
            said = session.log.value
        }
        return view to said
    }

    private companion object {
        val CHOICES = setOf("Swap Cards", "Discard")
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp

        /** A 20:9 phone at the same width, which is where the second button went under. */
        val TALL_H = 900.dp
    }
}
