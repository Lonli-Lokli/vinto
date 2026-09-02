package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * These test the *rules* in the generator, not its judgement.
 *
 * Which swaps are worth searching is a heuristic and may differ from TypeScript. Which moves
 * are legal is not: a generator that proposes an illegal move produces either a bot that
 * cheats or one the engine rejects mid-game, and the difference between those two outcomes
 * is only which of them you notice.
 */
class MoveGeneratorTest {

    private fun memory() = BotMemory("bot-1", Difficulty.HARD, Random(1))

    private fun known(vararg cards: Pair<Int, Rank>): Map<Int, CardMemory> =
        cards.associate { (position, rank) ->
            position to CardMemory(
                testCard(rank, "${rank.serialName}_$position"),
                confidence = 1.0,
                lastSeen = 0,
                observations = 1,
            )
        }

    // Mirrors MctsGameState's own breadth; a builder here would be more machinery than the
    // tests it serves.
    @Suppress("LongParameterList")
    private fun state(
        players: List<MctsPlayerState>,
        currentIndex: Int = 0,
        discardTop: game.vinto.shapes.Card? = null,
        deckSize: Int = 20,
        isTossInPhase: Boolean = false,
        tossInRanks: List<Rank> = emptyList(),
        pendingCard: game.vinto.shapes.Card? = null,
        turnCount: Int = 20,
        vintoCallerId: String? = null,
        coalitionLeaderId: String? = null,
        hiddenCards: Map<String, game.vinto.shapes.Card> = emptyMap(),
    ) = MctsGameState(
        players = players,
        currentPlayerIndex = currentIndex,
        botPlayerId = players[currentIndex].id,
        discardPileTop = discardTop,
        discardPile = Pile(),
        deckSize = deckSize,
        botMemory = memory(),
        hiddenCards = hiddenCards,
        pendingCard = pendingCard,
        // Committed, so a pending card offers only its action and the tests read the aims alone.
        pendingOrigin = pendingCard?.let { PendingOrigin.COMMITTED },
        isTossInPhase = isTossInPhase,
        tossInRanks = tossInRanks,
        turnCount = turnCount,
        vintoCallerId = vintoCallerId,
        coalitionLeaderId = coalitionLeaderId,
    )

    private fun seat(id: String, cards: Int = 4, knownCards: Map<Int, CardMemory> = emptyMap()) =
        MctsPlayerState(id, cardCount = cards, knownCards = knownCards)

    @Test
    fun aTossInWindowOffersOnlyTossingInOrPassing() {
        val moves = MoveGenerator.generateMoves(
            state(
                listOf(seat("bot-1", knownCards = known(0 to Rank.SEVEN)), seat("p2")),
                isTossInPhase = true,
                tossInRanks = listOf(Rank.SEVEN),
            ),
        )

        assertTrue(moves.all { it.type == MctsMoveType.TOSS_IN || it.type == MctsMoveType.PASS })
    }

    @Test
    fun allMatchingCardsGoInAsOneMove() {
        // The rules resolve a toss-in as a single act; splitting it would let the search
        // explore throwing one of a pair and keeping the other.
        val moves = MoveGenerator.generateMoves(
            state(
                listOf(seat("bot-1", knownCards = known(0 to Rank.SEVEN, 2 to Rank.SEVEN)), seat("p2")),
                isTossInPhase = true,
                tossInRanks = listOf(Rank.SEVEN),
            ),
        )

        val tossIn = moves.single { it.type == MctsMoveType.TOSS_IN }
        assertEquals(listOf(0, 2), tossIn.tossInPositions)
    }

    @Test
    fun theBotThrowsOnlyWhatItRemembersEvenWhenTheWorldSaysItHoldsAMatch() {
        // A sampled world may deal the bot a 7 it has never read. The real bot cannot know
        // that, so the move is not offered — a rival's unread match, which the rival knows
        // about, is.
        val hidden = mapOf("bot-1-0" to testCard(Rank.SEVEN, "7_0"), "p2-0" to testCard(Rank.SEVEN, "7_1"))
        val self = MoveGenerator.generateMoves(
            state(
                listOf(seat("bot-1"), seat("p2")),
                isTossInPhase = true,
                tossInRanks = listOf(Rank.SEVEN),
                hiddenCards = hidden,
            ),
        )
        assertFalse(self.any { it.type == MctsMoveType.TOSS_IN }, "the bot tossed a card it never read")

        val rival = MoveGenerator.generateMoves(
            state(
                listOf(seat("bot-1"), seat("p2")),
                currentIndex = 1,
                isTossInPhase = true,
                tossInRanks = listOf(Rank.SEVEN),
                hiddenCards = hidden,
            ).copy(botPlayerId = "bot-1"),
        )
        assertTrue(rival.any { it.type == MctsMoveType.TOSS_IN })
    }

    @Test
    fun theDiscardPileIsOnlyTakeableWhenItsActionIsUnused() {
        val players = listOf(seat("bot-1"), seat("p2"))

        val unused = testCard(Rank.QUEEN, "Q_0")
        assertTrue(
            MoveGenerator.generateMoves(state(players, discardTop = unused))
                .any { it.type == MctsMoveType.TAKE_DISCARD },
        )

        val alreadyPlayed = unused.copy(played = true)
        assertFalse(
            MoveGenerator.generateMoves(state(players, discardTop = alreadyPlayed))
                .any { it.type == MctsMoveType.TAKE_DISCARD },
        )

        // A number card has no action to play, so it cannot be taken either.
        val numberCard = testCard(Rank.THREE, "3_0")
        assertFalse(
            MoveGenerator.generateMoves(state(players, discardTop = numberCard))
                .any { it.type == MctsMoveType.TAKE_DISCARD },
        )
    }

    @Test
    fun thereIsNothingToDrawFromAnEmptyDeck() {
        val moves = MoveGenerator.generateMoves(
            state(listOf(seat("bot-1"), seat("p2")), deckSize = 0),
        )
        assertFalse(moves.any { it.type == MctsMoveType.DRAW })
    }

    @Test
    fun theCoalitionNeverTargetsTheVintoCaller() {
        // The final-round rule: nobody may interact with the caller's cards. This is the one
        // the search must never propose breaking, because the engine would reject it and the
        // bot would sit there having chosen an impossible move.
        val players = listOf(seat("bot-1"), seat("caller"), seat("p3"))
        val peekState = state(
            players,
            pendingCard = testCard(Rank.NINE, "9_0"),
            vintoCallerId = "caller",
            coalitionLeaderId = "bot-1",
        )

        val moves = MoveGenerator.generateMoves(peekState)
        assertTrue(moves.isNotEmpty(), "expected peek moves")
        assertTrue(
            moves.none { move -> move.targets.any { it.playerId == "caller" } },
            "the coalition was offered the Vinto caller's cards",
        )
    }

    @Test
    fun theVintoCallerMayStillTargetTheCoalition() {
        // The restriction runs one way only — the caller is not the one being protected from.
        val players = listOf(seat("caller"), seat("p2"), seat("p3"))
        val moves = MoveGenerator.generateMoves(
            state(
                players,
                pendingCard = testCard(Rank.NINE, "9_0"),
                vintoCallerId = "caller",
                coalitionLeaderId = "p2",
            ),
        )
        assertTrue(moves.any { move -> move.targets.any { it.playerId == "p2" } })
    }

    @Test
    fun peekMovesOnlyEverTargetUnknownCards() {
        // Peeking at a card you already know is a wasted action, so it is not offered.
        val self = seat("bot-1", cards = 4, knownCards = known(0 to Rank.KING, 1 to Rank.TWO))
        val moves = MoveGenerator.generateMoves(
            state(listOf(self, seat("p2")), pendingCard = testCard(Rank.SEVEN, "7_0")),
        )

        val peeked = moves.flatMap { it.targets }.map { it.position }.toSet()
        assertEquals(setOf(2, 3), peeked)
    }

    @Test
    fun jackAndQueenAlwaysTakeTwoCardsFromDifferentPlayers() {
        val players = listOf(seat("bot-1", knownCards = known(0 to Rank.TEN)), seat("p2"), seat("p3"))

        for (rank in listOf(Rank.JACK, Rank.QUEEN)) {
            val moves = MoveGenerator.generateMoves(
                state(players, pendingCard = testCard(rank, "${rank.serialName}_0")),
            )
            assertTrue(moves.isNotEmpty(), "$rank generated no moves")

            for (move in moves) {
                assertEquals(2, move.targets.size, "$rank produced ${move.targets.size} targets")
                assertTrue(
                    move.targets[0].playerId != move.targets[1].playerId,
                    "$rank targeted the same player twice",
                )
            }
        }
    }

    @Test
    fun everyGeneratedMoveIsLegalInTheStateThatProducedIt() {
        // The blunt version of all of the above, over a spread of positions.
        val positions = listOf(
            state(listOf(seat("bot-1"), seat("p2"))),
            state(listOf(seat("bot-1"), seat("p2")), pendingCard = testCard(Rank.QUEEN, "Q_0")),
            state(listOf(seat("bot-1"), seat("p2")), pendingCard = testCard(Rank.ACE, "A_0")),
            state(listOf(seat("bot-1"), seat("p2")), pendingCard = testCard(Rank.KING, "K_0")),
            state(listOf(seat("bot-1"), seat("p2")), isTossInPhase = true, tossInRanks = listOf(Rank.TWO)),
        )

        for (position in positions) {
            for (move in MoveGenerator.generateMoves(position)) {
                assertTrue(
                    MoveGenerator.isLegalMove(position, move),
                    "generated an illegal ${move.type} move",
                )
            }
        }
    }

    @Test
    fun vintoIsAskedAtTheEndOfATurnAndNotInTheOpening() {
        // The rules: Vinto is declared at the end of a turn, and nobody calls before everyone
        // has had a couple of turns. Neither is a judgement about the hand.
        val players = listOf(seat("bot-1"), seat("p2"))

        assertFalse(
            MoveGenerator.generateMoves(state(players, turnCount = 20))
                .any { it.type == MctsMoveType.CALL_VINTO },
            "Vinto was offered at the start of a turn",
        )
        assertFalse(
            MoveGenerator.generateMoves(state(players, turnCount = 2).copy(awaitingVintoDecision = true))
                .any { it.type == MctsMoveType.CALL_VINTO },
        )
        val endOfTurn = MoveGenerator.generateMoves(state(players, turnCount = 20).copy(awaitingVintoDecision = true))
        assertTrue(endOfTurn.any { it.type == MctsMoveType.CALL_VINTO })
        assertTrue(endOfTurn.any { it.type == MctsMoveType.PASS })
        assertFalse(
            MoveGenerator.generateMoves(
                state(players, turnCount = 20, vintoCallerId = "p2").copy(awaitingVintoDecision = true),
            ).any { it.type == MctsMoveType.CALL_VINTO },
            "a second call was offered after somebody had called",
        )
    }

    @Test
    fun aDrawnCardMayBePlayedSwappedInAnywhereOrDiscarded() {
        val self = seat("bot-1", cards = 3, knownCards = known(0 to Rank.TEN))
        val moves = MoveGenerator.generateMoves(
            state(listOf(self, seat("p2")), pendingCard = testCard(Rank.NINE, "9_0"))
                .copy(pendingOrigin = PendingOrigin.DRAWN),
        )

        assertEquals(setOf(0, 1, 2), moves.filter { it.type == MctsMoveType.SWAP }.map { it.swapPosition }.toSet())
        assertEquals(1, moves.count { it.type == MctsMoveType.DISCARD })
        assertTrue(moves.any { it.type == MctsMoveType.USE_ACTION })
        assertTrue(moves.all { it.cardInPlay == Rank.NINE }, "a reply did not carry the rank it was about")
    }

    @Test
    fun aCommittedCardWithNowhereToAimCanOnlyBePutDown() {
        // A peek-own by a bot that has already read every card of its own has nowhere to look.
        val self = seat("bot-1", cards = 2, knownCards = known(0 to Rank.KING, 1 to Rank.TWO))
        val moves = MoveGenerator.generateMoves(
            state(listOf(self, seat("p2")), pendingCard = testCard(Rank.SEVEN, "7_0")),
        )

        assertEquals(listOf(MctsMoveType.DISCARD), moves.map { it.type })
    }

    @Test
    fun aKingNamesOnlyCardsItsHolderKnows() {
        val self = seat("bot-1", cards = 3, knownCards = known(0 to Rank.TEN, 1 to Rank.TEN))
        val rival = seat("p2", cards = 3, knownCards = known(2 to Rank.QUEEN))
        val moves = MoveGenerator.generateMoves(
            state(listOf(self, rival), pendingCard = testCard(Rank.KING, "K_0")),
        )

        val named = moves.filter { it.type == MctsMoveType.USE_ACTION }
        assertEquals(3, named.size)
        assertTrue(named.all { it.declaredRank != null && it.targets.size == 1 })
        assertTrue(named.any { it.targets.single() == MctsActionTarget("p2", 2) && it.declaredRank == Rank.QUEEN })
        assertTrue(named.none { it.targets.single() == MctsActionTarget("bot-1", 2) }, "a blind card was named")
    }
}
