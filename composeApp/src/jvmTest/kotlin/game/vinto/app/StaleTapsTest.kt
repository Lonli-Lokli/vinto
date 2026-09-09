package game.vinto.app

import androidx.compose.runtime.mutableStateOf
import game.vinto.app.game.GameHolder
import game.vinto.app.game.showsTheSameHandAs
import game.vinto.app.game.withoutStaleTaps
import game.vinto.client.CardRef
import game.vinto.client.Question
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A tap that would land on a card the engine has already moved is withheld.
 *
 * The table is drawn as it was after the move being animated, and its taps come from that
 * picture; while the picture lags the engine, "card 3" means two different cards. That is how
 * a second tap on a slot just thrown from cost a penalty. Only the viewer's own hand decides:
 * somebody else's throw leaves this player's window open, which online is the whole point.
 */
class StaleTapsTest {

    @Test
    fun aHandTheEngineHasChangedTakesNoTapsUntilTheScreenCatchesUp() {
        val shown = tossWindow()
        val table = tableFor(shown, Question.None)
        val me = shown.viewerId
        assertTrue(table.taps.keys.any { it.playerId == me }, "the window offers this player's cards")

        // The engine has already taken one card off this hand; the screen still shows five.
        val live = shown.copy(
            players = shown.players.map { seat ->
                if (seat.id == me) seat.copy(cards = seat.cards.drop(1)) else seat
            },
        )
        val guarded = table.withoutStaleTaps(shown, live)
        assertTrue(
            guarded.taps.keys.none { it.playerId == me },
            "a stale tap got through: ${guarded.taps.keys}",
        )
        assertEquals(table.copy(taps = guarded.taps), guarded, "nothing but the taps changed")
    }

    @Test
    fun somebodyElsesThrowLeavesThisPlayersTapsAlone() {
        val shown = tossWindow()
        val table = tableFor(shown, Question.None)
        val other = shown.players.first { it.id != shown.viewerId }
        val live = shown.copy(
            players = shown.players.map { seat ->
                if (seat.id == other.id) seat.copy(cards = seat.cards.drop(1)) else seat
            },
        )
        assertEquals(table, table.withoutStaleTaps(shown, live))
    }

    @Test
    fun aScreenThatHasCaughtUpKeepsEveryTap() {
        val shown = tossWindow()
        val table = tableFor(shown, Question.None)
        assertEquals(table, table.withoutStaleTaps(shown, shown))
        assertTrue(table.taps.containsKey(CardRef(shown.viewerId, 0)))
    }

    /**
     * The same question the coach's finger has to ask.
     *
     * A lesson points at slots — the card to give up, the one to look at, the match to throw
     * in — and it picks them from the seat's *memory*, which is the engine's. While the screen
     * is a card behind, position two means one card to the engine and its neighbour on the
     * felt, so the finger slid along while the player watched. One predicate answers it for
     * the taps and for the pointer, so the two cannot disagree about which moment they are in.
     */
    @Test
    fun aHandTheEngineHasChangedIsNotTheHandOnScreen() {
        val shown = tossWindow()
        val me = shown.viewerId
        val live = shown.copy(
            players = shown.players.map { seat ->
                if (seat.id == me) seat.copy(cards = seat.cards.drop(1)) else seat
            },
        )

        assertTrue(shown.showsTheSameHandAs(shown), "a screen that has caught up is settled")
        assertFalse(shown.showsTheSameHandAs(live), "a hand that lost a card is not settled")
    }

    /**
     * Every screen asks for its table through ONE function, and that function withholds them.
     *
     * The guard above was written for the online case — its own KDoc says so, "which online is
     * the moment two people throw at once" — and online was the screen that did not call it.
     * Solo did. That asymmetry is the bug this asserts away: `GameHolder.tableAsShown` is now
     * what the solo screen, the room screen and the lesson all draw from, so there is no second
     * place left to forget it. Online is also where forgetting costs most: the room applies
     * whatever index arrives, `ClientMessage.Action` carries no id to dedupe by, and the
     * validator never checks whether this seat has already thrown.
     */
    @Test
    fun theTableAScreenDrawsWithholdsStaleTapsWhoeverIsDrawingIt() {
        val shown = tossWindow()
        val me = shown.viewerId
        // The engine has moved on; the felt is still a card behind.
        val live = shown.copy(
            players = shown.players.map { seat ->
                if (seat.id == me) seat.copy(cards = seat.cards.drop(1)) else seat
            },
        )

        val lagging = GameHolder(teachingSession(), mutableStateOf(live))
        assertTrue(
            lagging.tableAsShown(shown).taps.keys.none { it.playerId == me },
            "a stale tap got through: ${lagging.tableAsShown(shown).taps.keys}",
        )

        val caughtUp = GameHolder(teachingSession(), mutableStateOf(shown))
        assertTrue(
            caughtUp.tableAsShown(shown).taps.keys.any { it.playerId == me },
            "a settled screen lost the taps it should keep",
        )
    }

    /**
     * No screen draws a table straight from [game.vinto.app.game.GameHolder.tableFor].
     *
     * The behaviour above is only half the guarantee. The room screen's bug was not that the
     * guard was wrong — it was that the room never called it, for months, while solo did. One
     * correct function and one screen that does not reach it is exactly the shape that came
     * back, so this reads the screens instead of trusting them: a table on a screen comes from
     * `tableAsShown`, which cannot be spelled without the guard.
     *
     * Source-level on purpose, like `BuildScriptCommentsTest` — the alternative is standing up a
     * whole room to prove one call site, and this catches the next screen too, which no
     * behavioural test of today's two can.
     */
    @Test
    fun everyScreenTakesItsTableThroughTheGuardedHelper() {
        val screens = File(repoRoot(), "composeApp/src/commonMain/kotlin/game/vinto/app/game")
            .listFiles { f: File -> f.isFile && f.name.endsWith("Screen.kt") }
            .orEmpty()
            .toList()

        // A walk that found nothing would pass while checking nothing at all.
        assertTrue(screens.size >= 3, "only found ${screens.size} screens — the walk is wrong")

        val unguarded = screens.filter { file ->
            file.readLines().any { line ->
                val code = line.substringBefore("//").trim()
                "holder.tableFor(" in code || "tableFor(shown" in code
            }
        }.map { it.name }

        assertTrue(
            unguarded.isEmpty(),
            "these screens build a table without withholding stale taps — use `tableAsShown`: $unguarded",
        )
    }

    /** The repository, found from the test's own working directory — see `BuildScriptCommentsTest`. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("no settings.gradle.kts above ${System.getProperty("user.dir")}")
    }

    /** A toss-in window on the player's own discard, where their cards are tappable. */
    private fun tossWindow(): PlayerView {
        lateinit var view: PlayerView
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
            view = session.view.value
        }
        return view
    }
}
