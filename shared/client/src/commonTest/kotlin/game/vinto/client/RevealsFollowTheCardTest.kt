package game.vinto.client

import game.vinto.bot.BotRunner
import game.vinto.engine.PlayerView
import game.vinto.engine.PublicReveal
import game.vinto.engine.tossInIsOpen
import game.vinto.shapes.Card
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.Lane
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.getCardValue
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A reveal follows its card.
 *
 * Reported from a phone as *"a claim under this turn error displayed in plan mode, why do we
 * have some errors there"*: a card turned face up for the table is a `PublicReveal` at a
 * position, and the session kept it at that position after the card had gone — so a claim about
 * whatever card slid into the place next read as contradicted by a card that was no longer
 * there, and the plan naming it wore the warning. The session's reveals now follow the cards
 * the moves fly, and this holds them against the engine's own answer — the card's identity,
 * which a client never sees — over whole games.
 */
class RevealsFollowTheCardTest {

    @Test
    fun theStandingRevealsAreExactlyTheRevealedCardsStillInAHand() = runTest(timeout = WHOLE_GAME) {
        var stood = 0
        for (seed in SEEDS) {
            val game = Drive(seed)
            game.setUp()
            var moves = 0
            while (!game.session.isOver && moves++ < MOVE_LIMIT) {
                if (!game.step()) break
                game.truth.check("seed $seed after $moves moves")
                if (game.session.reveals.value.isNotEmpty()) stood++
            }
            assertTrue(game.session.isOver, "seed $seed never reached scoring")
        }
        assertTrue(stood > 0, "no reveal ever stood, so nothing was followed")
    }

    @Test
    fun aRevealFollowsATradeSlidesDownBehindAThrowAndGoesWithASwapOut() {
        val view = teachingSession().view.value
        val a = view.players[1].id
        val b = view.players[2].id
        val nine = PublicReveal(a, 2, card(Rank.NINE))

        // Traded: the reveal lands with the card.
        val trade = listOf(
            listOf(Beat.Move(Anchor.Seat(a, 2), Anchor.Seat(b, 4)), Beat.Move(Anchor.Seat(b, 4), Anchor.Seat(a, 2))),
        )
        assertEquals(listOf(PublicReveal(b, 4, nine.card)), listOf(nine).following(trade, view, view))

        // A card thrown from below it: the hand closes up, and the reveal slides down with its card.
        val shorter = view.copy(
            players = view.players.map { if (it.id == a) it.copy(cards = it.cards.drop(1)) else it },
        )
        val throwBelow = listOf(listOf(Beat.Move(Anchor.Seat(a, 0), Anchor.Discard, shown = true)))
        assertEquals(listOf(PublicReveal(a, 1, nine.card)), listOf(nine).following(throwBelow, view, shorter))

        // Swapped out: gone — and the hand is the same size, so nothing else slides.
        val five = PublicReveal(a, 3, card(Rank.FIVE))
        val swapOut = listOf(
            listOf(Beat.Move(Anchor.Seat(a, 2), Anchor.Discard), Beat.Move(Anchor.Pending, Anchor.Seat(a, 2))),
        )
        assertEquals(listOf(five), listOf(nine, five).following(swapOut, view, view))
    }

    @Test
    fun aClaimAboutTheCardThatSlidIntoARevealedPlaceIsNotProvedWrongByIt() {
        // Ember's second card was thrown blind and shown to be a nine. Later the card before it
        // was thrown for real and the hand closed up: the nine is first now, and the card that
        // is second is the one a teammate says is a Joker. A plan naming that card rests on the
        // Joker claim, and the nine's reveal — which used to sit at that position — must not
        // read as having proved it wrong.
        val whole = teachingSession().view.value
        val ember = whole.players[1].id
        val tide = whole.players[2].id
        val nine = PublicReveal(ember, 1, card(Rank.NINE))
        val closedUp = whole.copy(
            players = whole.players.map { seat ->
                if (seat.id == ember) {
                    seat.copy(cards = seat.cards.drop(1), claims = listOf(Claim(tide, listOf(1), listOf(Rank.JOKER))))
                } else {
                    seat
                }
            },
        )
        val thrown = listOf(listOf(Beat.Move(Anchor.Seat(ember, 0), Anchor.Discard, shown = true)))
        val followed = listOf(nine).following(thrown, whole, closedUp)
        val plan = CoalitionPlan(
            lanes = listOf(Lane(tide, Step.Swap(cardAt(closedUp, ember, 1), cardAt(closedUp, tide, 0)))),
        )

        assertEquals(
            StepHealth.LIVE,
            readPlan(closedUp, plan, followed).health.single(),
            "the reveal did not follow its card",
        )
        // And the stale reading, for the record: this is the error that was on the felt.
        assertEquals(StepHealth.BROKEN, readPlan(closedUp, plan, listOf(nine)).health.single())
    }

    /**
     * The person's seat, played by a bot — and throwing a card it has not looked at into every
     * window it may, because that is the one thing that turns a card face up for the table on
     * purpose, and the reveal then has to follow the card through every trade and every
     * closing-up after.
     */
    private class Drive(private val seed: Long) {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        val truth = Truth(session)
        private val me = session.playerId
        private val person = BotRunner(Difficulty.EASY, Random(seed))
        private var threwInThisWindow = false

        suspend fun setUp() {
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        }

        /** One move for the person; false when the bot has none to make. */
        suspend fun step(): Boolean {
            val view = session.view.value
            if (!view.tossInIsOpen) threwInThisWindow = false
            when {
                view.conferMsRemaining != null -> session.doneConferring()
                view.tossInIsOpen && !threwInThisWindow && me !in view.barredFromTossIn -> throwBlind(view)
                view.tossInIsOpen -> session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))
                else -> return play()
            }
            return true
        }

        private suspend fun throwBlind(view: PlayerView) {
            threwInThisWindow = true
            val mine = view.players.first { it.id == me }
            val blind = mine.cards.indices.firstOrNull { it !in mine.knownCardPositions } ?: return
            session.dispatch(GameAction.ParticipateInTossIn(ParticipateInTossInPayload(me, listOf(blind))))
        }

        private suspend fun play(): Boolean {
            val everySeat = session.state.copy(
                players = session.state.players.map { it.copy(isHuman = false, isBot = true) },
            )
            val action = person.nextAction(everySeat) ?: return false
            val refused = session.dispatch(action)
            assertTrue(refused == null, "seed $seed: the drive was refused: $refused")
            return true
        }
    }

    /**
     * The engine's own answer: every reveal the round reported, at the place its card lies now
     * by identity, and gone once the card has left every hand — never resurrected by a reshuffle
     * dealing it out again, which is a card the table has not seen since.
     */
    private class Truth(private val session: LocalGameSession) {
        private var standing = listOf<PublicReveal>()
        private var taken = 0

        fun check(where: String) {
            val raw = session.revealedSoFar
            standing = (standing + raw.drop(taken)).mapNotNull { reveal ->
                session.state.players.firstNotNullOfOrNull { seat ->
                    val at = seat.cards.indexOfFirst { it.id == reveal.card.id }
                    if (at >= 0) PublicReveal(seat.id, at, reveal.card) else null
                }
            }
            taken = raw.size
            assertEquals(
                standing.map { Triple(it.playerId, it.position, it.card.id) }.toSet(),
                session.reveals.value.map { Triple(it.playerId, it.position, it.card.id) }.toSet(),
                where,
            )
        }
    }

    private fun card(rank: Rank) = Card(
        id = "revealed-${rank.serialName}",
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = null,
    )

    private companion object {
        val SEEDS = listOf(11L, 12L, 13L)
        const val MOVE_LIMIT = 600
    }
}
