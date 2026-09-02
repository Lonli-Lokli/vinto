package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What a two-card action is pointed at, as the table says it.
 *
 * The rail has room for one card beside its words, and it spent that room on the card doing
 * the pointing — while the pair being swapped, which is the whole decision, was a line of
 * small text. So the table now carries the aim itself: two ends, named and faced, filled as
 * they are chosen.
 *
 * The face is the part worth pinning here rather than only on the screen. A Queen looks
 * before it swaps and a Jack does not, and that difference is decided in `projectView` — the
 * aim only carries what it was handed. A day when it starts deciding for itself is a day a
 * Jack shows a card nobody was allowed to see, and it is this file that says so.
 */
class AimTest {

    @Test
    fun anAimIsOfferedFromTheMomentTheActionStartsWithBothEndsEmpty() = runTest {
        val session = aiming(Rank.JACK)

        val aim = assertNotNull(session.table().aim, "a Jack wants two cards and says so at once")
        assertNull(aim.first, "nothing is chosen yet")
        assertNull(aim.second, "nor at the other end")
    }

    @Test
    fun aQueenCarriesTheFaceOfEachCardItLookedAt() = runTest {
        val session = aiming(Rank.QUEEN)
        val me = session.playerId

        session.dispatch((session.table().taps.getValue(CardRef(me, 2)) as Move.Send).action)
        val half = assertNotNull(session.table().aim)
        val mine = assertNotNull(half.first, "the card just chosen")
        assertEquals(Speaker.You, mine.who)
        assertEquals(THIRD_CARD, mine.slot, "slots are counted the way a person counts a row")
        assertIs<CardView.Visible>(mine.card, "a Queen looks: the face travels with the aim")
        assertNull(half.second, "the second end is still waiting")

        val theirs = session.table().taps.keys.first()
        session.dispatch((session.table().taps.getValue(theirs) as Move.Send).action)
        val both = assertNotNull(session.table().aim)
        assertIs<CardView.Visible>(assertNotNull(both.second).card)
        assertEquals(Ask.SwapThem, session.table().prompt, "and now the decision")
    }

    @Test
    fun aJackCarriesNoFaceAtAllBecauseItSwapsBlind() = runTest {
        val session = aiming(Rank.JACK)
        val me = session.playerId

        session.dispatch((session.table().taps.getValue(CardRef(me, 2)) as Move.Send).action)
        val mine = assertNotNull(assertNotNull(session.table().aim).first)
        assertEquals(Speaker.You, mine.who)
        assertEquals(
            CardView.Hidden,
            mine.card,
            "a Jack never looks, so not even the player aiming it is told what they picked",
        )
    }

    /** Every other action leaves the column to the card in play, which is what it is for. */
    @Test
    fun onlyATwoCardActionOffersAnAim() = runTest {
        assertNull(aiming(Rank.NINE).table().aim, "a peek aims at one card, and the felt lifts it")
        assertNull(aiming(Rank.ACE).table().aim, "an Ace names a player, not a card")
        assertNull(aiming(Rank.KING).table().aim, "a King has a rank to name first")
    }

    /**
     * A King that named a Jack correctly aims like a Jack.
     *
     * The borrowed action is the one being aimed, so it gets the pair too — and the words
     * under it name the Jack, because by then the Jack *is* the card in play: a right
     * declaration takes it out of the hand and hands the pending action to it. The King is
     * already on the discard, worth nothing and doing nothing on its own.
     */
    @Test
    fun aKingThatNamedAJackAimsLikeOne() = runTest {
        val (session, jack) = kingAboutToNameAJack()

        session.dispatch((session.table().taps.getValue(jack) as Move.Send).action)
        session.dispatch(
            (session.table().ranks.first { it.rank == Rank.JACK }.move as Move.Send).action,
        )

        val table = session.table()
        val aim = assertNotNull(table.aim, "the King is aiming a Jack now")
        assertNull(aim.first, "and starts from nothing, as a Jack does")
        assertEquals(
            Detail.WhatTheCardDoes(Rank.JACK),
            table.detail,
            "the words name the borrowed card, not the King that fetched it",
        )
    }

    /**
     * A King played, and a real Jack somewhere on the table for it to name.
     *
     * The seed is searched rather than picked, because a King only borrows an action when the
     * name was *right* — declaring "Jack" at a card that is not one costs a penalty and ends
     * the turn, which is a different case with a different answer. Every deal here is the
     * engine's own, so the search is deterministic; it only has to find one.
     */
    private suspend fun kingAboutToNameAJack(): Pair<LocalGameSession, CardRef> {
        for (seed in 1L..SEEDS_TRIED) {
            val session = aiming(Rank.KING, seed)
            val jack = session.state.players.firstNotNullOfOrNull { seat ->
                seat.cards.indexOfFirst { it.rank == Rank.JACK }
                    .takeIf { it >= 0 }
                    ?.let { CardRef(seat.id, it) }
            }
            if (jack != null) return session to jack
        }
        error("no deal in $SEEDS_TRIED seeds put a Jack in a hand")
    }

    /** The card running the action is named in words, now that it is not drawn beside them. */
    @Test
    fun theWordsSayWhatCardIsRunningTheAction() = runTest {
        assertEquals(Detail.WhatTheCardDoes(Rank.QUEEN), aiming(Rank.QUEEN).table().detail)
        assertEquals(Detail.WhatTheCardDoes(Rank.JACK), aiming(Rank.JACK).table().detail)
    }

    private suspend fun aiming(rank: Rank, seed: Long = 77L): LocalGameSession {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(rank)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))
        return session
    }

    private fun LocalGameSession.table() = tableFor(view.value)

    private companion object {
        const val THIRD_CARD = 3
        const val SEEDS_TRIED = 40L
    }
}
