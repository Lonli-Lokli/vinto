package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How [VintoRoundSolver] steers the live Vinto call — the wiring in
 * [MctsBotDecisionService.shouldCallVinto], not the solver's arithmetic (that is
 * [VintoRoundSolverTest]'s job).
 *
 * The contract under test, from [VintoCallWiring]:
 *
 *  - a believed-zero hand calls by right, and only *knowledge* can veto it — a solver that
 *    has seen nothing cannot say no. That last part is load-bearing: self-play games end
 *    only on a Vinto call, so a blind veto would make the endgame unreachable exactly when
 *    the bots play worst.
 *  - a believed hand of one to four points calls only when a confident solver approves —
 *    the only path by which a positive hand ever calls.
 *
 * Everything runs on HARD, whose memory is perfect and non-decaying, so what the bot
 * believes is exactly what the context says it has seen.
 */
class VintoCallWiringTest {

    private fun hand(vararg ranks: Rank): List<Card> =
        ranks.mapIndexed { index, rank -> testCard(rank, "${rank.serialName}_own$index") }

    private fun opponentHand(id: String, vararg ranks: Rank): Pair<String, List<Card>> =
        id to ranks.mapIndexed { index, rank -> testCard(rank, "${rank.serialName}_$id$index") }

    /**
     * A context where the bot has read its whole hand, and has seen exactly the opponent
     * cards in [seen] — everything else on the table is a card it has never looked at.
     */
    private fun contextWith(
        ownCards: List<Card>,
        opponents: List<Pair<String, List<Card>>>,
        seen: Map<String, Map<Int, Card>> = emptyMap(),
        turnNumber: Int = 20,
    ): BotDecisionContext {
        val bot = testPlayer("p1", "Bot", isHuman = false, cards = ownCards)
        val others = opponents.map { (id, cards) ->
            testPlayer(id, id, isHuman = false, cards = cards, knownCardPositions = emptyList())
        }
        val ownKnowledge = ownCards.mapIndexed { position, card -> position to card }.toMap()
        return botContext(
            "p1",
            testState(listOf(bot) + others, turnNumber = turnNumber),
            opponentKnowledge = seen + ("p1" to ownKnowledge),
        )
    }

    private fun service() = MctsBotDecisionService(Difficulty.HARD, Random(1))

    @Test
    fun aBlindSolverCannotVetoAZeroHand() {
        // Nobody has seen a single opponent card. The worst-case analysis is pure
        // assumption, and assumption does not overrule a hand worth calling on.
        val context = contextWith(
            ownCards = hand(Rank.JOKER, Rank.KING),
            opponents = listOf(
                opponentHand("p2", Rank.TWO, Rank.THREE),
                opponentHand("p3", Rank.FOUR, Rank.FIVE),
                opponentHand("p4", Rank.SIX, Rank.SIX),
            ),
        )

        assertTrue(service().shouldCallVinto(context), "a blind solver vetoed a -1 hand")
    }

    @Test
    fun aConfidentSolverVetoesAZeroHandItHasSeenBeaten() {
        // The bot has personally seen p2 holding Joker + King: an observed -1 against its
        // own zero. Every other card on the table is read too, so the solver speaks from
        // knowledge — and vetoes.
        val p2 = opponentHand("p2", Rank.JOKER, Rank.KING)
        val p3 = opponentHand("p3", Rank.SIX, Rank.SIX)
        val p4 = opponentHand("p4", Rank.SIX, Rank.SIX)
        val everySeatSeen = listOf(p2, p3, p4).associate { (id, cards) ->
            id to cards.mapIndexed { position, card -> position to card }.toMap()
        }

        val context = contextWith(
            ownCards = hand(Rank.KING),
            opponents = listOf(p2, p3, p4),
            seen = everySeatSeen,
        )

        assertFalse(
            service().shouldCallVinto(context),
            "called a zero into a hand it had already seen at -1",
        )
    }

    @Test
    fun aConfidentSolverEnablesASmallPositiveHand() {
        // Two points is not zero, so the plain rule alone would never call — but the bot
        // has read the entire table and the best anyone else can reach is worse. This is
        // the path that ends games where nobody ever assembles a zero.
        val p2 = opponentHand("p2", Rank.QUEEN, Rank.QUEEN)
        val p3 = opponentHand("p3", Rank.SIX, Rank.SIX)
        val p4 = opponentHand("p4", Rank.SEVEN, Rank.EIGHT)
        val everySeatSeen = listOf(p2, p3, p4).associate { (id, cards) ->
            id to cards.mapIndexed { position, card -> position to card }.toMap()
        }

        val context = contextWith(
            ownCards = hand(Rank.TWO),
            opponents = listOf(p2, p3, p4),
            seen = everySeatSeen,
        )

        assertTrue(
            service().shouldCallVinto(context),
            "refused a two-point call with the whole table read and beaten",
        )
    }

    @Test
    fun aBlindSolverDoesNotEnableAPositiveHand() {
        // The same two-point hand with nothing seen: the plain rule says no, and there is
        // no knowledge to overrule it with.
        val context = contextWith(
            ownCards = hand(Rank.TWO),
            opponents = listOf(
                opponentHand("p2", Rank.QUEEN, Rank.QUEEN),
                opponentHand("p3", Rank.SIX, Rank.SIX),
                opponentHand("p4", Rank.SEVEN, Rank.EIGHT),
            ),
        )

        assertFalse(service().shouldCallVinto(context), "a blind solver enabled a positive hand")
    }

    /** Four seats × [VintoCallWiring.LATE_GAME_LAPS] laps: the stalemate valve is open. */
    private val lateGameTurn = 4 * VintoCallWiring.LATE_GAME_LAPS + 1

    @Test
    fun deepIntoAStalledGameARelativelyBestHandCalls() {
        // Eight points would never pass the solver — but it is turn 49, every hand on the
        // table has been seen, and everyone else is expected to score worse. Waiting for a
        // provably safe call that will never come is how a game fails to end.
        val p2 = opponentHand("p2", Rank.SIX, Rank.SIX)
        val p3 = opponentHand("p3", Rank.SIX, Rank.FIVE)
        val p4 = opponentHand("p4", Rank.FIVE, Rank.FIVE)
        val everySeatSeen = listOf(p2, p3, p4).associate { (id, cards) ->
            id to cards.mapIndexed { position, card -> position to card }.toMap()
        }

        val stalled = contextWith(
            ownCards = hand(Rank.FIVE, Rank.THREE),
            opponents = listOf(p2, p3, p4),
            seen = everySeatSeen,
            turnNumber = lateGameTurn,
        )
        assertTrue(
            service().shouldCallVinto(stalled),
            "held the table's best hand in a stalled endgame and never called",
        )

        // The same table thirty turns earlier is not a stalemate yet, and eight points is
        // not a hand the solver signs off on.
        val early = contextWith(
            ownCards = hand(Rank.FIVE, Rank.THREE),
            opponents = listOf(p2, p3, p4),
            seen = everySeatSeen,
        )
        assertFalse(service().shouldCallVinto(early), "called an eight before the stalemate valve")
    }

    @Test
    fun theStalemateValveStillRefusesWhenAnOpponentIsExpectedToBeLower() {
        // Late or not, calling into a hand you expect to lose to is just losing on purpose.
        val p2 = opponentHand("p2", Rank.SIX, Rank.SIX)
        val p3 = opponentHand("p3", Rank.SIX, Rank.FIVE)
        val p4 = opponentHand("p4", Rank.TWO, Rank.THREE)
        val everySeatSeen = listOf(p2, p3, p4).associate { (id, cards) ->
            id to cards.mapIndexed { position, card -> position to card }.toMap()
        }

        val context = contextWith(
            ownCards = hand(Rank.FIVE, Rank.THREE),
            opponents = listOf(p2, p3, p4),
            seen = everySeatSeen,
            turnNumber = lateGameTurn,
        )

        assertFalse(
            service().shouldCallVinto(context),
            "the valve called an eight into an expected five",
        )
    }

    @Test
    fun aHandAboveTheEnablerCapNeverCallsHoweverGoodTheEvidence() {
        // Six points, whole table read, everyone else far worse — still no call. The
        // enabler is a narrow door, not a general "call whenever ahead" strategy: a
        // six-point lead is one lucky swap from gone.
        val p2 = opponentHand("p2", Rank.QUEEN, Rank.QUEEN)
        val p3 = opponentHand("p3", Rank.QUEEN, Rank.TEN)
        val p4 = opponentHand("p4", Rank.TEN, Rank.TEN)
        val everySeatSeen = listOf(p2, p3, p4).associate { (id, cards) ->
            id to cards.mapIndexed { position, card -> position to card }.toMap()
        }

        val context = contextWith(
            ownCards = hand(Rank.SIX),
            opponents = listOf(p2, p3, p4),
            seen = everySeatSeen,
        )

        assertFalse(service().shouldCallVinto(context), "called on a hand above the enabler cap")
    }
}
