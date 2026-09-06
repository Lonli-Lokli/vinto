package game.vinto.bot

import game.vinto.shapes.Claim
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.SerializedOpponentKnowledge
import game.vinto.shapes.TableTalk
import game.vinto.shapes.actorId
import game.vinto.shapes.believedAt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
    ).copy(claims = declared?.map { Claim(id, listOf(it.key), listOf(it.value)) })

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

        assertTrue(human.cards.none { it.rankKnown }, "an undeclared hand leaked into the plan")
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

        assertTrue(claimed.rankKnown)
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
        assertTrue(input.members.first { it.id == "human-2" }.cards.none { it.rankKnown })
    }

    // ------------------------------------------------------------ the runner's manners

    @Test
    fun theRunnerNeverWaitsOnACoalitionNomination() {
        // This used to assert the opposite: with a person in the coalition the runner returned
        // null for *everything*, holding all bot play until a `SET_COALITION_LEADER` arrived
        // from the client. The nomination is gone — it decided nothing, since the round is
        // scored against the lowest coalition hand whoever holds it — so the only thing that
        // behaviour can still do is stall a final round.
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
        assertNotNull(
            BotRunner(Difficulty.HARD, Random(1)).nextAction(withHumanMember),
            "bot play was held waiting for a nomination nobody makes any more",
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
        assertNotNull(action, "an all-bot coalition plays its final round")
        assertTrue(
            action !is GameAction.SetCoalitionLeader,
            "nobody proposes a move the doors refuse",
        )
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
    fun aBotCallerSaysNothing() {
        // The caller's register can only be information or a bluff, and a bot has no model of
        // when to bluff (design D12). Its hand is the lowest here, so it is exactly the seat
        // that would otherwise say "mine is low".
        val state = finalRound(
            players = listOf(
                seat("bot-caller", isHuman = false, ranks = listOf(Rank.JOKER)),
                seat("human-1", isHuman = true, ranks = listOf(Rank.NINE)),
                seat("bot-2", isHuman = false, ranks = listOf(Rank.KING, Rank.TWO)),
                seat("bot-3", isHuman = false, ranks = listOf(Rank.FIVE)),
            ),
            callerId = "bot-caller",
        ).copy(turnNumber = 3)

        val runner = BotRunner(Difficulty.HARD, Random(1))
        val said = generateSequence { runner.nextTalk(state) }.take(6).toList()

        assertTrue(said.isNotEmpty(), "the coalition's bots had nothing to say at all")
        assertTrue(said.none { it.by == "bot-caller" }, "the caller spoke: $said")
    }

    /**
     * A runner whose bots remember exactly what a test says they do.
     *
     * The real memory re-reads a bot's own known cards every time it thinks, so its grade
     * cannot be pinned by turn number alone; these tests are about what a bot *does* with a
     * grade, and the grade is the input.
     */
    private fun runnerRemembering(graded: Map<Int, Pair<Rank, Double>>): BotRunner = BotRunner(
        Difficulty.HARD,
        Random(3),
        serviceFactory = { difficulty, random ->
            val real = BotDecisionServiceFactory.create(difficulty, random)
            object : BotDecisionService by real {
                override fun gradedOwnCards(context: BotDecisionContext) = graded
            }
        },
    )

    @Test
    fun aContradictedBotStandsWhereItsMemoryHoldsAndLetsGoWhereItHasDecayed() {
        // A bot that still holds the card above the trusted line stands by its King, and the
        // dispute stays on the table for the reveal to settle. One whose memory of it has
        // faded takes the claim back rather than defend a memory it no longer trusts — by
        // saying the card could be any rank, which replaces its word and disputes nothing.
        fun disputed(turn: Int): GameState {
            val bot = seat("bot-1", isHuman = false, ranks = listOf(Rank.KING, Rank.TWO)).let { player ->
                player.copy(
                    claims = listOf(
                        Claim("bot-1", listOf(0), listOf(Rank.KING)),
                        Claim("bot-1", listOf(1), listOf(Rank.TWO)),
                        Claim("human-1", listOf(0), listOf(Rank.SEVEN)),
                    ),
                )
            }
            return finalRound(
                players = listOf(
                    seat("human-caller", isHuman = true, ranks = listOf(Rank.KING)),
                    seat("human-1", isHuman = true, ranks = listOf(Rank.NINE)),
                    bot,
                ),
                callerId = "human-caller",
            ).copy(turnNumber = turn)
        }

        // Standing is saying the same thing again: the King is repeated, not withdrawn, and the
        // dispute stays for the reveal. Repeating it moves the bot's word after the human's, so
        // the exchange is closed and the bot is not asked again.
        val sure = runnerRemembering(mapOf(0 to (Rank.KING to 0.9), 1 to (Rank.TWO to 0.9)))
        val stands = assertIs<GameAction.DeclareCards>(sure.nextAction(disputed(turn = 5)))
        assertEquals("bot-1", stands.payload.playerId)
        assertEquals(listOf(Claim("bot-1", listOf(0), listOf(Rank.KING))), stands.payload.claims)
        val stood = GameEngineFacade.reduce(disputed(turn = 5), stands)
        assertTrue(believedAt(stood.players.first { it.id == "bot-1" }, 0).disputed, "standing ended the dispute")
        val onceMore = sure.nextAction(stood)
        assertTrue(
            onceMore !is GameAction.DeclareCards || onceMore.payload.playerId != "bot-1",
            "the bot answered the same contradiction twice: $onceMore",
        )

        val letGo = runnerRemembering(mapOf(0 to (Rank.KING to 0.3), 1 to (Rank.TWO to 0.9)))
            .nextAction(disputed(turn = 5))
        val takenBack = assertIs<GameAction.DeclareCards>(letGo, "a faded card was defended")
        assertEquals("bot-1", takenBack.payload.playerId)
        assertEquals("bot-1", takenBack.payload.about)
        val claim = takenBack.payload.claims.single()
        assertEquals(listOf(0), claim.positions, "the wrong card was let go")
        assertTrue(claim.vacuous, "letting go said something: ${claim.ranks}")

        // And once let go, the dispute is over: the human's word stands alone and the bot is
        // not asked again — which is what makes this an answer rather than a loop.
        val after = GameEngineFacade.reduce(disputed(turn = 5), takenBack)
        val believed = believedAt(after.players.first { it.id == "bot-1" }, 0)
        assertEquals(setOf(Rank.SEVEN), believed.candidates)
        assertTrue(!believed.disputed)
        val again = runnerRemembering(mapOf(0 to (Rank.KING to 0.3), 1 to (Rank.TWO to 0.9))).nextAction(after)
        assertTrue(
            again !is GameAction.DeclareCards || again.payload.playerId != "bot-1",
            "the bot answered the same contradiction twice: $again",
        )
    }

    @Test
    fun aDecayedMemoryDeclaresLessRatherThanWrongly() {
        // Five cards read, and a memory that has faded unevenly: what the bot says shrinks to
        // what it still holds — an exact claim where it is sure, one pair where it half
        // remembers two, and nothing for the rest. It never reaches for the engine's record of
        // what it once read to fill the gaps; that fallback made the bot with the worst memory
        // the one whose claims were always right.
        val hand = listOf(Rank.FIVE, Rank.JOKER, Rank.NINE, Rank.QUEEN, Rank.TWO)
        val state = finalRound(
            players = listOf(
                seat("human-caller", isHuman = true, ranks = listOf(Rank.KING)),
                seat("bot-1", isHuman = false, ranks = hand),
                seat("bot-2", isHuman = false, ranks = listOf(Rank.TWO)),
            ),
            callerId = "human-caller",
        )

        val faded = runnerRemembering(
            mapOf(
                0 to (Rank.FIVE to 0.9),
                1 to (Rank.JOKER to 0.3),
                2 to (Rank.NINE to 0.3),
                3 to (Rank.QUEEN to 0.1),
            ),
        ).nextAction(state)
        val said = assertIs<GameAction.DeclareCards>(faded)
        assertEquals("bot-1", said.payload.playerId)
        assertEquals(
            listOf(listOf(0), listOf(1, 2)),
            said.payload.claims.map { it.positions },
            "the bot spoke for cards it no longer holds: ${said.payload.claims}",
        )
        assertTrue(said.payload.claims.last().covering, "two half-remembered cards were not said as a pair")

        // Memory gone entirely: the bot still speaks for its hand, because whether it owes a
        // declaration is a fact about the state — but what it says is that every card it read
        // could be anything, which belief reads past. Nothing is claimed, and nothing is read
        // off the engine's record.
        val gone = assertIs<GameAction.DeclareCards>(runnerRemembering(emptyMap()).nextAction(state))
        assertEquals("bot-1", gone.payload.playerId)
        assertTrue(gone.payload.claims.all { it.vacuous }, "an empty memory claimed a rank: ${gone.payload.claims}")
        val spoken = GameEngineFacade.reduce(state, gone).players.first { it.id == "bot-1" }
        assertTrue(hand.indices.all { believedAt(spoken, it).sources.isEmpty() }, "a vacuous claim was believed")
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
        assertEquals(
            listOf(listOf(Rank.FIVE), listOf(Rank.JOKER)),
            action.payload.claims.sortedBy { it.positions.first() }.map { it.ranks },
        )
    }

    @Test
    fun anEasyBotsDeclarationsCanBeWrongButAreSeedStable() {
        val state = finalRound(
            players = listOf(
                seat("human-caller", isHuman = true, ranks = listOf(Rank.KING)),
                seat(
                    "bot-1",
                    isHuman = false,
                    ranks = listOf(Rank.FIVE, Rank.JOKER, Rank.NINE, Rank.QUEEN, Rank.TWO),
                ),
                seat("bot-2", isHuman = false, ranks = listOf(Rank.TWO)),
            ),
            callerId = "human-caller",
        )
        val truth = mapOf(
            0 to Rank.FIVE,
            1 to Rank.JOKER,
            2 to Rank.NINE,
            3 to Rank.QUEEN,
            4 to Rank.TWO,
        )

        fun declaredWithSeed(seed: Int): List<Claim>? {
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
                    "bot-1",
                    isHuman = false,
                    ranks = listOf(Rank.TWO, Rank.FIVE),
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
                        claims = listOf(
                            Claim(player.id, listOf(0), listOf(Rank.TWO)),
                            Claim(player.id, listOf(1), listOf(Rank.FIVE)),
                        ),
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

    // ------------------------------------------------------------ answering a suggestion

    @Test
    fun aBotWeighsASuggestionWithItsOwnPlannerAndSaysWhichWay() {
        // Propose, never command. A bot runs the move through its own planner and answers —
        // and either way, the move it makes when it agrees is *its own*, not the proposer's.
        val state = finalRound(
            players = listOf(
                seat("human-1", isHuman = true, ranks = listOf(Rank.KING)),
                seat("bot-1", isHuman = false, ranks = listOf(Rank.FIVE, Rank.NINE)),
                seat("bot-2", isHuman = false, ranks = listOf(Rank.TWO)),
                seat("bot-3", isHuman = false, ranks = listOf(Rank.SIX)),
            ),
            callerId = "human-1",
        )
        val runner = BotRunner(Difficulty.HARD, Random(7))

        val (move, answer) = runner.answerTo(
            state,
            TableTalk.Proposal("human-1", "bot-1", GameAction.DrawCard(PlayerIdPayload("bot-1"))),
        )

        assertEquals("bot-1", answer.by, "the answer is the answerer's own words")
        assertEquals("human-1", (answer as TableTalk.Answer).to)
        if (move != null) {
            assertEquals("bot-1", move.actorId, "an accepted suggestion is the accepter's move")
            assertEquals(TableTalk.Answer.Says.YES, answer.says)
        } else {
            assertTrue(answer.says != TableTalk.Answer.Says.YES, "declined but said yes")
        }
    }

    @Test
    fun aBotWillNotBeUsedToDriveAThirdSeat() {
        // `Proposal(to = B, move = DrawCard(C))` would have B agree and the room play C's move
        // — one seat driving another through a third's consent. The room does not seat-check
        // its own bots, so nothing downstream would have caught it.
        val state = finalRound(
            players = listOf(
                seat("human-1", isHuman = true, ranks = listOf(Rank.KING)),
                seat("bot-1", isHuman = false, ranks = listOf(Rank.FIVE)),
                seat("bot-2", isHuman = false, ranks = listOf(Rank.TWO)),
                seat("bot-3", isHuman = false, ranks = listOf(Rank.SIX)),
            ),
            callerId = "human-1",
        )

        val (move, _) = BotRunner(Difficulty.HARD, Random(7)).answerTo(
            state,
            TableTalk.Proposal("human-1", "bot-1", GameAction.DrawCard(PlayerIdPayload("bot-2"))),
        )

        assertNull(move, "a bot agreed to make somebody else's move")
    }

    @Test
    fun aBotRefusesASuggestionTheRulesWouldRefuseAnyway() {
        val state = finalRound(
            players = listOf(
                seat("human-1", isHuman = true, ranks = listOf(Rank.KING)),
                seat("bot-1", isHuman = false, ranks = listOf(Rank.FIVE)),
                seat("bot-2", isHuman = false, ranks = listOf(Rank.TWO)),
                seat("bot-3", isHuman = false, ranks = listOf(Rank.SIX)),
            ),
            callerId = "human-1",
        )

        // Not bot-1's turn, so the move is illegal for it whoever suggested it.
        val (move, answer) = BotRunner(Difficulty.HARD, Random(7)).answerTo(
            state,
            TableTalk.Proposal("human-1", "bot-3", GameAction.DrawCard(PlayerIdPayload("bot-3"))),
        )

        assertNull(move, "a bot agreed to a move the validator would refuse")
        assertEquals(TableTalk.Answer.Says.THAT_LEAVES_US_WORSE, (answer as TableTalk.Answer).says)
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
