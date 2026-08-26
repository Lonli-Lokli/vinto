package game.vinto.client

import game.vinto.engine.projectView
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
     * A swap is two cards crossing, and they cross *at the same time*.
     *
     * One scene, therefore, which is what a scene is for: everything in it starts together
     * and the scene lasts as long as its longest beat. Splitting the pair into two scenes was
     * tried and is wrong — a swap is one gesture, not a card going in and later another
     * coming out, and pulling it apart makes the table look like it is thinking between two
     * halves of a move it already made.
     *
     * The order within the scene still says which is which: in first, out second.
     */
    @Test
    fun aSwapIsTwoCardsCrossingAtOnce() = runTest {
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
        assertEquals(
            1,
            move.count { scene -> scene.any { it is Beat.Move } },
            "in one scene, so they cross together",
        )

        val (into, out) = flights
        assertEquals(Anchor.Pending to seat, into.from to into.to, "the drawn card goes in")
        assertEquals(Rank.FIVE, into.card?.rank)
        assertEquals(seat to Anchor.Discard, out.from to out.to, "as the old one comes out")
    }

    /**
     * A correct call sends the card it named to the pile, lit.
     *
     * Guessing right is the one moment in a turn a player has *proved* something, and the
     * proof is the card: it comes out of the hand face up so the other three can read it.
     *
     * To the **pile**, like every other swapped-out card, which is worth a case of its own
     * because the engine gets there by a different route: a correct call makes the card the
     * pending action rather than discarding it outright. A card in play lies on the discard,
     * so that is where it goes. Animating it to the drawn slot instead — which is what the
     * pending action *sounds* like — sent it visibly to the wrong end of the table.
     */
    @Test
    fun aCorrectCallShowsTheNamedCardOnItsWayToThePile() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val view = projectView(session.state, session.playerId)
        val mine = view.players.first { it.id == session.playerId }
        val calledPosition = 0
        val calledRank = session.state.players
            .first { it.id == session.playerId }.cards[calledPosition].rank

        val scenes = scenesOf(session)
        session.dispatch(
            GameAction.SwapCard(SwapCardPayload(session.playerId, calledPosition, calledRank)),
        )
        runCurrent()

        val seat = Anchor.Seat(session.playerId, calledPosition)
        val out = scenes.last().flatten().filterIsInstance<Beat.Move>().first { it.from == seat }

        assertEquals(Anchor.Discard, out.to, "onto the pile, where a card in play lies")
        assertEquals(calledRank, out.card?.rank, "and it is the card that was named")
        assertTrue(out.shown, "travelling lit, because the table has to see the call was good")
        assertTrue(mine.cards.isNotEmpty())
    }

    /**
     * A card somebody else drew flies **face up**.
     *
     * The rules have the drawn card revealed publicly ("draws top card from draw pile and
     * reveals it publicly"), and it is the most informative moment of anybody else's turn —
     * what they drew, and what they then chose to do with it, is most of what there is to
     * learn about a hand. The spin is what marks the card as theirs rather than yours.
     *
     * This asserted the opposite until it was played on a phone: three bots taking their
     * turns behind a screen is not a game anybody can learn or play well.
     *
     * It used to turn over on the way as well, to mark the card as somebody else's. It does
     * not any more: a card crossing a table does not spin, and the drawn slot in the middle
     * already says whose turn it is.
     */
    @Test
    fun aCardDrawnByABotFliesFaceUp() = runTest {
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

        // The last batch is the aiming itself; the ones before it are the draw and the play,
        // and the play flies the card to the pile, which is not what this case is about.
        val aiming = scenes.last().flatten()

        val peeks = aiming.filterIsInstance<Beat.Peek>()
        assertEquals(1, peeks.size, "one card looked at")
        assertEquals(Anchor.Seat(target.playerId, target.position), peeks.single().at)
        assertTrue(peeks.single().card != null, "and you are the one looking, so you see it")
        assertTrue(aiming.filterIsInstance<Beat.Move>().isEmpty(), "nothing moved")
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

    /**
     * Playing an action card sends it to the pile, lit.
     *
     * It used to be lifted in place — held up in the middle of the felt and set down again
     * while the pile quietly filled in behind it — which is a card teleporting with a flourish
     * in front of it. Playing a card *puts it on the pile*, so it goes there, and the light it
     * travels in is what makes the loudest moment of a turn read as one.
     */
    @Test
    fun playingAnActionCardSendsItToThePileLit() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val scenes = scenesOf(session)
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(session.playerId)))
        runCurrent()

        val played = scenes.moves().single { it.to == Anchor.Discard }
        assertEquals(Anchor.Pending, played.from, "from where it was waiting")
        assertEquals(Rank.NINE, played.card?.rank)
        assertTrue(played.shown, "and lit, because everybody is being shown it")
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

    // ------------------------------------------------------------------ the deal

    @Test
    fun theDealIsWavesOfFaceDownCardsCoveringEverySlotOnce() = runTest {
        val view = LocalGameSession(seed = 8L, difficulty = Difficulty.EASY).view.value

        val deal = dealScenes(view)
        assertEquals(DEALT, deal.size, "one wave per card of the deal")

        val moves = deal.flatten().map { beat ->
            beat as? Beat.Move ?: error("the deal contained a $beat")
        }
        moves.forEach { move ->
            assertEquals(Anchor.Deck, move.from, "every card comes off the deck")
            assertNull(move.card, "a dealt card flies face-down")
            assertTrue(move.quick, "a deal is dealt at a dealer's tempo")
        }

        // Twenty flights, one to every slot of every seat, no slot twice.
        val slots = moves.map { it.to }
        assertEquals(view.players.size * DEALT, slots.size)
        assertEquals(slots.size, slots.toSet().size, "a slot was dealt to twice")
        view.players.forEach { seat ->
            repeat(DEALT) { position ->
                assertTrue(
                    Anchor.Seat(seat.id, position) in slots,
                    "${seat.id} slot $position was never dealt",
                )
            }
        }

        // And within a wave, one card to each seat — the dealer goes round the table.
        deal.forEach { wave ->
            val seats = wave.filterIsInstance<Beat.Move>()
                .map { (it.to as Anchor.Seat).playerId }
            assertEquals(seats.toSet().size, seats.size, "a wave dealt one seat twice")
        }
    }

    // ------------------------------------------------------------------ the reveal

    @Test
    fun scoringTurnsTheHandsOverSeatBySeatInTableOrder() = runTest {
        val session = started()
        val scenes = scenesOf(session)

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        tableFor(session.view.value).choices.firstOrNull { it.label == "Call Vinto" }?.let {
            session.dispatch((it.move as Move.Send).action)
        }
        runCurrent()

        assertEquals(
            game.vinto.shapes.GamePhase.SCORING,
            session.view.value.phase,
            "seed 8 is pinned because it calls and completes; it stopped doing so",
        )

        // The reveal rides the round-ending frames: scenes that are nothing but cards
        // turning over, one seat at a time.
        val reveals = scenes.flatten()
            .filter { scene -> scene.isNotEmpty() && scene.all { it is Beat.Reveal } }
            .filter { scene ->
                scene.filterIsInstance<Beat.Reveal>()
                    .map { (it.at as Anchor.Seat).playerId }
                    .toSet().size == 1
            }

        assertTrue(reveals.size >= 2, "the hands turned over together rather than seat by seat")

        // In table order, for every viewer alike: an order that favoured the viewer would
        // give two seats different scripts for one moment, which the visibility matrix
        // forbids.
        val order = reveals.map {
            (it.first() as Beat.Reveal).at.let { at -> (at as Anchor.Seat).playerId }
        }
        val table = session.view.value.players.map { it.id }.filter { it in order }
        assertEquals(table, order, "the hands turn in table order")
    }

    @Test
    fun aTableAlreadyAtScoringRevealsNothingAgain() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        tableFor(session.view.value).choices.firstOrNull { it.label == "Call Vinto" }?.let {
            session.dispatch((it.move as Move.Send).action)
        }
        val over = session.view.value
        assertEquals(game.vinto.shapes.GamePhase.SCORING, over.phase, "seed 8 stopped completing")

        // A resumed game opens on the scored table; replaying the reveal on every glance is
        // a reveal that stops meaning anything.
        assertTrue(scoringScenes(over, over).isEmpty())
    }

    private fun Table.send(startsWith: String): GameAction {
        val choice = choices.first { it.label.startsWith(startsWith) }
        return (choice.move as Move.Send).action
    }

    private fun LocalGameSession.table() = tableFor(view.value)

    private companion object {
        const val DEALT = 5
    }
}
