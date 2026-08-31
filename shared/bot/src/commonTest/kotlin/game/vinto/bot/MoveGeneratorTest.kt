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
        isTossInPhase = isTossInPhase,
        tossInRanks = tossInRanks,
        turnCount = turnCount,
        vintoCallerId = vintoCallerId,
        coalitionLeaderId = coalitionLeaderId,
    )

    private fun seat(id: String, cards: Int = 4, knownCards: Map<Int, CardMemory> = emptyMap(), score: Double = 20.0) =
        MctsPlayerState(id, cardCount = cards, knownCards = knownCards, score = score)

    @Test
    fun aTossInWindowOffersOnlyTossingInOrPassing() {
        val hidden = mapOf("bot-1-0" to testCard(Rank.SEVEN, "7_0"))
        val moves = MoveGenerator.generateMoves(
            state(
                listOf(seat("bot-1"), seat("p2")),
                isTossInPhase = true,
                tossInRanks = listOf(Rank.SEVEN),
                hiddenCards = hidden,
            ),
        )

        assertTrue(moves.all { it.type == MctsMoveType.TOSS_IN || it.type == MctsMoveType.PASS })
    }

    @Test
    fun allMatchingCardsGoInAsOneMove() {
        // The rules resolve a toss-in as a single act; splitting it would let the search
        // explore throwing one of a pair and keeping the other.
        val hidden = mapOf(
            "bot-1-0" to testCard(Rank.SEVEN, "7_0"),
            "bot-1-2" to testCard(Rank.SEVEN, "7_1"),
        )
        val moves = MoveGenerator.generateMoves(
            state(
                listOf(seat("bot-1"), seat("p2")),
                isTossInPhase = true,
                tossInRanks = listOf(Rank.SEVEN),
                hiddenCards = hidden,
            ),
        )

        val tossIn = moves.single { it.type == MctsMoveType.TOSS_IN }
        assertEquals(listOf(0, 2), tossIn.tossInPositions)
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
    fun vintoIsNotOfferedInTheOpening() {
        val ahead = seat("bot-1", score = 0.0)
        val behind = seat("p2", score = 30.0)

        assertFalse(
            MoveGenerator.generateMoves(state(listOf(ahead, behind), turnCount = 2))
                .any { it.type == MctsMoveType.CALL_VINTO },
        )
        assertTrue(
            MoveGenerator.generateMoves(state(listOf(ahead, behind), turnCount = 20))
                .any { it.type == MctsMoveType.CALL_VINTO },
        )
    }

    @Test
    fun aKnownKingInAnOpponentHandMakesTheBotWaryOfCallingVinto() {
        // A six-point lead clears the base threshold of five, and nothing more.
        val ahead = seat("bot-1", score = 14.0)
        val plainOpponent = seat("p2", score = 20.0)
        assertTrue(
            MoveGenerator.generateMoves(state(listOf(ahead, plainOpponent)))
                .any { it.type == MctsMoveType.CALL_VINTO },
        )

        // A King can rearrange the table before the round ends, so the same lead is no
        // longer enough.
        val armed = seat("p2", score = 20.0, knownCards = known(0 to Rank.KING, 1 to Rank.QUEEN))
        assertFalse(
            MoveGenerator.generateMoves(state(listOf(ahead, armed)))
                .any { it.type == MctsMoveType.CALL_VINTO },
        )
    }
}
