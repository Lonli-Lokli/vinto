package game.vinto.bot

import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.Lane
import game.vinto.shapes.PlanEdit
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.coalitionInTurnOrder
import game.vinto.shapes.edited
import game.vinto.shapes.laneOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the bots put on the board (task 2.5, as adopted): the concentration play, one lane at a
 * time, computed from the shared picture and proposed rather than played. Bots seed empty lanes,
 * never touch a lane a person has set or cleared, and only bother while a person is there.
 */
class BoardProposalsTest {

    private val caller = "bot-2"
    private val me = "human-1"
    private val nina = "bot-3"
    private val don = "bot-4"

    private var counter = 0
    private fun card(rank: Rank) = testCard(rank, "${rank.serialName}-${counter++}")

    private fun seat(id: String, ranks: List<Rank>, claims: List<Claim>? = null): PlayerState =
        testPlayer(id, id, isHuman = id == me, cards = ranks.map(::card))
            .copy(claims = claims, isVintoCaller = id == caller)

    /**
     * The caller is in seat two, so the coalition plays Nina, Don, then the person. Nina holds
     * a Jack to put down and call, a two she could give away and a ten she would like rid of;
     * Don holds a King and a seven; the person has said they hold a Jack and a nine.
     *
     * The Jacks are the point: a plan can only aim a trade with a card the table can see, so
     * a bot proposes a trade by putting a Jack down and calling it, never on a blind draw.
     */
    private fun table(): GameState = testState(
        players = listOf(
            seat(
                me,
                listOf(Rank.JACK, Rank.NINE),
                claims = listOf(Claim(me, listOf(0), listOf(Rank.JACK)), Claim(me, listOf(1), listOf(Rank.NINE))),
            ),
            seat(caller, listOf(Rank.SIX)),
            seat(nina, listOf(Rank.JACK, Rank.TWO, Rank.TEN)),
            seat(don, listOf(Rank.KING, Rank.SEVEN)),
        ),
        phase = GamePhase.FINAL,
        vintoCallerId = caller,
    ).let { it.copy(currentPlayerIndex = it.players.indexOfFirst { p -> p.id == caller }) }

    /**
     * A coalition of nothing but bots still writes its plan down, because the caller reads it.
     *
     * The one round where the person is on the *other* side: they called Vinto, so the three
     * seats against them are all bots. Nothing was seeded at all — the seeding stopped where
     * there was no human *in the coalition* to seed it for — so the caller called, watched three
     * bots take their turns, and never saw a word of what they intended.
     *
     * The plan is public and the caller may read it (design D12): it is built from claims the
     * table has already heard, so there is nothing in it to hide. And it is the whole tension of
     * the round they just started — "will they beat me?" — which is the one question the screen
     * was answering with a blank. Reported from a phone: *"I as vinto do not see bots plan"*.
     */
    @Test
    fun aCoalitionOfNothingButBotsStillWritesItsPlanDownForTheCallerToRead() {
        // The person calls, so the three seats against them are bots.
        val called = table().let { state ->
            state.copy(
                players = state.players.map { it.copy(isVintoCaller = it.id == me) },
                vintoCallerId = me,
                currentPlayerIndex = state.players.indexOfFirst { it.id == me },
            )
        }

        val seeded = seedTheBoard(called, plan = null)
        val coalition = coalitionInTurnOrder(called.players.map { it.id }, me)
        assertEquals(listOf(caller, nina, don), coalition, "the coalition is not the three bots")

        assertTrue(
            coalition.any { seeded.plan.laneOf(it)?.step != null },
            "an all-bot coalition planned nothing, so the caller has nothing to read",
        )
        assertTrue(
            seeded.plan.lanes.all { lane -> lane.step.cardsTouched().none { it == me } },
            "a proposal reached for the caller's cards",
        )
        assertTrue(seeded.said.isNotEmpty(), "the bots agreed a line without saying anything about it")
    }

    /** Whose cards a step names, for the check above. */
    private fun Step?.cardsTouched(): List<String> = when (this) {
        is Step.Swap -> listOf(from.seat, to.seat)
        is Step.PutDown -> listOf(card.seat)
        else -> emptyList()
    }

    @Test
    fun theBotsFillLanesWhileATradeStillLowersTheLowestHandAndThenStop() {
        // The first lane gets the trade that leaves one hand on two. After that no trade can
        // lower the lowest hand further, so the later lanes are left as "your call" — a plan
        // says what helps, not something for every turn.
        val seeded = seedTheBoard(table(), plan = null)

        val order = coalitionInTurnOrder(table().players.map { it.id }, caller)
        assertEquals(listOf(nina, don, me), order)
        val first = assertIs<Step.PutDown>(
            assertNotNull(seeded.plan.laneOf(nina), "no proposal for the first lane").step,
        )
        assertEquals(Rank.JACK, first.guess, "the Jack put down was not called")
        val trade = assertIs<Step.Swap>(first.then, "the called Jack trades nothing")
        assertTrue(trade.from.seat != caller && trade.to.seat != caller, "a proposal reached for the caller's cards")
        assertTrue(trade.from.seat != trade.to.seat, "a swap within one hand")
        assertNull(seeded.plan.laneOf(don)?.step, "a trade was proposed that could not lower the lowest hand")
        assertNull(seeded.plan.laneOf(me)?.step)
        assertEquals(nina, seeded.plan.editedBy, "a bot's proposal was signed by somebody else")
        assertTrue(nina in seeded.plan.agreed && don in seeded.plan.agreed, "the bots did not nod to their own line")
    }

    @Test
    fun aProposalIsAnchoredToWhatTheTableSaid() {
        // The person has said their one card is a two; the only trade that lowers the lowest
        // hand below it is Don's King for it. The step names the person's card by the claim,
        // so it can follow the card if a Jack moves it before the turn comes.
        val lowPerson = testState(
            players = listOf(
                seat(me, listOf(Rank.TWO), claims = listOf(Claim(me, listOf(0), listOf(Rank.TWO)))),
                seat(caller, listOf(Rank.SIX)),
                seat(nina, listOf(Rank.JACK, Rank.NINE)),
                seat(don, listOf(Rank.SEVEN, Rank.KING)),
            ),
            phase = GamePhase.FINAL,
            vintoCallerId = caller,
        ).let { it.copy(currentPlayerIndex = it.players.indexOfFirst { p -> p.id == caller }) }

        val seeded = seedTheBoard(lowPerson, plan = null)
        val called = assertIs<Step.PutDown>(assertNotNull(seeded.plan.laneOf(nina)).step)
        val step = assertIs<Step.Swap>(called.then)
        val persons = listOf(step.from, step.to).single { it.seat == me }
        assertEquals(Claim(me, listOf(0), listOf(Rank.TWO)), persons.anchor, "the step cannot follow its card")
        val dons = listOf(step.from, step.to).single { it.seat == don }
        assertEquals(1, dons.position, "the King is not the card that moves")
        assertNull(dons.anchor, "a card nobody has spoken about got an anchor")
    }

    @Test
    fun theBotsStopTheMomentAPersonHasEditedTheBoard() {
        val state = table()
        val coalition = coalitionInTurnOrder(state.players.map { it.id }, caller)
        val seeded = seedTheBoard(state, plan = null).plan

        // The person clears Don's lane. It stays clear: the board is theirs now.
        val cleared = (
            seeded.edited(
                PlanEdit.ClearLane(don),
                me,
                coalition,
                caller,
            ) as game.vinto.shapes.PlanEditOutcome.Edited
            ).plan
        val again = seedTheBoard(state, cleared)
        assertNull(again.plan.laneOf(don), "the bots refilled a lane the person cleared")
        assertEquals(cleared, again.plan, "the bots edited a board a person had touched")
    }

    @Test
    fun aLockedLaneAndTheTurnInProgressAreLeftAlone() {
        val state = table().let { it.copy(currentPlayerIndex = it.players.indexOfFirst { p -> p.id == nina }) }
        val standing = CoalitionPlan(
            lanes = listOf(Lane(don, Step.TakeTheDiscard, locked = true)),
            agreed = listOf(don),
            editedBy = don,
        )

        val seeded = seedTheBoard(state, standing)

        assertNull(seeded.plan.laneOf(nina), "the turn in progress was given a lane")
        assertEquals(Step.TakeTheDiscard, seeded.plan.laneOf(don)?.step, "a locked lane was replaced")
        assertNotNull(seeded.plan.laneOf(me)?.step, "the one open lane was left empty")
    }

    @Test
    fun nothingIsProposedWithoutAPersonToReadIt() {
        val allBots = table().let { s -> s.copy(players = s.players.map { it.copy(isHuman = false, isBot = true) }) }
        assertTrue(seedTheBoard(allBots, plan = null).plan.isEmpty, "three bots planned on a board nobody reads")
    }

    @Test
    fun nothingIsProposedWhenNoTradeHelps() {
        // One card each, every one a two and every one spoken for: no trade changes the lowest
        // hand, so nothing is worth saying. Spoken for, because an unspoken card is priced at
        // the deck's average in the shared picture, and trading a known two for it *is* a gain.
        val flat = testState(
            players = listOf(
                seat(me, listOf(Rank.TWO), claims = listOf(Claim(me, listOf(0), listOf(Rank.TWO)))),
                seat(caller, listOf(Rank.SIX)),
                seat(nina, listOf(Rank.TWO), claims = listOf(Claim(nina, listOf(0), listOf(Rank.TWO)))),
                seat(don, listOf(Rank.TWO), claims = listOf(Claim(don, listOf(0), listOf(Rank.TWO)))),
            ),
            phase = GamePhase.FINAL,
            vintoCallerId = caller,
        )
        assertTrue(seedTheBoard(flat, plan = null).plan.isEmpty, "a pointless trade was proposed")
    }

    @Test
    fun theBestTradeConcentratesTheLowCards() {
        // Nina's picture of the table above: Don has said nothing, so his two cards are priced at
        // the deck's mean each and his is the lowest hand at ten. The trade that lowers it most is
        // Nina's two into it for one of those — the two is the card that moves.
        val input = assertNotNull(buildCoalitionPlanInput(table(), nina))
        val hands = input.members.associate { it.id to it.cards }
        val best = assertNotNull(bestSwap(hands, input.members.map { it.id }))
        assertEquals(7, best.minAfter)
        val moved = listOf(best.from, best.to).map { hands.getValue(it.seat)[it.position].value }
        assertTrue(0 in moved || 2 in moved, "the trade moved neither the two nor the King: $best")
    }
}
