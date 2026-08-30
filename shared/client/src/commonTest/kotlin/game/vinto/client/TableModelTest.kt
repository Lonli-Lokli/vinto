package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.projectView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the table offers, in each state a player can be in.
 *
 * Every case drives a real [LocalGameSession] rather than hand-building a view: a table read
 * from a state the engine cannot produce proves nothing, and the states worth testing are
 * exactly the awkward ones — a Jack half-aimed, a Queen that has looked and must now decide,
 * a toss-in window with cards selected. `SET_NEXT_DRAW_CARD` puts a chosen rank on top of the
 * deck, so each of those is two moves away rather than a hundred.
 */
class TableModelTest {

    private suspend fun started(seed: Long = 77L): LocalGameSession {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))
        return session
    }

    /** Deals [rank] to the player and leaves it pending, having played its action. */
    private suspend fun aiming(rank: Rank, seed: Long = 77L): LocalGameSession {
        val session = started(seed)
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(rank)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(session.playerId)))
        return session
    }

    private fun LocalGameSession.table(question: Question = Question.None) = tableFor(view.value, question)

    private fun Table.labels() = choices.map { it.label }

    private fun Table.send(label: Label): GameAction {
        val choice = choices.firstOrNull { it.label == label }
            ?: error("no $label among ${labels()}")
        return (choice.move as Move.Send).action
    }

    // ------------------------------------------------------------------ setup

    @Test
    fun setupAsksForTwoCardsAndThenToStart() = runTest {
        val session = LocalGameSession(seed = 3L, difficulty = Difficulty.EASY)

        val first = session.table()
        assertEquals(Ask.LookAtTwoOfYours, first.prompt)
        assertEquals(FIVE_CARDS, first.taps.size, "every card of mine is offered")
        assertTrue(first.taps.keys.all { it.playerId == session.playerId }, "and only mine")

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 2)))
        val second = session.table()
        assertEquals(Ask.OneMoreToLookAt, second.prompt)
        assertEquals(FIVE_CARDS - 1, second.taps.size, "the card already seen is not offered again")

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 3)))
        val ready = session.table()
        assertTrue(ready.taps.isEmpty(), "no more peeking")
        assertTrue(ready.send(Label.StartRound) is GameAction.FinishSetup)
    }

    // ------------------------------------------------------------------ a turn

    @Test
    fun aTurnStartsWithADrawAndNoWayToEndItEarly() = runTest {
        val table = started().table()

        assertEquals(Ask.YourTurn, table.prompt)
        assertTrue(table.send(Label.DrawCard) is GameAction.DrawCard)
        assertTrue(table.taps.isEmpty(), "nothing to touch until a card is drawn")

        // Vinto is declared at the end of a turn. The engine would accept it here and then
        // still expect the turn to be played, which is a state no button should be able to
        // produce — so the offer lives in the toss-in window instead.
        assertFalse(
            table.labels().any { it == Label.CallVinto },
            "a turn cannot be ended before it has been taken: ${table.labels()}",
        )
    }

    @Test
    fun aPlainCardCannotBePlayedButCanBeKeptOrThrownAway() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val table = session.table()
        assertEquals(Ask.YouDrew(Rank.FIVE), table.prompt)
        assertFalse(table.labels().any { it == Label.UseAction }, "a 5 has no action: ${table.labels()}")
        assertTrue(table.send(Label.Discard) is GameAction.DiscardCard)
    }

    @Test
    fun keepingACardAsksWhichSlotAndThenWhetherToCallIt() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val keep = session.table().choices.first { it.label == Label.SwapCards }.move
        assertEquals(Move.Ask(Question.WhichSlot), keep, "keeping a card is a question, not a move")

        val slots = session.table(Question.WhichSlot)
        assertEquals(FIVE_CARDS, slots.taps.size)
        assertEquals(Move.Ask(Question.CallRank(2)), slots.taps[CardRef(session.playerId, 2)])

        val calling = session.table(Question.CallRank(2))
        assertEquals(ALL_RANK_COUNT, calling.ranks.size, "any rank can be named")

        val silent = calling.send(Label.JustSwap) as GameAction.SwapCard
        assertEquals(2, silent.payload.position)
        assertNull(silent.payload.declaredRank, "saying nothing declares nothing")

        val named = (calling.ranks.first { it.rank == Rank.KING }.move as Move.Send).action
        assertEquals(Rank.KING, (named as GameAction.SwapCard).payload.declaredRank)
    }

    // ------------------------------------------------------------------ aiming actions

    @Test
    fun aPeekOwnOffersOnlyMyOwnCards() = runTest {
        val session = aiming(Rank.SEVEN)
        val table = session.table()

        assertEquals(Ask.LookAtOneOfYourOwn, table.prompt)
        assertTrue(table.taps.keys.all { it.playerId == session.playerId }, "only my hand")
        assertEquals(FIVE_CARDS, table.taps.size)
    }

    @Test
    fun aPeekOpponentOffersEveryHandButMine() = runTest {
        val session = aiming(Rank.NINE)
        val table = session.table()

        assertEquals(Ask.LookAtOneOfAnotherPlayers, table.prompt)
        assertTrue(table.taps.keys.none { it.playerId == session.playerId }, "not my own hand")
        assertEquals(THREE_OPPONENTS * FIVE_CARDS, table.taps.size)
    }

    @Test
    fun aPeekIsAcknowledgedOnceItHasBeenTaken() = runTest {
        val session = aiming(Rank.SEVEN)
        val target = session.table().taps.keys.first()

        session.dispatch((session.table().taps.getValue(target) as Move.Send).action)

        val seen = session.table()
        assertEquals(Ask.RememberIt, seen.prompt)
        assertTrue(seen.taps.isEmpty(), "the peek is spent")
        assertTrue(seen.send(Label.Done) is GameAction.ConfirmPeek)
    }

    @Test
    fun aJackTakesTwoCardsFromTwoDifferentPlayersAndThenAsks() = runTest {
        val session = aiming(Rank.JACK)

        val first = session.table()
        assertEquals(Ask.ChooseTwoFromDifferentPlayers, first.prompt)
        assertEquals(FOUR_SEATS * FIVE_CARDS, first.taps.size, "any card, to begin with")

        val mine = CardRef(session.playerId, 0)
        session.dispatch((first.taps.getValue(mine) as Move.Send).action)

        val second = session.table()
        assertTrue(
            second.taps.keys.none { it.playerId == session.playerId },
            "the rest of my hand is no longer a legal second target",
        )

        val theirs = second.taps.keys.first()
        session.dispatch((second.taps.getValue(theirs) as Move.Send).action)

        val decide = session.table()
        assertEquals(Ask.SwapThem, decide.prompt)
        assertTrue(decide.send(Label.SwapCards) is GameAction.ExecuteJackSwap)
        assertTrue(decide.send(Label.LeaveThem) is GameAction.SkipJackSwap)
    }

    @Test
    fun aQueenLooksFirstAndThenOffersItsOwnSwap() = runTest {
        val session = aiming(Rank.QUEEN)

        assertEquals(Ask.LookAtTwoFromDifferentPlayers, session.table().prompt)

        val mine = CardRef(session.playerId, 0)
        session.dispatch((session.table().taps.getValue(mine) as Move.Send).action)
        val theirs = session.table().taps.keys.first()
        session.dispatch((session.table().taps.getValue(theirs) as Move.Send).action)

        val decide = session.table()
        assertEquals(Ask.SwapThem, decide.prompt)
        assertTrue(decide.send(Label.SwapCards) is GameAction.ExecuteQueenSwap, "a Queen's swap, not a Jack's")
    }

    @Test
    fun aKingNamesACardAndThenARank() = runTest {
        val session = aiming(Rank.KING)

        val choosing = session.table()
        assertEquals(Ask.ChooseAnyCard, choosing.prompt)
        assertTrue(choosing.ranks.isEmpty(), "nothing to declare until a card is chosen")

        session.dispatch((choosing.taps.values.first() as Move.Send).action)

        val declaring = session.table()
        assertEquals(ALL_RANK_COUNT, declaring.ranks.size)
        val king = (declaring.ranks.first { it.rank == Rank.NINE }.move as Move.Send).action
        assertEquals(Rank.NINE, (king as GameAction.DeclareKingAction).payload.declaredRank)
    }

    @Test
    fun anAceNamesAPlayerRatherThanACard() = runTest {
        val session = aiming(Rank.ACE)
        val table = session.table()

        assertEquals(Ask.WhoDrawsACard, table.prompt)
        assertTrue(table.taps.isEmpty(), "an Ace has no card to aim at")
        assertEquals(THREE_OPPONENTS, table.seatTaps.size, "and cannot be aimed at myself")
        assertFalse(session.playerId in table.seatTaps)
    }

    // ------------------------------------------------------------------ toss-in

    @Test
    fun aDiscardOpensATossInYouTakeByTouchingACard() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))

        val table = session.table()
        assertEquals(Ask.TossIn(listOf(Rank.FIVE), barred = false), table.prompt)
        assertTrue(table.send(Label.Continue) is GameAction.PlayerTossInFinished)
        assertTrue(table.send(Label.CallVinto) is GameAction.CallVinto, "your own turn is ending")

        // Throwing a card in is one touch, with no confirmation step. The risk is what makes
        // it a decision; a confirmation would only make a bad idea slower.
        assertEquals(FIVE_CARDS, table.taps.size, "any of your cards can go in")
        val thrown = (table.taps.getValue(CardRef(session.playerId, 3)) as Move.Send).action
        assertEquals(
            listOf(3),
            (thrown as GameAction.ParticipateInTossIn).payload.positions,
            "the card you touched, and only that one",
        )
    }

    /**
     * The rules of whatever is happening, so a player never has to leave the table.
     *
     * The model's half of the claim: *which* explanation, and about which card. That the
     * rendered paragraph actually names the Queen, says what it does and says what it costs
     * is `CardHelpTest` in composeApp, where the words live — the two halves together are what
     * the single string assertion used to cover.
     */
    @Test
    fun everyStateExplainsItself() = runTest {
        val fresh = LocalGameSession(seed = 5L, difficulty = Difficulty.EASY)
        assertEquals(Explains.HowSetupWorks, fresh.table().help)

        val session = aiming(Rank.QUEEN)
        assertEquals(Explains.TheCardInPlay(Rank.QUEEN), session.table().help)
    }

    /** A drawn action card says what it does without being asked. */
    @Test
    fun aDrawnActionCardExplainsItselfInline() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.KING)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))

        val table = session.table()
        assertEquals(Ask.YouDrew(Rank.KING), table.prompt)
        assertEquals(Detail.WhatTheCardDoes(Rank.KING), table.detail)
    }

    // ------------------------------------------------------------------ what is shown

    /**
     * The two cards you looked at during setup go face-down again when play starts — and the
     * view stops carrying them at all.
     *
     * It used to carry them all round, with the screen politely declining to draw them. That
     * put the answer in the client and made not-looking a matter of trust, which is no
     * protection from a client we did not write. The engine still knows what each seat has
     * learned, because the bots and the scoring need it; the seat is told *which* cards it
     * has seen, which is public, and not what they were.
     */
    @Test
    fun yourSetupPeeksAreYoursToRememberOncePlayBegins() = runTest {
        val session = LocalGameSession(seed = 11L, difficulty = Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))

        val duringSetup = session.table().revealed
        assertEquals(
            setOf(CardRef(session.playerId, 0), CardRef(session.playerId, 1)),
            duringSetup,
            "while you are told to look, you can see them",
        )

        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))

        assertTrue(session.table().revealed.isEmpty(), "and then they are yours to remember")

        val me = session.view.value.players.first { it.id == session.playerId }
        assertEquals(listOf(0, 1), me.knownCardPositions, "which cards you looked at is public")
        assertTrue(me.cards[0] !is CardView.Visible, "what they were is not sent any more")
        assertTrue(me.cards[1] !is CardView.Visible)
    }

    @Test
    fun anActionShowsWhatItRevealsAndOnlyWhileItIsRunning() = runTest {
        val session = aiming(Rank.SEVEN)
        assertTrue(session.table().revealed.isEmpty(), "nothing yet")

        val target = session.table().taps.keys.first()
        session.dispatch((session.table().taps.getValue(target) as Move.Send).action)

        assertEquals(setOf(target), session.table().revealed, "the card the 7 looked at")

        session.dispatch(GameAction.ConfirmPeek(PlayerIdPayload(session.playerId)))
        assertFalse(target in session.table().revealed, "and once the card is put down, not any more")
    }

    @Test
    fun everyHandGoesFaceUpAtScoring() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        session.dispatch(session.table().send(Label.CallVinto))

        assertTrue(session.isOver)
        // Every card on the table, however many that has become — a round adds penalty cards
        // and takes tossed-in ones away, so counting five a hand would be counting the deal
        // rather than the game.
        val onTable = session.view.value.players.sumOf { it.cards.size }
        assertEquals(onTable, session.table().revealed.size)
        assertTrue(onTable >= FOUR_SEATS, "there are hands to turn over")
    }

    /**
     * Being barred from tossing in is said out loud.
     *
     * It is a rule a player breaks once and then cannot see they have broken: the window
     * would simply stop taking cards, with nothing to tell "you are barred for the round"
     * apart from "you were too slow".
     */
    @Test
    fun aPlayerWhoThrewInAWrongCardIsToldTheyAreOut() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))

        // Throw cards in until one of them is a wrong guess, which is what bars a player.
        // The old version threw position 4 — never peeked, so almost certainly not a 5 — and
        // *returned* when it happened to match, which made the whole case vacuous whenever the
        // deal was unkind. A five has no action, so a right guess simply shortens the hand and
        // leaves the window open for the next attempt.
        repeat(session.view.value.players.first { it.id == session.playerId }.cards.size) {
            if (session.playerId in session.view.value.barredFromTossIn) return@repeat
            val tap = session.table().taps.entries
                .firstOrNull { it.key.playerId == session.playerId }
                ?: return@repeat
            session.dispatch((tap.value as Move.Send).action)
        }
        assertTrue(
            session.playerId in session.view.value.barredFromTossIn,
            "the whole hand matched the discard, which this seed does not do",
        )

        val after = session.table()
        assertTrue(after.taps.isEmpty(), "no card can be thrown in any more")
        assertEquals(
            Detail.BarredFromThisCard,
            after.detail,
            "and it says why — and that the next card to land is a fresh chance, because " +
                "outside the final round the bar is the window rather than the round",
        )
        assertTrue(after.send(Label.Continue) is GameAction.PlayerTossInFinished)
        assertTrue(
            after.send(Label.CallVinto) is GameAction.CallVinto,
            "and you can still end your own turn — being barred is not a second penalty",
        )
    }

    // ------------------------------------------------------------------ watching

    @Test
    fun aSeatWithNothingToDoIsToldWhoIsPlaying() = runTest {
        val session = started()
        val someBot = session.view.value.players.first { it.isBot }.id

        val theirs = tableFor(projectView(session.state, someBot))

        assertTrue(theirs.waiting, "a bot's view of my turn is a waiting one")
        assertIs<Ask.SomebodyIsPlaying>(theirs.prompt, "${theirs.prompt}")
        assertTrue(theirs.choices.isEmpty() && theirs.taps.isEmpty(), "and offers nothing")
    }

    @Test
    fun aFinishedRoundSaysHowItWent() = runTest {
        val session = started()
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))

        // The window after your own discard is where Vinto belongs, and the only place the
        // table offers it.
        session.dispatch(session.table().send(Label.CallVinto))

        // Calling Vinto hands the round to the coalition: the bots nominate a leader and play
        // their last turn between them, so by the time control returns the round is scored.
        assertTrue(session.isOver, "the final round played itself out")

        val table = session.table()
        assertTrue(table.waiting)
        assertIs<Ask.RoundOver>(table.prompt, "${table.prompt}")
    }

    private companion object {
        const val FIVE_CARDS = 5
        const val FOUR_SEATS = 4
        const val THREE_OPPONENTS = 3
        const val ALL_RANK_COUNT = 14
    }
}
