package game.vinto.bot

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.SerializedOpponentKnowledge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from `legacy-web/packages/bot/src/lib/__tests__/coalition-planner.test.ts`.
 *
 * These are worth porting scenario-for-scenario rather than rewriting, because they assert
 * *specific decisions* rather than properties — which makes them a real check that the Kotlin
 * planner finds the same winning lines as the TypeScript one, not merely a legal one.
 *
 * Every scenario is a position where the coalition is losing as it stands and exactly one
 * line saves it.
 */
class CoalitionPlannerTest {

    private companion object {
        const val HUMAN = "human"
        const val BOT1 = "bot1"
        const val BOT2 = "bot2"
        const val BOT3 = "bot3"
    }

    private var cardCounter = 0

    private fun card(rank: Rank): Card = testCard(rank, "${rank.serialName}-${cardCounter++}")

    /**
     * The caller is the human; the three bots are the coalition and share their hands.
     * Knowledge of the caller's cards is parked on bot1, since the planner pools it.
     */
    @Suppress("LongParameterList")
    private fun buildState(
        human: List<Rank>,
        bot1: List<Rank>,
        bot2: List<Rank>,
        bot3: List<Rank>,
        currentPlayerIndex: Int,
        discardPile: List<Rank> = listOf(Rank.FOUR),
        pendingCard: Card? = null,
        knownHumanCards: List<Int>? = null,
        phase: GamePhase = GamePhase.FINAL,
        vintoCallerId: String? = HUMAN,
        /** Positions a bot has neither read nor declared: placeholders in the plan. */
        unread: Map<String, List<Int>> = emptyMap(),
    ): GameState {
        val caller = testPlayer(HUMAN, "Human", isHuman = true, cards = human.map(::card))
            .copy(isVintoCaller = true)

        val botIds = listOf(BOT1, BOT2, BOT3)
        val bots = listOf(bot1, bot2, bot3).mapIndexed { index, ranks ->
            val cards = ranks.map(::card)
            val blind = unread[botIds[index]].orEmpty()
            testPlayer(
                botIds[index],
                "Bot ${index + 1}",
                isHuman = false,
                cards = cards,
                knownCardPositions = cards.indices.filter { it !in blind },
            ).copy(
                coalitionWith = botIds,
                // The scenarios assume a fully shared coalition picture, which now means
                // fully *declared*: every bot has said out loud what it holds, truthfully.
                declaredCards = cards.mapIndexed { position, c -> position to c.rank }
                    .filter { (position, _) -> position !in blind }
                    .toMap(),
            )
        }

        val knownIndices = knownHumanCards ?: caller.cards.indices.toList()
        val withKnowledge = bots.toMutableList()
        withKnowledge[0] = bots[0].copy(
            opponentKnowledge = mapOf(
                HUMAN to SerializedOpponentKnowledge(
                    knownCards = knownIndices.associateWith { caller.cards[it] },
                ),
            ),
        )

        val players: List<PlayerState> = listOf(caller) + withKnowledge

        return testState(
            players = players,
            phase = phase,
            subPhase = GameSubPhase.CHOOSING,
            vintoCallerId = vintoCallerId,
            coalitionLeaderId = BOT1,
            discardPile = Pile(discardPile.map(::card)),
        ).copy(
            currentPlayerIndex = currentPlayerIndex,
            pendingAction = pendingCard?.let {
                PendingAction(
                    card = it,
                    playerId = players[currentPlayerIndex].id,
                    actionPhase = ActionPhase.CHOOSING_ACTION,
                    from = PendingCardOrigin.DRAWING,
                    targets = emptyList(),
                )
            },
        )
    }

    private fun score(cards: List<Card>): Int = cards.sumOf { it.value }

    /** Applies a Jack/Queen plan on the test's side, so the assertion is about the result. */
    private fun applySwap(
        state: GameState,
        targets: List<BotActionTarget>,
    ): Map<String, List<Card>> {
        val hands = state.players.associate { it.id to it.cards.toMutableList() }
        val (first, second) = targets
        val held = hands.getValue(first.playerId)[first.position]
        hands.getValue(first.playerId)[first.position] = hands.getValue(second.playerId)[second.position]
        hands.getValue(second.playerId)[second.position] = held
        return hands
    }

    private fun coalitionMinimum(hands: Map<String, List<Card>>): Int =
        listOf(BOT1, BOT2, BOT3).minOf { score(hands.getValue(it)) }

    // --- input construction ------------------------------------------------------------

    @Test
    fun buildsInputWithPooledKnowledgeUnseenCountsAndTheRemainingTurnQueue() {
        val state = buildState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE),
            bot1 = listOf(Rank.THREE, Rank.FIVE),
            bot2 = listOf(Rank.JOKER, Rank.NINE),
            bot3 = listOf(Rank.SEVEN, Rank.FIVE),
            currentPlayerIndex = 2,
            knownHumanCards = listOf(0, 1),
        )

        val input = assertNotNull(buildCoalitionPlanInput(state, BOT2))

        assertEquals(listOf(BOT1, BOT2, BOT3), input.members.map { it.id })
        assertEquals(listOf(BOT3), input.turnQueue, "bot3 still to play, then back to the caller")
        assertEquals(listOf(2, 3), input.callerKnownValues.sorted())
        assertEquals(1, input.callerUnknownCount)
        // Two threes are visible — the caller's and bot1's — so two are left unseen.
        assertEquals(2, input.unseenCounts[Rank.THREE])
        // The caller's Ace was never seen, so it is still a possible draw.
        assertEquals(4, input.unseenCounts[Rank.ACE])
        // The four on the discard pile is gone.
        assertEquals(3, input.unseenCounts[Rank.FOUR])
    }

    @Test
    fun returnsNothingOutsideACoalitionFinalRound() {
        val state = buildState(
            human = listOf(Rank.TWO),
            bot1 = listOf(Rank.THREE),
            bot2 = listOf(Rank.FOUR),
            bot3 = listOf(Rank.FIVE),
            currentPlayerIndex = 1,
            phase = GamePhase.PLAYING,
            vintoCallerId = null,
        )

        assertNull(buildCoalitionPlanInput(state, BOT1))
    }

    // --- finding the winning line ------------------------------------------------------

    @Test
    fun usesADrawnJackToMoveAJokerIntoACoalitionHandThatThenBeatsTheCaller() {
        // The caller is on 6. Bot1 is 8, bot2 is 8, bot3 is 12 — nobody wins as things stand.
        val drawn = card(Rank.JACK)
        val state = buildState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE),
            bot1 = listOf(Rank.THREE, Rank.FIVE),
            bot2 = listOf(Rank.JOKER, Rank.NINE),
            bot3 = listOf(Rank.SEVEN, Rank.FIVE),
            currentPlayerIndex = 2,
            pendingCard = drawn,
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT2))

        val decision = planCoalitionDrawnCard(input, drawn)

        val useAction = decision as? CoalitionDrawnCardDecision.UseAction
        assertNotNull(useAction, "expected the Jack to be played, got $decision")
        assertEquals(2, useAction.action.targets.size)
        assertEquals(true, useAction.action.shouldSwap)

        val hands = applySwap(
            state,
            useAction.action.targets.map { BotActionTarget(it.playerId, it.position) },
        )
        assertTrue(coalitionMinimum(hands) < 6, "the swap has to actually put someone under 6")
        assertTrue(useAction.action.targets.none { it.playerId == HUMAN })
    }

    @Test
    fun picksJackTargetsForAPendingActionThatConcentrateTheLowCards() {
        val jack = card(Rank.JACK)
        val state = buildState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE),
            bot1 = listOf(Rank.THREE, Rank.FIVE),
            bot2 = listOf(Rank.JOKER, Rank.NINE),
            bot3 = listOf(Rank.SEVEN, Rank.FIVE),
            currentPlayerIndex = 2,
            pendingCard = jack,
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT2))

        val decision = planCoalitionActionTargets(input, jack)

        assertEquals(2, decision.targets.size)
        assertEquals(true, decision.shouldSwap)
        assertTrue(coalitionMinimum(applySwap(state, decision.targets)) < 6)
    }

    @Test
    fun usesADrawnKingToStripTheHighCardOffTheChampionToBe() {
        // The caller is on 6. Bot1 is [3,10]; removing the 10 leaves 3. Bot3 acts last, so
        // there is no later turn to fix it — this is the only line that wins.
        val drawn = card(Rank.KING)
        val state = buildState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE),
            bot1 = listOf(Rank.THREE, Rank.TEN),
            bot2 = listOf(Rank.SEVEN, Rank.NINE),
            bot3 = listOf(Rank.EIGHT, Rank.NINE),
            currentPlayerIndex = 3,
            pendingCard = drawn,
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT3))
        assertEquals(emptyList(), input.turnQueue)

        val decision = planCoalitionDrawnCard(input, drawn)

        val useAction = decision as? CoalitionDrawnCardDecision.UseAction
        assertNotNull(useAction, "expected the King to be played, got $decision")
        assertEquals(listOf(CoalitionActionTarget(BOT1, 1)), useAction.action.targets)
        assertEquals(Rank.TEN, useAction.action.declaredRank)
    }

    @Test
    fun spendsItsOwnTurnToTriggerATossInThatEmptiesATeammatesHand() {
        // Bot2 holds a lone 9. Bot3 swapping a drawn 4 over its own 9 discards a 9, bot2
        // tosses its 9 in, and a player with no cards at all is a certain win.
        val drawn = card(Rank.FOUR)
        val state = buildState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE),
            bot1 = listOf(Rank.THREE, Rank.TEN),
            bot2 = listOf(Rank.NINE),
            bot3 = listOf(Rank.SEVEN, Rank.NINE),
            currentPlayerIndex = 3,
            pendingCard = drawn,
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT3))

        assertEquals(CoalitionDrawnCardDecision.Swap(position = 1), planCoalitionDrawnCard(input, drawn))
    }

    @Test
    fun swapsALowDrawnCardOverItsOwnJackAndDeclaresItToPlayTheSwap() {
        val drawn = card(Rank.TWO)
        val state = buildState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE),
            bot1 = listOf(Rank.THREE, Rank.NINE),
            bot2 = listOf(Rank.JACK, Rank.EIGHT),
            bot3 = listOf(Rank.SEVEN, Rank.NINE),
            currentPlayerIndex = 2,
            pendingCard = drawn,
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT2))

        assertEquals(
            CoalitionDrawnCardDecision.Swap(position = 0, declaredRank = Rank.JACK),
            planCoalitionDrawnCard(input, drawn),
        )
    }

    // --- turn start --------------------------------------------------------------------

    @Test
    fun takesAnUnplayedJackOffTheDiscardWhenItWinsTheRound() {
        // The caller sits on 1, so only a hand at 0 or below beats it: the Jack that puts
        // bot1's Ace beside bot2's Joker does, for certain. A draw wins only if it turns up
        // one of the few cards that can reach that — an Ace, a swap card, a King — so the
        // certain win has to be preferred to the gamble. (With the caller on 6 the position
        // did not discriminate: any low draw swapped over the 9 won too, and the toss-ins a
        // discarded card sets off made drawing as near-certain as taking.)
        val state = buildState(
            human = listOf(Rank.ACE, Rank.ACE, Rank.JOKER),
            bot1 = listOf(Rank.ACE, Rank.FIVE),
            bot2 = listOf(Rank.JOKER, Rank.NINE),
            bot3 = listOf(Rank.SEVEN, Rank.TEN),
            currentPlayerIndex = 2,
            discardPile = listOf(Rank.JACK),
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT2))
        assertEquals(Rank.JACK, input.discardTop?.rank)

        assertEquals(CoalitionTurnStart.TAKE_DISCARD, planCoalitionTurnStart(input))
    }

    @Test
    fun drawsWhenTheDiscardTopCannotHelp() {
        val state = buildState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE),
            bot1 = listOf(Rank.THREE, Rank.FIVE),
            bot2 = listOf(Rank.JOKER, Rank.NINE),
            bot3 = listOf(Rank.SEVEN, Rank.FIVE),
            currentPlayerIndex = 2,
            discardPile = listOf(Rank.SEVEN),
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT2))

        assertEquals(CoalitionTurnStart.DRAW, planCoalitionTurnStart(input))
    }

    @Test
    fun prefersASwapThatLowersItsOwnTotalWhenNothingBetterExists() {
        val drawn = card(Rank.TWO)
        val state = buildState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE),
            bot1 = listOf(Rank.SIX, Rank.FIVE),
            bot2 = listOf(Rank.SEVEN, Rank.TEN),
            bot3 = listOf(Rank.NINE, Rank.EIGHT),
            currentPlayerIndex = 3,
            pendingCard = drawn,
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT3))

        assertEquals(CoalitionDrawnCardDecision.Swap(position = 0), planCoalitionDrawnCard(input, drawn))
    }

    // --- toss-in and action selection ---------------------------------------------------

    @Test
    fun tossesInEveryMatchingPositiveCardAndKingsButNeverJokers() {
        val state = buildState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE),
            bot1 = listOf(Rank.FIVE, Rank.KING, Rank.FIVE, Rank.JOKER),
            bot2 = listOf(Rank.NINE, Rank.EIGHT),
            bot3 = listOf(Rank.SEVEN, Rank.NINE),
            currentPlayerIndex = 2,
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT1))

        assertEquals(listOf(0, 2), planCoalitionTossIn(input, listOf(Rank.FIVE)))
        // A King is worth nothing to hold and buys a declaration on the way out.
        assertEquals(listOf(1), planCoalitionTossIn(input, listOf(Rank.KING)))
        // A Joker is worth -1; throwing it away would raise the hand.
        assertEquals(emptyList(), planCoalitionTossIn(input, listOf(Rank.JOKER)))
        assertEquals(emptyList(), planCoalitionTossIn(input, listOf(Rank.TWO)))
    }

    @Test
    fun playsAQueuedTossInJackWhenASwapHelpsAndSkipsPeeksAndAces() {
        val state = buildState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE),
            bot1 = listOf(Rank.THREE, Rank.FIVE),
            bot2 = listOf(Rank.JOKER, Rank.NINE),
            bot3 = listOf(Rank.SEVEN, Rank.FIVE),
            currentPlayerIndex = 2,
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT2))

        assertTrue(shouldCoalitionUseAction(input, card(Rank.JACK)))
        // Every card here is declared, so a peek has nothing to reveal; and an Ace hurts a
        // teammate whatever the position.
        assertTrue(!shouldCoalitionUseAction(input, card(Rank.SEVEN)))
        assertTrue(!shouldCoalitionUseAction(input, card(Rank.ACE)))
        assertEquals(emptyList(), planCoalitionActionTargets(input, card(Rank.NINE)).targets)
    }

    @Test
    fun aPeekAtAnUnreadCardIsWorthPlayingWhenATeammateCanThenNameIt() {
        // The caller sits on 2. Bot2 holds an Ace and a card it has never read; bot3, still
        // to play, holds a King. Read the card and bot3 can declare it out of bot2's hand,
        // leaving bot2 on 1 — a win. Unread, it cannot be named, and the round is lost.
        val state = buildState(
            human = listOf(Rank.ACE, Rank.ACE),
            bot1 = listOf(Rank.SIX, Rank.SIX),
            bot2 = listOf(Rank.NINE, Rank.ACE),
            bot3 = listOf(Rank.KING, Rank.THREE),
            currentPlayerIndex = 2,
            unread = mapOf(BOT2 to listOf(0)),
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT2))
        assertTrue(!input.members.first { it.id == BOT2 }.cards[0].known, "the fixture read the card")

        assertTrue(shouldCoalitionUseAction(input, card(Rank.SEVEN)), "the peek was not worth playing")
        assertEquals(
            listOf(BotActionTarget(BOT2, 0)),
            planCoalitionActionTargets(input, card(Rank.SEVEN)).targets,
        )
        // A 9 looks at a teammate's card, and none of theirs is unread.
        assertEquals(emptyList(), planCoalitionActionTargets(input, card(Rank.NINE)).targets)
    }

    // --- the rule ------------------------------------------------------------------------

    @Test
    fun neverTargetsTheVintoCallerEvenWhenThatWouldBeTheBestSwap() {
        // The caller is sitting on a Joker the coalition can see. Taking it would win the
        // round outright, and the rule says it cannot be touched.
        val jack = card(Rank.JACK)
        val state = buildState(
            human = listOf(Rank.JOKER, Rank.TWO, Rank.THREE),
            bot1 = listOf(Rank.THREE, Rank.FIVE),
            bot2 = listOf(Rank.FOUR, Rank.NINE),
            bot3 = listOf(Rank.SEVEN, Rank.FIVE),
            currentPlayerIndex = 2,
            pendingCard = jack,
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT2))

        val decision = planCoalitionActionTargets(input, jack)

        assertTrue(decision.targets.none { it.playerId == HUMAN })
        // Not merely absent from this answer: the caller is not in the search's hands at all.
        assertTrue(input.members.none { it.id == HUMAN })
    }

    @Test
    fun aFullFiveCardTableWithADrawnKingStillProducesADecision() {
        val drawn = card(Rank.KING)
        val state = buildState(
            human = listOf(Rank.TWO, Rank.THREE, Rank.ACE, Rank.FOUR, Rank.JOKER),
            bot1 = listOf(Rank.THREE, Rank.TEN, Rank.JACK, Rank.SIX, Rank.TWO),
            bot2 = listOf(Rank.EIGHT, Rank.NINE, Rank.QUEEN, Rank.FIVE, Rank.ACE),
            bot3 = listOf(Rank.SEVEN, Rank.NINE, Rank.KING, Rank.FOUR, Rank.JACK),
            currentPlayerIndex = 1,
            pendingCard = drawn,
            knownHumanCards = listOf(0, 1),
        )
        val input = assertNotNull(buildCoalitionPlanInput(state, BOT1))

        // The TypeScript asserts a wall-clock bound here. A timing assertion is not portable
        // across six targets — a Kotlin/Native test machine is not a Node one — so what is
        // checked is that the search terminates and commits to something.
        val decision = planCoalitionDrawnCard(input, drawn)

        assertTrue(decision is CoalitionDrawnCardDecision.UseAction || decision is CoalitionDrawnCardDecision.Swap)
    }
}
