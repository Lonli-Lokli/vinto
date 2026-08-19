package game.vinto.engine

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.ActionTarget
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.InitiatorIdPayload
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules as written, ported from `packages/engine/src/lib/__tests__/rules.test.ts`.
 *
 * Where the card-action files walk one rank through its whole life, this file checks the
 * statements in `docs/game-engine/VINTO_RULES.md` one at a time. Some overlap is deliberate:
 * these read as the rule, those read as the flow, and a change that breaks one and not the
 * other is worth being told about twice.
 *
 * **Three of the TypeScript's cases are empty.** `should apply penalty if declaration is
 * wrong`, `should prevent interaction with Vinto caller cards during final round` and
 * `should not allow taking non-action cards from discard` contain a comment reading "Test
 * documents expected behavior" and no assertion at all — they pass unconditionally, including
 * against an engine that does none of it. All three rules are real and all three are
 * enforced, so they are written properly here.
 */
class RulesTest {

    private fun executeQueenSwap(playerId: String) =
        GameAction.ExecuteQueenSwap(PlayerIdPayload(playerId))

    private fun skipQueenSwap(playerId: String) =
        GameAction.SkipQueenSwap(PlayerIdPayload(playerId))

    // --- Jack: swap any two face-down cards -----------------------------------------------

    @Test
    fun aJackSwapsTwoCardsBelongingToDifferentPlayers() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.ACE, "p2c1"), testCard(Rank.TWO, "p2c2")),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.JACK, "jack1"), "p1",
                targets = listOf(ActionTarget("p1", 0), ActionTarget("p2", 1)),
            ),
        )

        val next = unsafeReduce(state, executeQueenSwap("p1"))

        assertEquals(Rank.TWO, next.players[0].cards[0].rank)
        assertEquals(Rank.KING, next.players[1].cards[1].rank)
        assertEquals("jack1", next.discardPile.peekTop()?.id)
    }

    @Test
    fun aJackReachesAnyPositionInEitherHand() {
        // The TypeScript titles this "two cards from same player", but its targets are p1 and
        // p2 — what it actually covers is a swap deep into a longer hand, so that is the name.
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.ACE, "p1c2")),
                ),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(
                        testCard(Rank.TEN, "p2c1"), testCard(Rank.NINE, "p2c2"),
                        testCard(Rank.EIGHT, "p2c3"), testCard(Rank.SEVEN, "p2c4"),
                    ),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.JACK, "jack1"), "p1",
                targets = listOf(ActionTarget("p1", 0), ActionTarget("p2", 3)),
            ),
        )

        val next = unsafeReduce(state, executeQueenSwap("p1"))

        assertEquals(Rank.SEVEN, next.players[0].cards[0].rank)
        assertEquals(Rank.KING, next.players[1].cards[3].rank)
    }

    // --- Queen: peek two, optionally swap -------------------------------------------------

    private fun queenAimed() = testState(
        subPhase = GameSubPhase.AWAITING_ACTION,
        players = listOf(
            testPlayer("p1", "Player 1", isHuman = true, cards = listOf(testCard(Rank.JACK, "p1c1"))),
            testPlayer(
                "p2", "Player 2", isHuman = false,
                cards = listOf(testCard(Rank.KING, "p2c1"), testCard(Rank.ACE, "p2c2")),
            ),
        ),
        pendingAction = pending(
            testCard(Rank.QUEEN, "queen1"), "p1",
            targets = listOf(ActionTarget("p1", 0), ActionTarget("p2", 1)),
        ),
    )

    @Test
    fun aQueenMayPeekAndLeaveEverythingWhereItIs() {
        val next = unsafeReduce(queenAimed(), skipQueenSwap("p1"))

        assertEquals(Rank.JACK, next.players[0].cards[0].rank)
        assertEquals(Rank.KING, next.players[1].cards[0].rank)
        assertEquals(Rank.ACE, next.players[1].cards[1].rank)
        assertEquals("queen1", next.discardPile.peekTop()?.id)
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.QUEEN) == true)
    }

    @Test
    fun aQueenMayPeekAndThenSwapWhatItSaw() {
        val next = unsafeReduce(queenAimed(), executeQueenSwap("p1"))

        assertEquals(Rank.ACE, next.players[0].cards[0].rank)
        assertEquals(Rank.KING, next.players[1].cards[0].rank)
        assertEquals(Rank.JACK, next.players[1].cards[1].rank)
    }

    // --- King: declare a rank and play its action -----------------------------------------

    @Test
    fun aKingsDeclarationOpensTheTossInAndTheKingHitsThePileFirst() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.ACE, "p2c1"), testCard(Rank.ACE, "p2c2")),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.KING, "king1"), "p1",
                targets = listOf(ActionTarget("p2", 0)),
            ),
        )

        var next = unsafeReduce(state, useCardAction("p1"))
        next = unsafeReduce(next, declareKing("p1", Rank.ACE))

        assertNotNull(next.activeTossIn)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.ACE) == true)
        assertEquals("p1", next.activeTossIn?.initiatorId)
        // The declared Ace is still in play as a pending action, so the King is on top.
        assertEquals("king1", next.discardPile.peekTop()?.id)
    }

    // --- Ace, 7/8, 9/10 -------------------------------------------------------------------

    @Test
    fun anAceMakesTheChosenPlayerDrawOneCard() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            drawPile = pileOf(testCard(Rank.KING, "penalty1"), testCard(Rank.QUEEN, "card2")),
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.SEVEN, "p2c1"), testCard(Rank.EIGHT, "p2c2")),
                ),
            ),
            pendingAction = pending(testCard(Rank.ACE, "ace1"), "p1"),
        )

        val next = unsafeReduce(state, selectTarget("p1", "p2", 0))

        assertEquals(3, next.players[1].cards.size)
        assertEquals(1, next.drawPile.size)
    }

    @Test
    fun aSevenPeeksOneOfYourOwnCards() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
                testPlayer("p2", "Player 2", isHuman = false),
            ),
            pendingAction = pending(testCard(Rank.SEVEN, "seven1"), "p1"),
        )

        var next = unsafeReduce(state, selectTarget("p1", "p1", 1))
        next = unsafeReduce(next, confirmPeek("p1"))

        assertEquals("seven1", next.discardPile.peekTop()?.id)
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
        assertTrue(next.activeTossIn?.ranks?.contains(Rank.SEVEN) == true)
        assertTrue(1 in next.players[0].knownCardPositions, "the peeked position is now known")
    }

    @Test
    fun aNinePeeksOneOfSomebodyElsesCards() {
        val state = testState(
            subPhase = GameSubPhase.AWAITING_ACTION,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.KING, "p2c1"), testCard(Rank.ACE, "p2c2")),
                ),
            ),
            pendingAction = pending(testCard(Rank.NINE, "nine1"), "p1"),
        )

        var next = unsafeReduce(state, selectTarget("p1", "p2", 0))
        next = unsafeReduce(next, confirmPeek("p1"))

        assertEquals("nine1", next.discardPile.peekTop()?.id)
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, next.subPhase)
    }

    // --- Toss-in --------------------------------------------------------------------------

    @Test
    fun anyPlayerMayTossInAMatchingActionCardAndItsActionIsQueued() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.ACE, "p2c1"), testCard(Rank.SEVEN, "p2c2")),
                ),
                testPlayer(
                    "p3", "Player 3", isHuman = false,
                    cards = listOf(testCard(Rank.ACE, "p3c1"), testCard(Rank.EIGHT, "p3c2")),
                ),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.ACE), initiatorId = "p1"),
        )

        var next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))
        assertEquals(1, next.players.first { it.id == "p2" }.cards.size)
        assertEquals(1, next.activeTossIn?.queuedActions?.size)
        assertTrue(next.activeTossIn?.participants?.contains("p2") == true)

        next = unsafeReduce(next, participateInTossIn("p3", listOf(0)))
        assertEquals(1, next.players[2].cards.size)
        assertEquals(2, next.activeTossIn?.queuedActions?.size)
        assertTrue(next.activeTossIn?.participants?.contains("p3") == true)
    }

    @Test
    fun aTossedInCardWithNoActionJustGoesToThePile() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
                testPlayer(
                    "p2", "Player 2", isHuman = false,
                    cards = listOf(testCard(Rank.TWO, "p2c1"), testCard(Rank.SEVEN, "p2c2")),
                ),
                testPlayer(
                    "p3", "Player 3", isHuman = false,
                    cards = listOf(testCard(Rank.TWO, "p3c1"), testCard(Rank.EIGHT, "p3c2")),
                ),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.TWO), initiatorId = "p1"),
        )

        var next = unsafeReduce(state, participateInTossIn("p2", listOf(0)))
        assertEquals(1, next.players.first { it.id == "p2" }.cards.size)
        assertEquals(0, next.activeTossIn?.queuedActions?.size)
        assertEquals(1, next.discardPile.size)

        next = unsafeReduce(next, participateInTossIn("p3", listOf(0)))
        assertEquals(1, next.players[2].cards.size)
        assertEquals(0, next.activeTossIn?.queuedActions?.size)
        assertEquals(2, next.discardPile.size)
    }

    @Test
    fun aTossInOfACardThatIsNotThereIsRefused() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 1,
            drawPile = pileOf(testCard(Rank.KING, "penalty1")),
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer("p2", "Player 2", isHuman = false),
            ),
            activeTossIn = tossIn(ranks = listOf(Rank.ACE), initiatorId = "p1"),
        )

        assertTrue(rejects(state, participateInTossIn("p2", listOf(0))))
    }

    @Test
    fun finishingTheTossInPeriodDoesNotAdvanceTheTurnCounter() {
        val state = testState(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            turnNumber = 5,
            activeTossIn = tossIn(
                ranks = listOf(Rank.ACE),
                initiatorId = "p1",
                participants = listOf("p2", "p3"),
            ),
        )

        val next = unsafeReduce(state, GameAction.FinishTossInPeriod(InitiatorIdPayload("p1")))

        assertEquals(5, next.turnNumber)
    }

    // --- Declaring the rank of a swapped-out card -----------------------------------------

    @Test
    fun aCorrectDeclarationPlaysTheSwappedOutCardsAction() {
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.ACE, "drawn-card"), "p1",
                actionPhase = ActionPhase.CHOOSING_ACTION,
            ),
        )

        val next = unsafeReduce(state, swapCard("p1", 0, Rank.KING))

        assertTrue(0 in next.players[0].knownCardPositions)
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
    }

    @Test
    fun aWrongDeclarationCostsAPenaltyCardAndPlaysNothing() {
        // Empty in the TypeScript — a comment and no assertion. The rule is real: "If wrong →
        // take one penalty card face-down from deck", and the engine does it.
        val state = testState(
            subPhase = GameSubPhase.CHOOSING,
            drawPile = pileOf(testCard(Rank.JACK, "penalty1")),
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.ACE, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
            ),
            pendingAction = pending(
                testCard(Rank.SEVEN, "drawn-card"), "p1",
                actionPhase = ActionPhase.CHOOSING_ACTION,
            ),
        )

        // Position 0 holds an Ace, not a King.
        val next = unsafeReduce(state, swapCard("p1", 0, Rank.KING))

        assertEquals(3, next.players[0].cards.size, "no penalty card was taken")
        assertEquals(0, next.drawPile.size)
        assertEquals(
            GameSubPhase.TOSS_QUEUE_ACTIVE,
            next.subPhase,
            "a wrong declaration must not hand the player an action to play",
        )
        assertNull(next.pendingAction)
    }

    // --- Vinto ----------------------------------------------------------------------------

    @Test
    fun callingVintoTriggersTheFinalRound() {
        val state = testState(
            subPhase = GameSubPhase.IDLE,
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer("p2", "Player 2", isHuman = false),
                testPlayer("p3", "Player 3", isHuman = false),
            ),
        )

        val next = unsafeReduce(state, callVinto("p1"))

        assertTrue(next.finalTurnTriggered)
        assertEquals("p1", next.vintoCallerId)
        assertTrue(next.players[0].isVintoCaller)
    }

    @Test
    fun vintoCannotBeCalledTwice() {
        val state = testState(
            subPhase = GameSubPhase.IDLE,
            finalTurnTriggered = true,
            vintoCallerId = "p2",
            players = listOf(
                testPlayer("p1", "Player 1", isHuman = true),
                testPlayer("p2", "Player 2", isHuman = false),
            ),
        )

        assertTrue(rejects(state, callVinto("p1")))
        assertEquals("p2", state.vintoCallerId)
        assertTrue(!state.players[0].isVintoCaller)
    }

    @Test
    fun theCoalitionCannotTouchTheVintoCallersCardsInTheFinalRound() {
        // Empty in the TypeScript — a comment and no assertion. This is the rule that makes
        // calling Vinto a commitment rather than a free option, so it is worth a real test.
        val state = testState(
            phase = GamePhase.FINAL,
            subPhase = GameSubPhase.AWAITING_ACTION,
            currentPlayerIndex = 1,
            finalTurnTriggered = true,
            vintoCallerId = "p1",
            coalitionLeaderId = "p2",
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
                testPlayer("p2", "Player 2", isHuman = false, cards = listOf(testCard(Rank.TWO, "p2c1"))),
                testPlayer("p3", "Player 3", isHuman = false, cards = listOf(testCard(Rank.THREE, "p3c1"))),
            ),
            pendingAction = pending(testCard(Rank.JACK, "jack1"), "p2"),
        )

        assertTrue(rejects(state, selectTarget("p2", "p1", 0)), "the caller's card was targetable")
        // The rest of the table is still fair game — it protects the caller, not everyone.
        assertTrue(!rejects(state, selectTarget("p2", "p3", 0)))
    }

    // --- Taking from the discard pile -----------------------------------------------------

    @Test
    fun anUnusedActionCardCanBeTakenFromTheDiscard() {
        val state = testState(
            subPhase = GameSubPhase.IDLE,
            discardPile = pileOf(testCard(Rank.QUEEN, "disc1")),
        )

        val next = unsafeReduce(state, playDiscard("p1"))

        assertEquals("disc1", next.pendingAction?.card?.id)
        assertEquals(0, next.discardPile.size)
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
    }

    @Test
    fun aCardWithNoActionCannotBeTakenFromTheDiscard() {
        // Empty in the TypeScript — a comment and no assertion. The rule is explicit:
        // "Allowed only if the top discard is an unused action card (7-K)".
        val state = testState(
            subPhase = GameSubPhase.IDLE,
            discardPile = pileOf(testCard(Rank.FIVE, "disc1")),
        )

        assertTrue(rejects(state, playDiscard("p1")))
    }

    @Test
    fun anAlreadyPlayedActionCardCannotBeTakenEither() {
        val state = testState(
            subPhase = GameSubPhase.IDLE,
            discardPile = pileOf(testCard(Rank.QUEEN, "disc1").copy(played = true)),
        )

        assertTrue(rejects(state, playDiscard("p1")))
    }

    @Test
    fun takingFromTheDiscardGoesStraightToAimingWithNoChanceToSwap() {
        val state = testState(
            subPhase = GameSubPhase.IDLE,
            discardPile = pileOf(testCard(Rank.JACK, "disc1")),
        )

        val next = unsafeReduce(state, playDiscard("p1"))

        // `awaiting_action`, not `choosing`: the rule says the action must be played, and the
        // state machine is what enforces it — there is no branch offering a swap from here.
        assertEquals(GameSubPhase.AWAITING_ACTION, next.subPhase)
        assertEquals(Rank.JACK, next.pendingAction?.card?.rank)
        assertTrue(rejects(next, swapCard("p1", 0)), "a card taken from the discard was swappable")
    }

    // --- Values and turn flow --------------------------------------------------------------

    @Test
    fun everyRankIsWorthWhatTheRulesSay() {
        assertEquals(2, getCardValue(Rank.TWO))
        assertEquals(6, getCardValue(Rank.SIX))
        assertEquals(7, getCardValue(Rank.SEVEN))
        assertEquals(10, getCardValue(Rank.JACK))
        assertEquals(10, getCardValue(Rank.QUEEN))
        assertEquals(0, getCardValue(Rank.KING))
        assertEquals(1, getCardValue(Rank.ACE))
        assertEquals(-1, getCardValue(Rank.JOKER))
    }

    @Test
    fun aTurnRunsDrawThenSwapOrDiscardThenTossIn() {
        var state = testState(
            subPhase = GameSubPhase.IDLE,
            turnNumber = 5,
            drawPile = pileOf(testCard(Rank.ACE, "drawn1")),
            players = listOf(
                testPlayer(
                    "p1", "Player 1", isHuman = true,
                    cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.QUEEN, "p1c2")),
                ),
                testPlayer("p2", "Player 2", isHuman = false),
            ),
        )

        state = unsafeReduce(state, drawCard("p1"))
        assertEquals(GameSubPhase.CHOOSING, state.subPhase)
        assertEquals("drawn1", state.pendingAction?.card?.id)

        state = unsafeReduce(state, swapCard("p1", 0))
        assertEquals(GameSubPhase.TOSS_QUEUE_ACTIVE, state.subPhase)
        assertNull(state.pendingAction)
    }
}
