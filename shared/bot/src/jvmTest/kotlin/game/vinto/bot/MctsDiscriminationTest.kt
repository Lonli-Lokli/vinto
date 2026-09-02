package game.vinto.bot

import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Pile
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Does the search *discriminate* — pick the right move when there is a right move?
 *
 * The rest of the suite proves the bot's moves are legal and that its parts behave. None of
 * that says the search chooses; the first version of it did not, and passed everything.
 * Every case here has one answer a careful player would give without thinking, and the
 * search has to give it across seeds — not because the game is deterministic but because the
 * answer is obvious enough that sampling noise must not reach it.
 *
 * JVM-only: each case is a dozen full searches at `HARD`, which is minutes on Wasm.
 */
class MctsDiscriminationTest {

    private val botId = "bot1"
    private val humanId = "human1"
    private val seeds = 1L..12L

    /** How many of the seeds must agree. One slip is sampling; two is a search that does not see. */
    private val bar = 11

    private fun deck() = Pile(List(30) { testCard(Rank.SIX, "deck-$it") })

    private fun service(seed: Long) = MctsBotDecisionService(Difficulty.HARD, Random(seed))

    private fun contextWith(
        botCards: List<Card>,
        humanCards: List<Card>,
        knownOfHuman: Map<Int, Card>,
        pending: Card?,
        subPhase: GameSubPhase,
        discardTop: Card = testCard(Rank.SIX, "discard-6").copy(played = true),
    ): BotDecisionContext {
        val botPlayer = testPlayer(botId, "Bot", isHuman = false, cards = botCards)
        val human = testPlayer(humanId, "Human", isHuman = true, cards = humanCards)
        val state = testState(
            players = listOf(botPlayer, human),
            subPhase = subPhase,
            turnNumber = 9,
            drawPile = deck(),
            discardPile = Pile(listOf(discardTop)),
        ).copy(
            pendingAction = pending?.let {
                PendingAction(
                    card = it,
                    playerId = botId,
                    actionPhase = if (subPhase == GameSubPhase.CHOOSING) {
                        ActionPhase.CHOOSING_ACTION
                    } else {
                        ActionPhase.SELECTING_TARGET
                    },
                    from = PendingCardOrigin.DRAWING,
                    targets = emptyList(),
                )
            },
        )
        return botContext(botId, state).copy(
            pendingCard = pending,
            activeActionCard = pending?.takeIf { subPhase != GameSubPhase.CHOOSING },
            discardPile = state.discardPile,
            opponentKnowledge = mapOf(
                botId to botCards.indices.associateWith { botCards[it] },
                humanId to knownOfHuman,
            ),
        )
    }

    private fun agree(what: String, verdict: (Long) -> Boolean) {
        val agreed = seeds.count { verdict(it) }
        assertTrue(agreed >= bar, "$what in only $agreed of ${seeds.count()} seeds")
    }

    @Test
    fun aJackGivesATenForAKnownJokerRatherThanForAKnownNine() {
        val joker = testCard(Rank.JOKER, "h-joker")
        val nine = testCard(Rank.NINE, "h-nine")
        agree("the Jack took the Joker, and swapped") { seed ->
            val decision = service(seed).selectActionTargets(
                contextWith(
                    botCards = listOf(testCard(Rank.TEN, "b0"), testCard(Rank.TEN, "b1"), testCard(Rank.TWO, "b2")),
                    humanCards = listOf(joker, nine, testCard(Rank.FIVE, "h2"), testCard(Rank.FIVE, "h3")),
                    knownOfHuman = mapOf(0 to joker, 1 to nine),
                    pending = testCard(Rank.JACK, "jack"),
                    subPhase = GameSubPhase.AWAITING_ACTION,
                ),
            )
            val own = decision.targets.firstOrNull { it.playerId == botId }?.position
            val theirs = decision.targets.firstOrNull { it.playerId == humanId }?.position
            theirs == 0 && own in setOf(0, 1) && decision.shouldSwap != false
        }
    }

    @Test
    fun aJackThatCanOnlyLosePointsIsNotTraded() {
        // Own hand 2, 2, A; a Queen and a 10 known opposite, and one card unread. Every trade
        // the Jack can make takes on points. Playing it and declining the swap is as good as
        // not playing it — either way, nothing changes hands.
        val queen = testCard(Rank.QUEEN, "h-queen")
        val ten = testCard(Rank.TEN, "h-ten")
        agree("the losing Jack was not traded") { seed ->
            val context = contextWith(
                botCards = listOf(testCard(Rank.TWO, "b0"), testCard(Rank.TWO, "b1"), testCard(Rank.ACE, "b2")),
                humanCards = listOf(queen, ten, testCard(Rank.FIVE, "h2")),
                knownOfHuman = mapOf(0 to queen, 1 to ten),
                pending = testCard(Rank.JACK, "jack"),
                subPhase = GameSubPhase.CHOOSING,
            )
            val service = service(seed)
            !service.shouldUseAction(context.pendingCard!!, context) ||
                service.selectActionTargets(context.copy(activeActionCard = context.pendingCard)).shouldSwap == false
        }
    }

    @Test
    fun anUnplayedJackIsTakenOffThePileWhenAJokerIsOnOffer() {
        val joker = testCard(Rank.JOKER, "h-joker")
        agree("the Jack was taken") { seed ->
            // No pair in hand: three 10s would make a declared swap-out and its toss-in the
            // better line, which the first version of this test learned the hard way.
            val botCards = listOf(testCard(Rank.TEN, "b0"), testCard(Rank.NINE, "b1"), testCard(Rank.EIGHT, "b2"))
            val context = contextWith(
                botCards = botCards,
                humanCards = listOf(joker, testCard(Rank.FIVE, "h1"), testCard(Rank.FIVE, "h2")),
                knownOfHuman = mapOf(0 to joker),
                pending = null,
                subPhase = GameSubPhase.IDLE,
                discardTop = testCard(Rank.JACK, "discard-jack"),
            )
            service(seed).decideTurnAction(context).action == TurnAction.TAKE_DISCARD
        }
    }

    @Test
    fun aKingDeclaresItsOwnPairRatherThanARivalsSingleQueen() {
        // Naming a 10 sheds one 10 by declaration and the other by toss-in: twenty points.
        // Naming the rival's Queen costs them ten and the bot nothing.
        val queen = testCard(Rank.QUEEN, "h-queen")
        agree("the King named the pair") { seed ->
            val decision = service(seed).selectActionTargets(
                contextWith(
                    botCards = listOf(testCard(Rank.TEN, "b0"), testCard(Rank.TEN, "b1"), testCard(Rank.THREE, "b2")),
                    humanCards = listOf(queen, testCard(Rank.FIVE, "h1"), testCard(Rank.FIVE, "h2")),
                    knownOfHuman = mapOf(0 to queen),
                    pending = testCard(Rank.KING, "king"),
                    subPhase = GameSubPhase.AWAITING_ACTION,
                ),
            )
            decision.declaredRank == Rank.TEN && decision.targets.firstOrNull()?.playerId == botId
        }
    }

    @Test
    fun aDrawnTwoReplacesAKnownTenAndNeverAKnownJoker() {
        agree("the 2 went in over the 10") { seed ->
            val context = contextWith(
                botCards = listOf(testCard(Rank.JOKER, "b0"), testCard(Rank.TEN, "b1"), testCard(Rank.FOUR, "b2")),
                humanCards = List(3) { testCard(Rank.SIX, "h$it") },
                knownOfHuman = emptyMap(),
                pending = testCard(Rank.TWO, "drawn-2"),
                subPhase = GameSubPhase.CHOOSING,
            )
            service(seed).selectBestSwapPosition(context.pendingCard!!, context) == 1
        }
    }

    @Test
    fun aDrawnTenNeverGoesIntoAHandOfCheaperCards() {
        // A 10 drawn over 2, 3, 4 is either played for its peek or put down; what it is never
        // is swapped in.
        agree("the 10 stayed out of the hand") { seed ->
            val context = contextWith(
                botCards = listOf(testCard(Rank.TWO, "b0"), testCard(Rank.THREE, "b1"), testCard(Rank.FOUR, "b2")),
                humanCards = List(3) { testCard(Rank.SIX, "h$it") },
                knownOfHuman = emptyMap(),
                pending = testCard(Rank.TEN, "drawn-10"),
                subPhase = GameSubPhase.CHOOSING,
            )
            val service = service(seed)
            service.shouldUseAction(context.pendingCard!!, context) ||
                service.selectBestSwapPosition(context.pendingCard!!, context) == null
        }
    }

    @Test
    fun theSearchSpendsItsVisitsWhereTheRewardIs() {
        // The tree itself, not just its answer: the move played is the most visited child,
        // and it is visited most because its mean reward is highest.
        val joker = testCard(Rank.JOKER, "h-joker")
        agree("the best child was also the most visited") { seed ->
            val botCards = listOf(testCard(Rank.TEN, "b0"), testCard(Rank.NINE, "b1"), testCard(Rank.EIGHT, "b2"))
            val context = contextWith(
                botCards = botCards,
                humanCards = listOf(joker, testCard(Rank.FIVE, "h1"), testCard(Rank.FIVE, "h2")),
                knownOfHuman = mapOf(0 to joker),
                pending = null,
                subPhase = GameSubPhase.IDLE,
                discardTop = testCard(Rank.JACK, "discard-jack"),
            )
            val service = service(seed)
            val tree = service.searchTree(service.rootFor(context, pending = null))
            val take = tree.children.values.first { it.move?.type == MctsMoveType.TAKE_DISCARD }
            val draw = tree.children.values.first { it.move?.type == MctsMoveType.DRAW }
            take.visits > draw.visits && take.mean(0) > draw.mean(0)
        }
    }
}
