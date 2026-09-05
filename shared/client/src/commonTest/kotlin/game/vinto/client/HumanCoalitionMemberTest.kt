package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.projectView
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
import game.vinto.shapes.TableTalk
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The final round with a person in the coalition: they get their one turn, and the coalition's
 * table talk — declared claims — is theirs to join from the moment Vinto is called and
 * everyone's to read.
 */
class HumanCoalitionMemberTest {

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
        claims = declared?.map { Claim(id, listOf(it.key), listOf(it.value)) },
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
    fun aBotVintoCallStartsTheFinalRoundWithNothingToVoteOn() = runTest {
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = null, currentPlayerIndex = 2),
        )

        // This used to assert the opposite — that the bots were *held* until the person named
        // a leader. There is nothing to name: the round is scored against the lowest coalition
        // hand whoever holds it, and every bot declares before any coalition turn is played,
        // so the coalition already agrees on a target from the same public claims.
        session.dispatch(GameAction.Empty(JsonNull))
        assertNull(session.view.value.coalitionLeaderId, "nothing sets a leader any more")
        assertEquals(GamePhase.FINAL, session.view.value.phase)

        // And the table asks nothing about it. The seat rail is left to the one move that
        // still names a person — an Ace — so in the final round it offers nobody.
        val table = tableFor(session.view.value)
        assertTrue(
            table.seats.isEmpty(),
            "the retired nomination is still being put to the player: '${table.prompt}'",
        )

        // The coalition's window holds the round until the person has had their say, so the
        // player does what a player does before taking their turn.
        session.doneConferring()

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
        assertTrue(tap is Move.Ask && tap.question == Question.Claiming(session.playerId, listOf(0)))

        // The tap opens the rank picker; a rank becomes a DECLARE_CARDS the engine accepts.
        val picker = tableFor(view, Question.Claiming(session.playerId, listOf(0)))
        assertTrue(picker.ranks.isNotEmpty(), "no ranks on offer")
        val claim = picker.ranks.first { it.rank == Rank.QUEEN }.move as Move.Send
        session.dispatch(claim.action)

        val mine = session.view.value.players.first { it.id == session.playerId }
        assertEquals(listOf(Rank.QUEEN), mine.claims.single().ranks)
        assertEquals(session.playerId, mine.claims.single().by, "a claim is somebody's statement")

        // And the claim is worn as a badge on the card, for this seat and every other.
        val after = tableFor(session.view.value)
        assertEquals("Q", after.badges[CardRef(session.playerId, 0)])
    }

    /**
     * Reported from a phone. A bot called Vinto, the player was asked who plays for the rest
     * of them, they nominated a bot — and then had to take their own turn anyway, with no way
     * to tell that bot anything. Both halves of that are this app's fault rather than the
     * rules': the coalition each take their own last turn and always did, and the one thing
     * the player wanted to do — say what they are holding — was built, offered only while
     * *watching*, and so unreachable on the one turn in the round that is theirs.
     */
    @Test
    fun aCoalitionMemberCanStillTalkOnTheirOwnTurn() = runTest {
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = "bot-3", currentPlayerIndex = 0)
                .copy(subPhase = GameSubPhase.IDLE),
        )
        val view = session.view.value
        // Said first, so a fixture that drifted off the human's turn fails for that rather
        // than passing vacuously on the watching table this test exists to bypass.
        assertEquals(
            session.playerId,
            view.players[view.currentPlayerIndex].id,
            "the fixture is not on the human's turn",
        )

        val table = tableFor(view)
        val tap = table.taps[CardRef(session.playerId, 0)]
        assertTrue(
            tap is Move.Ask && tap.question == Question.Claiming(session.playerId, listOf(0)),
            "nothing to say on your own turn: ${table.taps}",
        )
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
            table.taps.values.none { it is Move.Ask && it.question is Question.Claiming },
            "the caller was offered a declaration",
        )
    }

    @Test
    fun aRevealedCardKeepsItsClaimSoTheRevealCanSettleIt() {
        // Mid-round a claim comes off when its card turns over. **Scoring is the exception,
        // and it is the point**: every hand goes up at once, which is the only moment a claim
        // can be checked at all — and being checkable at the end is what gives table talk a
        // cost and an honest claim its worth.
        val state = finalRound(
            callerId = "bot-2",
            leaderId = "bot-3",
            currentPlayerIndex = 2,
            declaredOnBot3 = mapOf(0 to Rank.FIVE),
        ).copy(phase = GamePhase.SCORING)

        val table = tableFor(projectView(state, "human-1"))
        assertTrue(CardRef("bot-3", 0) in table.revealed, "scoring reveals the card")
        assertNotNull(table.badges[CardRef("bot-3", 0)], "the claim vanished instead of being settled")
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

    // ------------------------------------------------------------ talking to the bots

    @Test
    fun whatABotSaysReachesTheStripAPlayerReads() = runTest {
        // The channel used to end in a flow nobody collected: twenty-six sentences and
        // nineteen locales rendering nothing. The log is the strip a screen already draws.
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = null, currentPlayerIndex = 2),
        )

        session.dispatch(GameAction.Empty(JsonNull))

        assertTrue(
            session.log.value.any { it is Say.Standing || it is Say.WillShed || it is Say.GiveMe },
            "the bots said nothing a player could read: ${session.log.value}",
        )
    }

    @Test
    fun aBotAnswersASuggestionInASoloGame() = runTest {
        // Solo is the one configuration where a person talks to bots, and it is the one a
        // store release ships. A proposal that was broadcast and ignored made the whole
        // "propose, never command" design unreachable.
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = null, currentPlayerIndex = 2),
        )

        val refusal = session.say(
            TableTalk.Proposal(
                by = session.playerId,
                to = "bot-3",
                move = GameAction.DrawCard(PlayerIdPayload("bot-3")),
            ),
        )

        assertNull(refusal)
        val answered = session.log.value.filterIsInstance<Say.Answered>()
        assertTrue(answered.isNotEmpty(), "no bot answered: ${session.log.value}")
    }

    @Test
    fun aPersonMayNotSpeakAsSomebodyElseEvenAlone() = runTest {
        // The same rule the room applies, in the one place, so a screen that tried it locally
        // is refused exactly as it would be online.
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = null, currentPlayerIndex = 2),
        )

        assertNotNull(
            session.say(TableTalk.Standing("bot-3", TableTalk.Standing.Where.LOW)),
            "one seat spoke as another in a solo game",
        )
    }

    // ------------------------------------------------------------ the confer window

    /**
     * The coalition's one strategic question, askable by a person.
     *
     * A round is scored against the **lowest** coalition hand, so before anybody plans anything
     * the table has to settle whose hand it is pushing. Bots have always known — they pool
     * sightings — and until now a person could only imply it, by declaring enough cards for the
     * others to add up. That is a lot of taps to say one thing, and it works only for somebody
     * who has *seen* their hand.
     *
     * `Standing` is the sentence for it, and it was written, rendered and translated into all
     * nineteen locales before anything could send one: the phrasebook value, `Say.Standing` and
     * `talkStanding` were all in place and no button anywhere produced it. This is that button.
     */
    @Test
    fun theConferWindowLetsYouSayWhereYourHandStands() = runTest {
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = null, currentPlayerIndex = 2),
        )
        session.dispatch(GameAction.Empty(JsonNull))

        val table = tableFor(session.view.value)
        val where = table.choices
            .mapNotNull { (it.move as? Move.Say)?.talk }
            .filterIsInstance<TableTalk.Standing>()
            .map { it.where }

        assertEquals(
            TableTalk.Standing.Where.entries.toSet(),
            where.toSet(),
            "the confer window cannot say where this hand stands",
        )
        assertTrue(table.choices.any { it.move is Move.Done }, "the way out went with the additions")
    }

    /**
     * Every sentence is this seat's own.
     *
     * The `by` on a message is the seat boundary inside the talk channel, and a rail that let a
     * player speak in somebody else's name would be the one place in the app where acting for
     * another seat is possible.
     */
    @Test
    fun everythingTheRailCanSayIsSaidInYourOwnName() = runTest {
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = null, currentPlayerIndex = 2),
        )
        session.dispatch(GameAction.Empty(JsonNull))
        val view = session.view.value

        val spoken = tableFor(view).choices.mapNotNull { (it.move as? Move.Say)?.talk }

        assertTrue(spoken.isNotEmpty(), "nothing to say at all")
        assertTrue(
            spoken.all { it.by == view.viewerId },
            "a button speaks in another seat's name: ${spoken.filter { it.by != view.viewerId }}",
        )
    }

    /** And saying it reaches the table, in this seat's name, without ending the window. */
    @Test
    fun sayingWhereYouStandIsHeardAndLeavesTheWindowOpen() = runTest {
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = null, currentPlayerIndex = 2),
        )
        session.dispatch(GameAction.Empty(JsonNull))

        val low = TableTalk.Standing(session.playerId, TableTalk.Standing.Where.LOW)
        assertNull(session.say(low), "the table refused a seat's own assessment")

        assertNotNull(
            session.view.value.conferMsRemaining,
            "saying where you stand closed the window you said it in",
        )
    }

    @Test
    fun aSoloCoalitionGetsItsSayBeforeTheBotsPlay() = runTest {
        // Without this the bots declare and play the instant Vinto is called, so a person in
        // the third coalition seat watches two turns go by before they can tell anybody
        // anything.
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = null, currentPlayerIndex = 2),
        )
        session.dispatch(GameAction.Empty(JsonNull))

        val view = session.view.value
        assertNotNull(view.conferMsRemaining, "no window opened for the person in the coalition")

        // And the table offers the way out, with every card still tappable to claim.
        val table = tableFor(view)
        assertEquals(Ask.SayWhatYouKnow, table.prompt)
        assertTrue(table.choices.any { it.label == Label.DoneTalking }, "a window with no button")
        assertTrue(table.taps.isNotEmpty(), "nothing to say during the talking window")
    }

    @Test
    fun sayingSoEndsTheWindowAndLetsTheRoundRun() = runTest {
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "bot-2", leaderId = null, currentPlayerIndex = 2),
        )
        session.dispatch(GameAction.Empty(JsonNull))
        assertNotNull(session.view.value.conferMsRemaining)

        assertNull(session.doneConferring())

        assertNull(session.view.value.conferMsRemaining, "the window stayed open")
        // And it does not reopen on the next thing that happens.
        session.dispatch(GameAction.Empty(JsonNull))
        assertNull(session.view.value.conferMsRemaining, "the window opened again after it closed")
    }

    @Test
    fun theCallerIsNotHeldByAWindowTheyAreNotIn() = runTest {
        val session = LocalGameSession(
            seed = 5L,
            difficulty = Difficulty.MODERATE,
            resuming = finalRound(callerId = "human-1", leaderId = null, currentPlayerIndex = 2),
        )
        session.dispatch(GameAction.Empty(JsonNull))

        assertNull(
            session.view.value.conferMsRemaining,
            "the caller was held by their opponents' conversation",
        )
    }
}
