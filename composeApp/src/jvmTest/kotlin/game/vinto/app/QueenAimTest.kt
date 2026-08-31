package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.CardStage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Frame
import game.vinto.client.LocalGameSession
import game.vinto.client.tableFor
import game.vinto.engine.CardView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SelectActionTargetPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A Queen's two cards stay up together until the swap is answered.
 *
 * Aiming picks two cards one tap at a time, and each aim used to be its own scene — so the
 * first card lifted, lowered itself while the player was still choosing the second, and the
 * second lifted alone. The aim is a *state*, held in the view until the action resolves, and
 * the table now lifts whatever the view holds (`heldUp`, `docs/kotlin/CHOREOGRAPHY.md`).
 *
 * What these cases pin, end to end through real frames from a real session: after both aims
 * have played and every scene is long over, both cards are still in the air — their slots
 * still held open, their faces up on the screen of the player looking. Declining the swap
 * puts both down again; taking it hands both to the flights, which land them in each other's
 * places. The first assertion is the one that fails against the scene-scoped lift.
 */
@OptIn(ExperimentalTestApi::class)
class QueenAimTest {

    @Test
    fun bothAimedCardsStayUpTogetherAndComeHomeWhenTheSwapIsDeclined() = runComposeUiTest {
        val play = runBlocking { QueenPlay(skipInsteadOfSwap = true) }
        mainClock.autoAdvance = false

        val frames = MutableStateFlow(emptyList<Frame>())
        var live by mutableStateOf(play.aimed.view)
        val layout = TableLayout.forScreen(PHONE_H)

        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CardStage(frames = frames, live = live, sizes = layout.sizes, pace = 1f) {
                        TableScreen(
                            state = TableState(it, tableFor(it), null, emptyList(), 1),
                            layout = layout,
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
        settle()

        // Both aims, played to the end and well past it.
        frames.value = play.aims
        settle()

        assertEquals(
            0,
            nodes(play.oppSlot),
            "the first card lowered itself while the second was being chosen: " +
                "the opponent's slot has its card back",
        )
        assertEquals(0, nodes(play.mySlot), "the second aimed card is not up")
        play.faces.forEach { face ->
            assertTrue(nodes(face) > 0, "the looker no longer sees $face held up")
        }

        // The decline: both cards jolt and come home. Nothing may stay in the air.
        live = play.resolved.view
        frames.value = listOf(play.resolved)
        settle()

        assertTrue(nodes(play.oppSlot) > 0, "the opponent's card never came home")
        assertTrue(nodes(play.mySlot) > 0, "your card never came home")
        play.faces.forEach { face ->
            assertEquals(0, nodes(face), "$face is still showing after the look ended")
        }
    }

    @Test
    fun executingTheSwapHandsBothCardsToTheFlights() = runComposeUiTest {
        val play = runBlocking { QueenPlay(skipInsteadOfSwap = false) }
        mainClock.autoAdvance = false

        val frames = MutableStateFlow(emptyList<Frame>())
        var live by mutableStateOf(play.aimed.view)
        val layout = TableLayout.forScreen(PHONE_H)

        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CardStage(frames = frames, live = live, sizes = layout.sizes, pace = 1f) {
                        TableScreen(
                            state = TableState(it, tableFor(it), null, emptyList(), 1),
                            layout = layout,
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
        settle()

        frames.value = play.aims
        settle()

        live = play.resolved.view
        frames.value = listOf(play.resolved)
        settle()

        assertTrue(nodes(play.oppSlot) > 0, "the swap left the opponent's slot empty")
        assertTrue(nodes(play.mySlot) > 0, "the swap left your slot empty")
        play.faces.forEach { face ->
            assertEquals(0, nodes(face), "$face is still hovering after the swap took it")
        }
    }

    /**
     * And the swap sets off from where the cards were hovering.
     *
     * The two cards are in the air when the player answers, so that is where their journey
     * starts. A flight that read its departure from the home slot would drop both cards back
     * into the hand for a frame and fly from there — the jump the whole state/moment split
     * exists to prevent, and one the sibling case above cannot see, because it asks only where
     * the cards ended up.
     *
     * Measured as a comparison rather than a coordinate: on the first frame of the answer,
     * each card is nearer to where it was hovering than to the slot it came from.
     */
    @Test
    fun theSwapSetsOffFromWhereTheCardsWereHovering() = runComposeUiTest {
        val play = runBlocking { QueenPlay(skipInsteadOfSwap = false) }
        mainClock.autoAdvance = false

        val frames = MutableStateFlow(emptyList<Frame>())
        var live by mutableStateOf(play.aimed.view)
        val layout = TableLayout.forScreen(PHONE_H)
        var scale = 1f

        setContent {
            scale = LocalDensity.current.density
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CardStage(frames = frames, live = live, sizes = layout.sizes, pace = 1f) {
                        TableScreen(
                            state = TableState(it, tableFor(it), null, emptyList(), 1),
                            layout = layout,
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
        settle()

        frames.value = play.aims
        settle()
        val hovers = play.faces.map { centre(it) }

        // The answer. Sampled finely, because a card dropping home for one frame and setting
        // off from there is exactly one frame of wrong.
        live = play.resolved.view
        frames.value = listOf(play.resolved)

        var setOff: List<Offset>? = null
        repeat(FIRST_FRAMES) {
            mainClock.advanceTimeBy(FINE_MS)
            val seen = play.faces.map { centreOrNull(it) }
            if (setOff == null && seen.all { it != null }) setOff = seen.filterNotNull()
        }

        // The slots themselves, read once the swap has landed and the hands are whole again.
        // A Queen exchanges two cards without changing either hand's size, so these are the
        // same two places the cards were lifted out of — different cards, same geometry.
        settle()
        val homes = listOf(play.oppSlot, play.mySlot).map { centre(it) }

        homes.zip(hovers).forEach { (home, hover) ->
            assertTrue(
                (hover - home).getDistance() > LIFTED_PX * scale,
                "the card never left its slot, so this case proves nothing",
            )
        }

        val from = setOff
        assertTrue(from != null, "neither card was drawn once the swap was answered")
        from!!.forEachIndexed { i, at ->
            val toHover = (at - hovers[i]).getDistance()
            val toHome = (at - homes[i]).getDistance()
            assertTrue(
                toHover < toHome,
                "card $i set off from its slot rather than from where it hovered: " +
                    "${toHome / scale}dp from home, ${toHover / scale}dp from the hover",
            )
        }
    }

    /**
     * A real game played to the moment of the decision, and the frames it emitted.
     *
     * Real frames from a real session rather than hand-made ones, because the defect lived in
     * the seams — between one aim's frame and the next — and hand-made frames would let the
     * test agree with itself rather than with the game.
     */
    private class QueenPlay private constructor() {
        lateinit var aims: List<Frame>
        lateinit var resolved: Frame
        lateinit var oppSlot: String
        lateinit var mySlot: String
        lateinit var faces: List<String>

        val aimed: Frame get() = aims.last()

        companion object {
            suspend operator fun invoke(skipInsteadOfSwap: Boolean): QueenPlay {
                val play = QueenPlay()
                val session = LocalGameSession(seed = SEED, difficulty = Difficulty.EASY)
                val me = session.playerId

                session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
                session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
                session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
                session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.QUEEN)))
                session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
                session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))

                val batches = mutableListOf<List<Frame>>()
                val collector = CoroutineScope(Dispatchers.Unconfined)
                val job = collector.launch { session.frames.collect { batches += it } }

                val opp = session.view.value.players.first { it.id != me }
                session.dispatch(
                    GameAction.SelectActionTarget(
                        SelectActionTargetPayload.Positional(me, opp.id, OPP_SLOT),
                    ),
                )
                session.dispatch(
                    GameAction.SelectActionTarget(
                        SelectActionTargetPayload.Positional(me, me, MY_SLOT),
                    ),
                )
                val answer = if (skipInsteadOfSwap) {
                    GameAction.SkipQueenSwap(PlayerIdPayload(me))
                } else {
                    GameAction.ExecuteQueenSwap(PlayerIdPayload(me))
                }
                session.dispatch(answer)
                job.cancel()

                val frames = batches.flatten()
                play.aims = frames.filter { it.action is GameAction.SelectActionTarget }
                play.resolved = frames.first { it.action.type == answer.type }

                val my = session.view.value.players.first { it.id == me }
                play.oppSlot = "${opp.nickname}, card ${OPP_SLOT + 1}"
                play.mySlot = "${my.nickname}, card ${MY_SLOT + 1}"

                // What the looker is shown: the two faces the Queen peeked, as the table
                // reads them out loud. Taken from the aimed view, which is the engine's say.
                play.faces = play.aimed.view.pendingAction?.targets.orEmpty().mapNotNull {
                    (it.card as? CardView.Visible)?.card
                }.map { "${it.rank.serialName}, worth ${it.value}" }
                check(play.faces.size == 2) { "the Queen should be holding two faces" }

                return play
            }
        }
    }

    private fun ComposeUiTest.centre(description: String): Offset =
        centreOrNull(description) ?: error("nothing on the table is described '$description'")

    private fun ComposeUiTest.centreOrNull(description: String): Offset? =
        onAllNodesWithContentDescription(description).fetchSemanticsNodes()
            .singleOrNull()?.boundsInRoot?.center

    private fun ComposeUiTest.nodes(description: String): Int =
        onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
    }

    private companion object {
        const val SEED = 31L
        const val OPP_SLOT = 0
        const val MY_SLOT = 3
        const val SETTLE_MS = 8_000L

        /** One display frame, near enough: the resolution a one-frame jump needs. */
        const val FINE_MS = 16L
        const val FIRST_FRAMES = 8

        /** How far a lift has to have moved a card for this case to be worth asking. */
        const val LIFTED_PX = 8f
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}
