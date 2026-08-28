package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.SerializedOpponentKnowledge
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The coalition with a person in it, and the declaration model that makes that fair.
 *
 * The planner may only know what the table has been told: the acting bot's own read cards,
 * teammates' *declared* ranks, and pooled sightings of the caller. A human teammate's unread,
 * undeclared cards are exactly as opaque to the bots as they are to everyone else — that is
 * the rule these tests pin.
 */
class CoalitionHumanMemberTest {

    private var cardCounter = 0

    private fun card(rank: Rank) = testCard(rank, "${rank.serialName}-${cardCounter++}")

    private fun seat(
        id: String,
        isHuman: Boolean,
        ranks: List<Rank>,
        declared: Map<Int, Rank>? = null,
        knownPositions: List<Int>? = null,
    ): PlayerState = testPlayer(
        id,
        id,
        isHuman = isHuman,
        cards = ranks.map(::card),
        knownCardPositions = knownPositions ?: if (isHuman) emptyList() else ranks.indices.toList(),
    ).copy(declaredCards = declared)

    private fun finalRound(
        players: List<PlayerState>,
        callerId: String,
        leaderId: String? = players.firstOrNull { it.isBot && it.id != callerId }?.id,
        subPhase: GameSubPhase = GameSubPhase.IDLE,
    ): GameState {
        val marked = players.map { player ->
            if (player.id == callerId) {
                player.copy(isVintoCaller = true)
            } else {
                player.copy(coalitionWith = players.map { it.id } - player.id)
            }
        }
        return testState(
            players = marked,
            phase = GamePhase.FINAL,
            subPhase = subPhase,
            vintoCallerId = callerId,
            coalitionLeaderId = leaderId,
        )
    }

    // ------------------------------------------------------------ what the plan may know

    @Test
    fun thePlanDoesNotSeeAHumanMembersUnreadCards() {
        // The human teammate holds a Joker and has declared nothing. The plan must hold that
        // card as an unknown with an expected value — not as the -1 it really is — and the
        // Joker must still count as un-seen in the draw distribution.
        val state = finalRound(
            players = listOf(
                seat("caller", isHuman = false, ranks = listOf(Rank.KING)),
                seat("bot-1", isHuman = false, ranks = listOf(Rank.FIVE), declared = mapOf(0 to Rank.FIVE)),
                seat("human-2", isHuman = true, ranks = listOf(Rank.JOKER, Rank.NINE)),
                seat("bot-3", isHuman = false, ranks = listOf(Rank.TWO), declared = mapOf(0 to Rank.TWO)),
            ),
            callerId = "caller",
        )

        val input = buildCoalitionPlanInput(state, "bot-1")!!
        val human = input.members.first { it.id == "human-2" }

        assertTrue(human.cards.none { it.known }, "an undeclared hand leaked into the plan")
        assertTrue(human.cards.all { it.value >= 0 }, "the hidden Joker's value leaked")
        assertEquals(2, input.unseenCounts[Rank.JOKER], "the hidden Joker was counted as seen")
    }

    @Test
    fun aDeclaredClaimEntersThePlanAtFaceValueEvenWhenItIsWrong() {
        // The human declared position 0 a TWO; it is really a NINE. The plan believes the
        // table talk — that is the whole model — and the claim's value is the claimed one.
        val state = finalRound(
            players = listOf(
                seat("caller", isHuman = false, ranks = listOf(Rank.KING)),
                seat("bot-1", isHuman = false, ranks = listOf(Rank.FIVE), declared = mapOf(0 to Rank.FIVE)),
                seat("human-2", isHuman = true, ranks = listOf(Rank.NINE), declared = mapOf(0 to Rank.TWO)),
            ),
            callerId = "caller",
        )

        val input = buildCoalitionPlanInput(state, "bot-1")!!
        val claimed = input.members.first { it.id == "human-2" }.cards[0]

        assertTrue(claimed.known)
        assertEquals(Rank.TWO, claimed.rank)
        assertEquals(2, claimed.value)
    }

    @Test
    fun aBotsPrivateSightingOfATeammateDoesNotEnterThePlan() {
        // bot-1 once peeked the human's card, so its opponentKnowledge holds the truth. The
        // plan still may not use it: teammate knowledge travels as declarations only, so the
        // whole coalition argues from the same public record.
        val human = seat("human-2", isHuman = true, ranks = listOf(Rank.JOKER))
        val state = finalRound(
            players = listOf(
                seat("caller", isHuman = false, ranks = listOf(Rank.KING)),
                seat("bot-1", isHuman = false, ranks = listOf(Rank.FIVE), declared = mapOf(0 to Rank.FIVE))
                    .copy(
                        opponentKnowledge = mapOf(
                            "human-2" to SerializedOpponentKnowledge(mapOf(0 to human.cards[0])),
                        ),
                    ),
                human,
            ),
            callerId = "caller",
        )

        val input = buildCoalitionPlanInput(state, "bot-1")!!
        assertTrue(input.members.first { it.id == "human-2" }.cards.none { it.known })
    }

    // ------------------------------------------------------------ the runner's manners

    @Test
    fun theRunnerHoldsTheLeaderChoiceWhenAHumanIsInTheCoalition() {
        val withHumanMember = finalRound(
            players = listOf(
                seat("caller", isHuman = false, ranks = listOf(Rank.KING)),
                seat("bot-1", isHuman = false, ranks = listOf(Rank.FIVE)),
                seat("human-2", isHuman = true, ranks = listOf(Rank.NINE)),
                seat("bot-3", isHuman = false, ranks = listOf(Rank.TWO)),
            ),
            callerId = "caller",
            leaderId = null,
        )
        assertNull(
            BotRunner(Difficulty.HARD, Random(1)).nextAction(withHumanMember),
            "the bots must wait for the human to choose the leader",
        )

        val botsOnly = finalRound(
            players = listOf(
                seat("human-caller", isHuman = true, ranks = listOf(Rank.KING)),
                seat("bot-1", isHuman = false, ranks = listOf(Rank.FIVE)),
                seat("bot-2", isHuman = false, ranks = listOf(Rank.NINE)),
                seat("bot-3", isHuman = false, ranks = listOf(Rank.TWO)),
            ),
            callerId = "human-caller",
            leaderId = null,
        )
        val action = BotRunner(Difficulty.HARD, Random(1)).nextAction(botsOnly)
        assertTrue(action is GameAction.SetCoalitionLeader, "a bots-only coalition still auto-picks")
    }

    @Test
    fun theRunnerDeclaresEachBotOnceAfterTheLeaderIsSet() {
        var state = finalRound(
            players = listOf(
                seat("human-caller", isHuman = true, ranks = listOf(Rank.KING)),
                seat("bot-1", isHuman = false, ranks = listOf(Rank.FIVE, Rank.NINE)),
                seat("bot-2", isHuman = false, ranks = listOf(Rank.TWO)),
                seat("bot-3", isHuman = false, ranks = listOf(Rank.SIX)),
            ),
            callerId = "human-caller",
        )
        val runner = BotRunner(Difficulty.HARD, Random(7))

        // Declarations come first, one per bot, in seat order.
        for (expected in listOf("bot-1", "bot-2", "bot-3")) {
            val action = runner.nextAction(state)
            assertTrue(action is GameAction.DeclareCards, "expected $expected to declare, got $action")
            assertEquals(expected, action.payload.playerId)
            state = (GameEngineFacade.reduce(state, action))
        }

        // Everybody has spoken; the next action is play, not more talk.
        assertTrue(runner.nextAction(state) !is GameAction.DeclareCards)
    }

    @Test
    fun aHardBotDeclaresExactlyWhatItHolds() {
        val state = finalRound(
            players = listOf(
                seat("human-caller", isHuman = true, ranks = listOf(Rank.KING)),
                seat("bot-1", isHuman = false, ranks = listOf(Rank.FIVE, Rank.JOKER)),
                seat("bot-2", isHuman = false, ranks = listOf(Rank.TWO)),
            ),
            callerId = "human-caller",
        )

        val action = BotRunner(Difficulty.HARD, Random(3)).nextAction(state)
        assertTrue(action is GameAction.DeclareCards)
        assertEquals(mapOf(0 to Rank.FIVE, 1 to Rank.JOKER), action.payload.claims)
    }

    @Test
    fun anEasyBotsDeclarationsCanBeWrongButAreSeedStable() {
        val state = finalRound(
            players = listOf(
                seat("human-caller", isHuman = true, ranks = listOf(Rank.KING)),
                seat(
                    "bot-1", isHuman = false,
                    ranks = listOf(Rank.FIVE, Rank.JOKER, Rank.NINE, Rank.QUEEN, Rank.TWO),
                ),
                seat("bot-2", isHuman = false, ranks = listOf(Rank.TWO)),
            ),
            callerId = "human-caller",
        )
        val truth = mapOf(
            0 to Rank.FIVE, 1 to Rank.JOKER, 2 to Rank.NINE, 3 to Rank.QUEEN, 4 to Rank.TWO,
        )

        fun declaredWithSeed(seed: Int): Map<Int, Rank>? {
            var current = state
            val runner = BotRunner(Difficulty.EASY, Random(seed))
            repeat(4) {
                val action = runner.nextAction(current) ?: return null
                if (action is GameAction.DeclareCards && action.payload.playerId == "bot-1") {
                    return action.payload.claims
                }
                current = GameEngineFacade.reduce(current, action)
            }
            return null
        }

        // Same seed, same claims — wrongness must be reproducible.
        assertEquals(declaredWithSeed(11), declaredWithSeed(11))

        // And with a 0.4 observation accuracy, *some* seed misremembers a five-card hand.
        val anyWrong = (1..20).any { declaredWithSeed(it) != truth }
        assertTrue(anyWrong, "an easy bot never misdeclared across twenty seeds")
    }

    @Test
    fun aCoalitionBotOnlyTossesCardsItHasRead() {
        // bot-1 holds a matching FIVE it has never read (position 1 not in
        // knownCardPositions, not declared). Tossing it would be a guess; the runner must
        // pass instead.
        val base = finalRound(
            players = listOf(
                seat("human-caller", isHuman = false, ranks = listOf(Rank.KING)),
                seat(
                    "bot-1", isHuman = false, ranks = listOf(Rank.TWO, Rank.FIVE),
                    declared = mapOf(0 to Rank.TWO),
                    knownPositions = listOf(0),
                ),
                seat("bot-2", isHuman = false, ranks = listOf(Rank.SIX), declared = mapOf(0 to Rank.SIX)),
            ),
            callerId = "human-caller",
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
        ).copy(
            activeTossIn = game.vinto.shapes.ActiveTossIn(
                ranks = listOf(Rank.FIVE),
                initiatorId = "bot-2",
                originalPlayerIndex = 2,
                participants = emptyList(),
                queuedActions = emptyList(),
                waitingForInput = true,
                playersReadyForNextTurn = listOf("human-caller"),
            ),
        )

        val blind = BotRunner(Difficulty.HARD, Random(5)).nextAction(base)
        assertTrue(blind is GameAction.PlayerTossInFinished, "the bot guessed at an unread card: $blind")

        // The same card, read and declared, is shed.
        val read = base.copy(
            players = base.players.map { player ->
                if (player.id == "bot-1") {
                    player.copy(
                        knownCardPositions = listOf(0, 1),
                        declaredCards = mapOf(0 to Rank.TWO, 1 to Rank.FIVE),
                    )
                } else {
                    player
                }
            },
        )
        val informed = BotRunner(Difficulty.HARD, Random(5)).nextAction(read)
        assertTrue(informed is GameAction.ParticipateInTossIn, "a read matching card was not tossed")
        assertEquals(listOf(1), informed.payload.positions)
    }
}

/** The real engine, behind a name the tests can read. */
private object GameEngineFacade {
    fun reduce(state: GameState, action: GameAction): GameState =
        when (val result = game.vinto.engine.GameEngine.reduce(state, action)) {
            is game.vinto.engine.ReduceResult.Success -> result.state
            is game.vinto.engine.ReduceResult.Failure -> error("engine refused ${action.type}: ${result.reason}")
        }
}
