package game.vinto.client

import game.vinto.shapes.Card
import game.vinto.shapes.Claim
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Call Vinto yourself and you can still read what the coalition intends.
 *
 * The one round where the person is on the *other* side of the table: they called, so all three
 * opponents are bots. Nothing was written down at all, and the caller watched three turns go by
 * without a word about what they were for — which is the whole tension of the round they had
 * just started. Reported from a phone: *"I as vinto do not see bots plan"*.
 *
 * **Two separate faults, and both had to go.** The bots' seeding stopped where there was no human
 * *in the coalition* to seed for; and the session asked the *member's* question — am I one of
 * them — before seeding at all, which the caller answers no to by definition. The window is a
 * member's and the caller is rightly not offered one; the board is the table's, and the plan is
 * built from claims the table has already heard, so there is nothing in it to hide (design D12).
 */
class TheCallerReadsThePlanTest {

    private val me = "human-1"
    private val ann = "bot-2"
    private val bob = "bot-3"
    private val cid = "bot-4"

    @Test
    fun theCoalitionWritesItsPlanDownEvenWhenTheCallerIsTheOnlyPersonAtTheTable() = runTest(timeout = WHOLE_GAME) {
        // Resumed to the moment before the call rather than played there: what is being read is
        // the shape of the round — one person, who called, against three bots — and driving a
        // real game to a self-call depends on which cards happen to turn up.
        val session = LocalGameSession(seed = SEED, difficulty = Difficulty.EASY, resuming = aboutToCall())
        val me = session.playerId

        // Called at the end of the person's own turn, which is the only way into this position —
        // and the dispatch carrying it is the one that runs the bots. It stops at the first
        // toss-in window, which is the caller's to close, so there is a round still to come for
        // the board to describe.
        assertNull(session.dispatch(GameAction.CallVinto(PlayerIdPayload(me))), "the call was refused")

        val view = session.view.value
        assertTrue(view.vintoCallerId == me, "the call did not name the person as the caller")

        // No window: conferring is for the coalition, and the caller is not in one.
        assertNull(view.conferMsRemaining, "the caller was offered a window to confer in")

        // But a board, with something on it, and a way to open it.
        val plan = assertNotNull(session.plan.value, "the coalition wrote nothing down")
        assertTrue(
            plan.lanes.any { it.step != null },
            "the coalition's board is empty, so the caller has nothing to read: ${plan.lanes}",
        )
        val summary = assertNotNull(
            tableFor(view, plan = plan).planSummary,
            "the caller has no way into the board",
        )
        assertTrue(summary.lanesSet > 0, "the summary says nothing is planned")
        assertTrue(!summary.mine, "the caller was counted among those who have agreed")
    }

    /**
     * The moment before the person calls Vinto: their own turn, ended, with the call still to make.
     *
     * The hands are chosen so that a trade actually helps — a plan says what *lowers the lowest
     * hand*, and a deal where nothing does is a deal where the bots correctly write nothing. The
     * claims are what makes the cards nameable: a step can only name what the table has heard.
     */
    private fun aboutToCall(): GameState = GameState(
        gameId = "the-caller-reads",
        roundNumber = 1,
        turnNumber = 12,
        // The end of the person's own turn, which is when Vinto may be called.
        phase = GamePhase.PLAYING,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = false,
        players = listOf(
            seat(me, listOf(Rank.NINE), human = true),
            // A Jack, so there is a trade the rules let a bot propose: put down, called, and made.
            seat(ann, listOf(Rank.TWO, Rank.JACK)),
            seat(bob, listOf(Rank.KING, Rank.SEVEN)),
            seat(cid, listOf(Rank.THREE)),
        ),
        currentPlayerIndex = 0,
        vintoCallerId = null,
        coalitionLeaderId = null,
        drawPile = Pile((0..6).map { card(Rank.FOUR, "draw-$it") }),
        discardPile = Pile(listOf(card(Rank.THREE, "discard"))),
        pendingAction = null,
        activeTossIn = null,
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.EASY,
        rngState = 0,
    )

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun seat(id: String, ranks: List<Rank>, human: Boolean = false) = PlayerState(
        id = id,
        name = id,
        nickname = id,
        isHuman = human,
        isBot = !human,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = ranks.indices.toList(),
        isVintoCaller = false,
        coalitionWith = emptyList(),
        // Said out loud, because a step can only name a card the table has been told about.
        claims = ranks.mapIndexed { index, rank -> Claim(id, listOf(index), listOf(rank)) },
    )

    private companion object {
        /** Only the bots' own randomness; the deal is written out above. */
        const val SEED = 20_260_411L
    }
}
