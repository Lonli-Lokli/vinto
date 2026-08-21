package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SwapCardPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What there is to see, and what there deliberately is not.
 *
 * Worth testing rather than watching: a scene is a third of a second long, too short to catch
 * on a screenshot and long enough for a player to notice a card going the wrong way. These are
 * also the cases that will matter most online, where the same function runs against views that
 * arrived over a socket.
 */
@OptIn(ExperimentalCoroutinesApi::class) // `runCurrent`, to drain the scene collector deterministically.
class ChoreographyTest {

    /**
     * The scenes a session has emitted, one entry per dispatch.
     *
     * A dispatch emits one frame per *move* — the player's, then each bot's — and these cases
     * are about what the player is shown rather than about whose move it was, so the frames
     * are flattened back to their scenes here.
     */
    private fun TestScope.scenesOf(session: LocalGameSession): List<List<Scene>> {
        val seen = mutableListOf<List<Scene>>()
        backgroundScope.launch {
            session.frames.collect { batch -> seen.add(batch.flatMap { it.scenes }) }
        }
        runCurrent()
        return seen
    }

    private suspend fun started(seed: Long = 8L): LocalGameSession {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))
        return session
    }

    /** Deals [rank] to the player and leaves it pending, having played its action. */
    private suspend fun aiming(rank: Rank, seed: Long = 8L): LocalGameSession {
        val session = started(seed)
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(rank)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(session.playerId)))
        return session
    }

    private fun List<List<Scene>>.moves(): List<Beat.Move> =
        flatten().flatten().filterIsInstance<Beat.Move>()

    @Test
    fun drawingBringsACardFromTheDeckFaceUp() = runTest {
        val session = started()
        val scenes = scenesOf(session)

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        runCurrent()

        val drawn = scenes.last().first().first() as Beat.Move
        assertEquals(Anchor.Deck, drawn.from)
        assertEquals(Anchor.Pending, drawn.to)
        assertEquals(Rank.NINE, drawn.card?.rank, "your own draw flies face-up")
    }

    /**
     * A swap is two cards crossing, and the order is the point: the new one goes into the
     * hand, *then* the old one comes out onto the pile.
     *
     * Two scenes rather than one, which is a correction. Played together the pair reads as
     * nothing at all — two cards passing at the same instant, over in a third of a second,
     * and what the player sees is that the table changed. Played in order it is what a person
     * does with their hands, and the beat between them is the one every pair of scenes gets.
     */
    @Test
    fun aSwapIsTwoCardsCrossingInOrder() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val scenes = scenesOf(session)
        session.dispatch(GameAction.SwapCard(SwapCardPayload(session.playerId, 2)))
        runCurrent()

        val move = scenes.last()
        val seat = Anchor.Seat(session.playerId, 2)

        val flights = move.flatten().filterIsInstance<Beat.Move>()
        assertEquals(2, flights.size, "two cards move")
        assertEquals(2, move.count { it.any { beat -> beat is Beat.Move } }, "one each, in turn")

        val (into, out) = flights
        assertEquals(Anchor.Pending to seat, into.from to into.to, "the drawn card goes in first")
        assertEquals(Rank.FIVE, into.card?.rank)
        assertEquals(seat to Anchor.Discard, out.from to out.to, "and the old one follows it out")
    }

    /**
     * A card somebody else drew flies **face up**, and turns over on the way.
     *
     * The rules have the drawn card revealed publicly ("draws top card from draw pile and
     * reveals it publicly"), and it is the most informative moment of anybody else's turn —
     * what they drew, and what they then chose to do with it, is most of what there is to
     * learn about a hand. The spin is what marks the card as theirs rather than yours.
     *
     * This asserted the opposite until it was played on a phone: three bots taking their
     * turns behind a screen is not a game anybody can learn or play well.
     */
    @Test
    fun aCardDrawnByABotFliesFaceUpAndTurnsOverOnTheWay() = runTest {
        val session = started()
        val scenes = scenesOf(session)

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId)))
        runCurrent()

        val draws = scenes.moves().filter { it.from == Anchor.Deck && it.to == Anchor.Pending }
        assertTrue(draws.size > 1, "the bots drew too: ${draws.size}")

        val theirs = draws.last()
        assertNotNull(theirs.card, "what a bot drew is on the table for everyone to read")
        assertTrue(theirs.spin, "and it turns over on the way, so it reads as theirs")
    }

    /** A peek lifts a card where it lies. Nothing travels. */
    @Test
    fun aPeekLiftsACardRatherThanMovingIt() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.SEVEN)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(session.playerId)))

        val scenes = scenesOf(session)
        val target = session.table().taps.keys.first()
        session.dispatch((session.table().taps.getValue(target) as Move.Send).action)
        runCurrent()

        val peeks = scenes.flatten().flatten().filterIsInstance<Beat.Peek>()
        assertEquals(1, peeks.size, "one card looked at")
        assertEquals(Anchor.Seat(target.playerId, target.position), peeks.single().at)
        assertTrue(peeks.single().card != null, "and you are the one looking, so you see it")
        assertTrue(scenes.moves().isEmpty(), "nothing moved")
    }

    /**
     * Somebody else's peek is public — which card, not what it was.
     *
     * This is real information rather than decoration: it is how you know a bot has just
     * learned one of its own cards, or read one of yours. Before this, another player's peek
     * was invisible and the only clue was a card changing hands two turns later.
     */
    @Test
    fun anotherPlayersPeekIsSeenButNotRead() = runTest {
        val session = started()
        val scenes = scenesOf(session)

        // Hand the turn to the bots and let them play until one peeks at something.
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId)))
        runCurrent()

        val theirs = scenes.flatten().flatten()
            .filterIsInstance<Beat.Peek>()
            .filter { (it.at as Anchor.Seat).playerId != session.playerId }

        if (theirs.isEmpty()) return@runTest // no bot happened to peek in this deal

        assertTrue(
            theirs.all { it.card == null },
            "you see that they looked, never at what: ${theirs.map { it.card?.rank }}",
        )
    }

    /** An Ace has no card to lift, so it points at the player instead. */
    @Test
    fun anAcePointsAtWhoeverHasToDraw() = runTest {
        val session = aiming(Rank.ACE)
        val scenes = scenesOf(session)

        val victim = session.table().seatTaps.keys.first()
        session.dispatch((session.table().seatTaps.getValue(victim) as Move.Send).action)
        runCurrent()

        val beats = scenes.flatten().flatten()
        assertTrue(
            beats.any { it is Beat.Attend && it.playerId == victim },
            "the table points at them: $beats",
        )
    }

    /** Playing an action card holds it up before it goes down. */
    @Test
    fun anActionCardIsHeldUpBeforeItIsPlayed() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val scenes = scenesOf(session)
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(session.playerId)))
        runCurrent()

        val staged = scenes.flatten().flatten().filterIsInstance<Beat.Stage>()
        assertEquals(Rank.NINE, staged.singleOrNull()?.card?.rank, "the 9 is shown: $staged")
    }

    /** A card somebody else drew turns over on the way, so it reads as theirs. */
    @Test
    fun aBotsDrawSpinsAndYoursDoesNot() = runTest {
        val session = started()
        val scenes = scenesOf(session)

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId)))
        runCurrent()

        val draws = scenes.moves().filter { it.from == Anchor.Deck && it.to == Anchor.Pending }
        assertFalse(draws.first().spin, "your own draw does not spin")
        assertTrue(draws.drop(1).all { it.spin }, "everybody else's does")
    }

    /** A declaration is answered, in green or in red. */
    @Test
    fun aDeclarationGetsAVerdict() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val scenes = scenesOf(session)
        session.dispatch(
            GameAction.SwapCard(game.vinto.shapes.SwapCardPayload(session.playerId, 4, Rank.KING)),
        )
        runCurrent()

        val verdicts = scenes.flatten().flatten().filterIsInstance<Beat.Verdict>()
        assertEquals(1, verdicts.size, "the call was answered: $verdicts")
    }

    /**
     * A penalty is a card arriving in a hand, and it is derived from the hand growing rather
     * than from the action that caused it — several actions can, and one added later animates
     * without anybody remembering to teach the choreography about it.
     */
    @Test
    fun aWrongCallIsSeenAsACardArrivingAndAHandFlinching() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val scenes = scenesOf(session)
        // Position 4 was never peeked, so calling a rank for it is a guess — and a wrong one
        // unless the deal is very kind.
        session.dispatch(GameAction.SwapCard(SwapCardPayload(session.playerId, 4, Rank.KING)))
        runCurrent()

        val beats = scenes.flatten().flatten()
        val flinched = beats.filterIsInstance<Beat.Flinch>()
        if (flinched.isEmpty()) return@runTest // the call happened to be right; nothing owed

        assertTrue(
            beats.any { it is Beat.Move && it.from == Anchor.Deck && it.to == flinched.first().at },
            "the penalty card came off the deck to where the hand flinched",
        )
        assertTrue(
            beats.any { it is Beat.Say && it.playerId == session.playerId },
            "and the seat said something about it",
        )
    }

    @Test
    fun callingVintoIsAnnouncedRatherThanMoved() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))

        val scenes = scenesOf(session)
        session.dispatch(session.table().send("Call Vinto"))
        runCurrent()

        val beats = scenes.flatten().flatten()
        assertTrue(beats.any { it is Beat.Say && it.line == "Vinto!" }, "somebody said it")
        assertTrue(
            beats.any { it is Beat.Attend && it.kind == Attention.VINTO },
            "and the table was pointed at them",
        )
    }

    /**
     * The turn moving is its own beat, because it is nobody's move.
     *
     * It happens in the engine's bookkeeping at the end of whatever action finished a turn,
     * which is exactly why it went unnoticed in the web app too: there is no action to hang
     * it on, so nothing was hanging anything on it.
     */
    @Test
    fun theTurnPassingIsAnnounced() = runTest {
        val session = started()
        val scenes = scenesOf(session)

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId)))
        runCurrent()

        val handoffs = scenes.flatten().flatten()
            .filterIsInstance<Beat.Attend>()
            .filter { it.kind == Attention.TURN }

        assertTrue(handoffs.isNotEmpty(), "the turn changed hands and said so")
        assertTrue(
            handoffs.none { it.playerId == session.playerId },
            "and it was somebody else's: ${handoffs.map { it.playerId }}",
        )
    }

    /** A King says what it is pretending to be before it does that card's job. */
    @Test
    fun aKingNamesWhatItBorrowed() = runTest {
        val session = aiming(Rank.KING)
        session.dispatch((session.table().taps.values.first() as Move.Send).action)

        val scenes = scenesOf(session)
        val nine = session.table().ranks.first { it.rank == Rank.NINE }
        session.dispatch((nine.move as Move.Send).action)
        runCurrent()

        val borrowed = scenes.flatten().flatten().filterIsInstance<Beat.Borrowed>()
        assertEquals(Rank.NINE, borrowed.singleOrNull()?.rank, "it said which: $borrowed")
    }

    private fun Table.send(startsWith: String): GameAction {
        val choice = choices.first { it.label.startsWith(startsWith) }
        return (choice.move as Move.Send).action
    }

    private fun LocalGameSession.table() = tableFor(view.value)
}
