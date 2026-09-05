package game.vinto.client

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
import game.vinto.shapes.TableTalk
import game.vinto.shapes.actorId
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Propose, never command.
 *
 * A proposal is a `GameAction` the proposer **cannot legally send**, addressed to the seat that
 * can. It carries no authority: it is never validated as the proposer's, never reduced, and
 * never applied by anybody but its recipient. Accepting sends the *recipient's own* move,
 * seat-bound like every other.
 *
 * Three reasons this is a rule and not a preference, and none of them is squeamishness:
 *
 *  - it is exactly what the anti-cheat boundary refuses. `ValidatorImpersonationTest`
 *    re-attributes 18,066 corpus actions and accepts none of them; a control that acted for
 *    another seat would be that, with a friendlier name;
 *  - it would be a different game per configuration. Drive two bots and you play three hands;
 *    online against three people you play one. Same screen, incomparable skill;
 *  - a bot that must obey is not a teammate, and the cooperation stops being cooperation.
 *
 * The payoff is that the workflow is identical whether the recipient is a person or a bot,
 * which is what lets a mixed coalition work without a second design.
 */
class ProposeNeverCommandTest {

    private val me = "human-1"
    private val caller = "bot-2"
    private val mate = "bot-3"

    private fun card(rank: Rank, id: String) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun seat(id: String, ranks: List<Rank>) = PlayerState(
        id = id,
        name = id,
        nickname = id,
        isHuman = id == me,
        isBot = id != me,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = emptyList(),
        isVintoCaller = id == caller,
        coalitionWith = if (id == caller) emptyList() else listOf(me, mate),
    )

    private fun finalRound(): GameState = GameState(
        gameId = "proposing",
        roundNumber = 1,
        turnNumber = 9,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = true,
        players = listOf(
            seat(me, listOf(Rank.NINE, Rank.FOUR)),
            seat(caller, listOf(Rank.KING, Rank.TWO)),
            seat(mate, listOf(Rank.FIVE, Rank.SIX)),
        ),
        currentPlayerIndex = 0,
        vintoCallerId = caller,
        coalitionLeaderId = null,
        drawPile = Pile((0..4).map { card(Rank.THREE, "draw-$it") }),
        discardPile = Pile(listOf(card(Rank.EIGHT, "seed"))),
        pendingAction = null,
        activeTossIn = null,
        turnActions = emptyList(),
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.MODERATE,
        rngState = 0,
    )

    /** A move only [me] could make, suggested by a teammate. */
    private fun suggestion(to: String = me) = TableTalk.Proposal(
        by = mate,
        to = to,
        move = GameAction.DrawCard(PlayerIdPayload(to)),
    )

    @Test
    fun acceptingSendsTheRecipientsOwnMove() {
        val table = tableFor(projectView(finalRound(), me), offered = suggestion())

        val accept = table.choices.first { it.label == Label.DoAsSuggested }.move
        assertIs<Move.Send>(accept)
        // The seat on the action is the *recipient's*. Nothing about it says it was somebody
        // else's idea, which is the whole point: it is an ordinary move from an ordinary seat.
        assertEquals(me, accept.action.let { (it as GameAction.DrawCard).payload.playerId })
    }

    @Test
    fun decliningIsSaidRatherThanSwallowed() {
        // A proposal that could only be ignored leaves the proposer watching nothing happen,
        // unable to tell whether it even arrived.
        val table = tableFor(projectView(finalRound(), me), offered = suggestion())

        val decline = table.choices.first { it.label == Label.DeclineSuggestion }.move
        assertIs<Move.Say>(decline)
        val answer = assertIs<TableTalk.Answer>(decline.talk)
        assertEquals(me, answer.by, "a refusal is the refuser's own words")
        assertEquals(mate, answer.to)
        assertEquals(TableTalk.Answer.Says.NO, answer.says)
    }

    @Test
    fun aSuggestionForSomebodyElseOffersThisSeatNothing() {
        // Readable, never actionable: no control anywhere acts for another seat.
        val table = tableFor(projectView(finalRound(), me), offered = suggestion(to = mate))

        assertTrue(
            table.choices.none { it.label == Label.DoAsSuggested },
            "one seat was offered another's move: ${table.choices.map { it.label }}",
        )
    }

    @Test
    fun noControlAnywhereActsForAnotherSeat() {
        // The sweep. Every move the table offers, in every question it can be in, must name
        // the viewer — the same rule `ValidatorImpersonationTest` holds over the corpus, held
        // here over the *controls* so a screen cannot offer what the validator would refuse.
        val view = projectView(finalRound(), me)
        val questions = listOf(
            Question.None,
            Question.WhichSlot,
            Question.CallRank(0),
            Question.Claiming(mate, listOf(0)),
            Question.Claiming(me, listOf(0, 1), listOf(Rank.KING, Rank.ACE)),
        )

        for (question in questions) {
            val table = tableFor(view, question, offered = suggestion())
            val moves = table.choices.map { it.move } + table.taps.values +
                table.ranks.map { it.move } + table.seats.map { it.move }

            for (move in moves) {
                val actor = when (move) {
                    is Move.Send -> move.action.actorId
                    is Move.Say -> move.talk.by
                    is Move.Ask -> null
                    // Neither names a seat: ending a window is not a move for anybody.
                    Move.Done -> null
                }
                assertTrue(
                    actor == null || actor == me,
                    "a control acted as $actor rather than $me, on $question",
                )
            }
        }
    }

    @Test
    fun anAgreedMoveOpensTheTurnWithoutNarrowingIt() {
        // Pre-arming aims a turn, it never closes one. The agreed move leads, because it is
        // what the player came to make — and everything they could otherwise have done is
        // still there, because agreeing to a suggestion is not a commitment.
        val myTurn = finalRound().copy(currentPlayerIndex = 0, subPhase = GameSubPhase.IDLE)
        val view = projectView(myTurn, me)

        val ordinary = tableFor(view)
        val armed = tableFor(view, offered = suggestion())

        assertEquals(Label.DoAsSuggested, armed.choices.first().label, "the agreed move was buried")
        assertTrue(
            ordinary.choices.all { it.label in armed.choices.map { armed -> armed.label } },
            "pre-arming took a move away: ${ordinary.choices.map { it.label }}",
        )
    }
}
