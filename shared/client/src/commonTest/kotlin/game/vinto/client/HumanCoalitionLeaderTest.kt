package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.projectView
import game.vinto.shapes.Card
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
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The final round with a person in the coalition: the bots wait for them to choose the
 * leader, they get their one turn, and the coalition's table talk — declared claims — is
 * theirs to join and everyone's to read.
 */
class HumanCoalitionLeaderTest {

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun seat(
        id: String,
        isHuman: Boolean,
        callerId: String,
        ranks: List<Rank>,
        declared: Map<Int, Rank>? = null,
    ) = PlayerState(
        id = id,
        name = id,
        nickname = id,
        isHuman = isHuman,
        isBot = !isHuman,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = if (isHuman) emptyList() else ranks.indices.toList(),
        isVintoCaller = id == callerId,
        coalitionWith = if (id == callerId) emptyList() else listOf("human-1", "bot-2", "bot-3", "bot-4") - id,
        declaredCards = declared,
    )

    /** A final round in progress: [callerId] has called, the seat after them is on play. */
    private fun finalRound(
        callerId: String,
        leaderId: String?,
        currentPlayerIndex: Int,
        declaredOnBot3: Map<Int, Rank>? = null,
    ) = GameState(
        gameId = "human-coalition",
        roundNumber = 1,
        turnNumber = 12,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.AI_THINKING,
        finalTurnTriggered = true,
        players = listOf(
            seat("human-1", isHuman = true, callerId, ranks = listOf(Rank.NINE)),
            seat("bot-2", isHuman = false, callerId, ranks = listOf(Rank.KING, Rank.TWO)),
            seat("bot-3", isHuman = false, callerId, ranks = listOf(Rank.FIVE), declared = declaredOnBot3),
            seat("bot-4", isHuman = false, callerId, ranks = listOf(Rank.SIX)),
        ),
        currentPlayerIndex = currentPlayerIndex,
        vintoCallerId = callerId,
        coalitionLeaderId = leaderId,
        drawPile = Pile((0..6).map { card(Rank.FOUR, "draw-$it") }),
        discardPile = Pile(listOf(card(Rank.THREE, "discard-seed"))),
        pendingAction = null,
        activeTossIn = null,
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.MODERATE,
        rngState = 0,
    )

    /** The human's scripted final-round manners: confirm windows, take the one turn. */
    private suspend fun playHumanThrough(session: LocalGameSession) {
        var guard = 0
        while (!session.isOver && guard++ < 60) {
            val v = session.view.value
            val action = when {
                v.activeTossIn != null &&
                    session.playerId !in v.activeTossIn!!.playersReadyForNextTurn ->
                    GameAction.PlayerTossInFinished(PlayerIdPayload(session.playerId))

                v.players.getOrNull(v.currentPlayerIndex)?.id == session.playerId &&
                    v.pendingAction == null && v.subPhase == GameSubPhase.IDLE ->
                    GameAction.DrawCard(PlayerIdPayload(session.playerId))

                v.pendingAction?.playerId == session.playerId &&
                    v.subPhase == GameSubPhase.CHOOSING ->
                    GameAction.DiscardCard(PlayerIdPayload(session.playerId))

                else -> return
            }
            session.dispatch(action)
        }
    }

    @Test
    fun aBotVintoCallWaitsForTheHumanToChooseTheLeader() = runTest {
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = null, currentPlayerIndex = 2),
        )

        // The bots are held: a no-op dispatch gives them every chance to move, and nothing
        // moves — no leader appears, the seat pointer stays put.
        session.dispatch(GameAction.Empty(JsonNull))
        assertNull(session.view.value.coalitionLeaderId, "a bot picked the leader over the human")
        assertEquals(2, session.view.value.currentPlayerIndex)
        assertEquals(GamePhase.FINAL, session.view.value.phase)

        // The prompt is live: every non-caller seat on offer, the caller not among them.
        val table = tableFor(session.view.value)
        assertIs<Ask.WhoPlaysForYou>(table.prompt, "expected the leader prompt, got '${table.prompt}'")
        assertEquals(setOf("human-1", "bot-3", "bot-4"), table.seatTaps.keys)

        // The human picks a seat; the bots resume and play through to their own turn.
        val pick = table.seatTaps.getValue("bot-3") as Move.Send
        session.dispatch(pick.action)
        assertEquals("bot-3", session.view.value.coalitionLeaderId)

        // The human plays their one turn; the round scores; the caller's hand is untouched.
        playHumanThrough(session)
        assertTrue(session.isOver, "the round never scored")
        val caller = session.view.value.players.first { it.id == "bot-2" }
        assertEquals(
            listOf(Rank.KING, Rank.TWO),
            caller.cards.map { (it as CardView.Visible).card.rank },
            "the caller's hand changed during the final round",
        )
    }

    @Test
    fun aCoalitionMemberCanDeclareACardWhileWaiting() = runTest {
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = "bot-3", currentPlayerIndex = 2),
        )
        val view = session.view.value

        // Waiting through a bot's turn, the human's own cards invite a claim.
        val waiting = tableFor(view)
        val tap = waiting.taps[CardRef(session.playerId, 0)]
        assertTrue(tap is Move.Ask && tap.question == Question.DeclareRank(0))

        // The tap opens the rank picker; a rank becomes a DECLARE_CARDS the engine accepts.
        val picker = tableFor(view, Question.DeclareRank(0))
        assertTrue(picker.ranks.isNotEmpty(), "no ranks on offer")
        val claim = picker.ranks.first { it.rank == Rank.QUEEN }.move as Move.Send
        session.dispatch(claim.action)

        assertEquals(
            mapOf(0 to Rank.QUEEN),
            session.view.value.players.first { it.id == session.playerId }.declaredCards,
        )

        // And the claim is worn as a badge on the card, for this seat and every other.
        val after = tableFor(session.view.value)
        assertEquals("Q", after.badges[CardRef(session.playerId, 0)])
    }

    @Test
    fun theVintoCallerGetsNoDeclareTaps() = runTest {
        // Here the human called Vinto. Their hand is frozen and out of the conversation.
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "human-1", leaderId = "bot-3", currentPlayerIndex = 2),
        )

        val table = tableFor(session.view.value)
        assertTrue(
            table.taps.values.none { it is Move.Ask && it.question is Question.DeclareRank },
            "the caller was offered a declaration",
        )
    }

    @Test
    fun aRevealedCardWearsNoClaim() {
        // At scoring every face is up, and a claim beside a face is the same word twice or a
        // contradiction — the reveal is what a claim was standing in for.
        val state = finalRound(
            callerId = "bot-2",
            leaderId = "bot-3",
            currentPlayerIndex = 2,
            declaredOnBot3 = mapOf(0 to Rank.FIVE),
        ).copy(phase = GamePhase.SCORING)

        val table = tableFor(projectView(state, "human-1"))
        assertTrue(CardRef("bot-3", 0) in table.revealed, "scoring reveals the card")
        assertNull(table.badges[CardRef("bot-3", 0)], "the claim is still worn over the face")
    }

    @Test
    fun declaredBadgesAppearOnEverySeatsTable() {
        val state = finalRound(
            callerId = "bot-2",
            leaderId = "bot-3",
            currentPlayerIndex = 2,
            declaredOnBot3 = mapOf(0 to Rank.FIVE),
        )

        for (seatId in state.players.map { it.id }) {
            val table = tableFor(projectView(state, seatId))
            assertEquals(
                "5",
                table.badges[CardRef("bot-3", 0)],
                "seat $seatId cannot read bot-3's claim",
            )
        }
    }

    @Test
    fun theLeaderNoLongerSeesCoalitionHands() {
        // The human is the leader; being nominated shows them nothing.
        val state = finalRound(callerId = "bot-2", leaderId = "human-1", currentPlayerIndex = 2)
        val revealed = revealedTo(projectView(state, "human-1"))
        assertTrue(revealed.isEmpty(), "the leader was shown cards: $revealed")
    }
}
