package game.vinto.client

import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.initializeGame
import game.vinto.engine.projectView
import game.vinto.shapes.DeclareKingActionPayload
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.SwapHandWithDeckPayload
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A card thrown into a window flies as the card it is.
 *
 * Reported 2026-10-09, with the game attached: *"I tossed in thinking it was 6, but it was king.
 * Log was showing 6."* It was a 6, and the engine took it as one. Dune had thrown a King into the
 * window and named a 6 with it, so the window was open for K and 6; the player threw their 6, and
 * what flew from their hand to the pile was a King. A plain card thrown in is slid in beneath the
 * pile's top, so its flight cannot wear the new top — and the face it wore instead was the
 * window's *first* rank, which once a King has named a card is the King.
 *
 * Read from both sides of the table, because a thrown card is public: the thrower and a seat
 * watching are shown the same face.
 */
class AThrownCardFliesAsItselfTest {

    @Test
    fun aSixThrownIntoAWindowAKingWidenedFliesAsASix() {
        val play = Play()
        play.plant(ME, 3, Rank.SIX)
        play.kingNamesASixOfTheOpponents()

        val throwIn = GameAction.ParticipateInTossIn(ParticipateInTossInPayload(ME, listOf(3)))
        val before = play.state
        play.act(throwIn)

        for (viewer in listOf(ME, WATCHER)) {
            val flight = play.flights(throwIn, before, viewer).single { it.from == Anchor.Seat(ME, 3) }
            assertEquals(Anchor.Discard, flight.to, "a plain card goes to the pile")
            assertEquals(Rank.SIX, flight.card?.rank, "seen by $viewer, the thrown 6 flew as a ${flight.card?.rank}")
        }
    }

    /**
     * And thrown together, a King and a 6 go each to its own place as itself: the King is queued,
     * so it lands in the Tossed row, and the 6 goes to the pile. The throw used to be judged as a
     * whole — every card queued or none — so both flew to the pile, both wearing the King.
     */
    @Test
    fun aKingAndASixThrownTogetherGoEachToItsOwnPlace() {
        val play = Play()
        play.plant(ME, 2, Rank.KING)
        play.plant(ME, 3, Rank.SIX)
        play.kingNamesASixOfTheOpponents()

        val throwIn = GameAction.ParticipateInTossIn(ParticipateInTossInPayload(ME, listOf(2, 3)))
        val before = play.state
        play.act(throwIn)

        for (viewer in listOf(ME, WATCHER)) {
            val flights = play.flights(throwIn, before, viewer)
            val king = flights.single { it.from == Anchor.Seat(ME, 2) }
            val six = flights.single { it.from == Anchor.Seat(ME, 3) }
            assertEquals(Anchor.Thrown(0), king.to, "seen by $viewer, the King waits its turn in the row")
            assertEquals(Rank.KING, king.card?.rank, "seen by $viewer")
            assertEquals(Anchor.Discard, six.to, "seen by $viewer, the 6 goes to the pile")
            assertEquals(Rank.SIX, six.card?.rank, "seen by $viewer")
        }
    }

    /** A dealt game at the top of the player's turn, and the means to move it. */
    private class Play {
        var state: GameState = ready()
            private set

        fun act(action: GameAction) {
            state = when (val result = GameEngine.reduce(state, action)) {
                is ReduceResult.Success -> result.state
                is ReduceResult.Failure -> error("${action.type} refused: ${result.reason}")
            }
        }

        fun plant(seat: String, position: Int, rank: Rank) =
            act(GameAction.SwapHandWithDeck(SwapHandWithDeckPayload(seat, position, rank)))

        /** The player plays a King off the deck and names, rightly, the 6 an opponent holds. */
        fun kingNamesASixOfTheOpponents() {
            plant(OPPONENT, 0, Rank.SIX)
            act(GameAction.SetNextDrawCard(RankPayload(Rank.KING)))
            act(GameAction.DrawCard(PlayerIdPayload(ME)))
            act(GameAction.UseCardAction(PlayerIdPayload(ME)))
            act(GameAction.SelectActionTarget(SelectActionTargetPayload.Positional(ME, OPPONENT, 0)))
            act(GameAction.DeclareKingAction(DeclareKingActionPayload(ME, Rank.SIX)))

            // The staging is the report's window, or the test is about something else: the King
            // first, so that reading the first rank is what goes wrong.
            assertEquals(listOf(Rank.KING, Rank.SIX), state.activeTossIn?.ranks, "the window the report had")
        }

        fun flights(action: GameAction, before: GameState, viewer: String): List<Beat.Move> =
            choreograph(action, projectView(before, viewer), projectView(state, viewer))
                .flatten()
                .filterIsInstance<Beat.Move>()

        private fun ready(): GameState {
            var state = initializeGame(SEED, Difficulty.EASY)
            for (action in listOf(
                GameAction.PeekSetupCard(PositionPayload(ME, 0)),
                GameAction.PeekSetupCard(PositionPayload(ME, 1)),
                GameAction.FinishSetup(PlayerIdPayload(ME)),
            )) {
                state = (GameEngine.reduce(state, action) as ReduceResult.Success).state
            }
            return state
        }
    }

    private companion object {
        const val SEED = 7L
        const val ME = "human-1"
        const val OPPONENT = "bot-1"
        const val WATCHER = "bot-2"
    }
}
