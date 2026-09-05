package game.vinto.client

import game.vinto.engine.PublicReveal
import game.vinto.engine.projectView
import game.vinto.shapes.Card
import game.vinto.shapes.CardAt
import game.vinto.shapes.Claim
import game.vinto.shapes.CoalitionPlan
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Lane
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.Step
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A plan decays honestly.
 *
 * The distinction the whole thing turns on is between a card **moving** and a belief being
 * **wrong**:
 *
 *  - the card moved, and the table watched it go, so the step follows it and says nothing. A
 *    plan that announced its own bookkeeping would be noise exactly when a coalition is busy;
 *  - the belief was wrong, and that is *news*. It is never repaired and never silently
 *    substituted, because everything built on that claim is now built on nothing — and because
 *    somebody misremembering is the game working rather than a player failing.
 *
 * The comparison lives here, in the client, off the reveals it already receives. The engine
 * never checks a claim against a card: doing so would end the game the coalition is playing,
 * and doing so in state would put it in the hash.
 */
class PlanDecayTest {

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

    private fun seat(id: String, ranks: List<Rank>, claims: List<Claim>? = null) = PlayerState(
        id = id,
        name = id,
        nickname = id,
        isHuman = id == me,
        isBot = id != me,
        cards = ranks.mapIndexed { index, rank -> card(rank, "$id-c$index") },
        knownCardPositions = emptyList(),
        isVintoCaller = id == caller,
        coalitionWith = if (id == caller) emptyList() else listOf(me, mate),
        claims = claims,
    )

    private fun table(
        mineClaims: List<Claim>? = null,
        matesClaims: List<Claim>? = null,
    ): GameState = GameState(
        gameId = "decay",
        roundNumber = 1,
        turnNumber = 9,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = true,
        players = listOf(
            seat(me, listOf(Rank.TWO, Rank.THREE), mineClaims),
            seat(caller, listOf(Rank.FOUR, Rank.FOUR)),
            seat(mate, listOf(Rank.NINE, Rank.KING), matesClaims),
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

    /** A step remembers what it was built on, so it can follow its card. */
    private fun swapPlan() = CoalitionPlan(
        lanes = listOf(
            Lane(
                mate,
                Step.Swap(
                    CardAt(me, 1, Claim(me, listOf(1), listOf(Rank.THREE))),
                    CardAt(mate, 1, Claim(mate, listOf(1), listOf(Rank.KING))),
                ),
            ),
        ),
    )

    @Test
    fun aStepWhoseCardsAreStillThereIsLive() {
        val state = table(
            mineClaims = listOf(Claim(me, listOf(1), listOf(Rank.THREE))),
            matesClaims = listOf(Claim(mate, listOf(1), listOf(Rank.KING))),
        )

        val reading = readPlan(projectView(state, me), swapPlan(), reveals = emptyList())

        assertEquals(listOf(StepHealth.LIVE), reading.health)
        assertFalse(reading.anyBroken)
    }

    @Test
    fun aStepFollowsItsCardInSilenceWhenTheTableWatchedItMove() {
        // The claim travelled with the card — `ActionUtils` carries it through a watched swap,
        // keeping its speaker — so the step goes where the claim went. Nothing was learned, so
        // nothing is said.
        val state = table(
            mineClaims = listOf(Claim(me, listOf(1), listOf(Rank.THREE))),
            // The King that was at mate/1 is now at mate/0, still claimed by the same seat.
            matesClaims = listOf(Claim(mate, listOf(0), listOf(Rank.KING))),
        )

        val reading = readPlan(projectView(state, me), swapPlan(), reveals = emptyList())

        assertEquals(listOf(StepHealth.REANCHORED), reading.health)
        val followed = reading.plan.lanes.single().step as Step.Swap
        assertEquals(
            CardAt(mate, 0, Claim(mate, listOf(1), listOf(Rank.KING))),
            followed.to,
            "the step did not follow its card",
        )
    }

    @Test
    fun aRevealThatContradictsAClaimBreaksTheStepAndIsNeverRepaired() {
        val state = table(
            mineClaims = listOf(Claim(me, listOf(1), listOf(Rank.THREE))),
            matesClaims = listOf(Claim(mate, listOf(1), listOf(Rank.KING))),
        )

        // The card everybody was told was a King turns over as a Seven.
        val reading = readPlan(
            projectView(state, me),
            swapPlan(),
            reveals = listOf(PublicReveal(mate, 1, card(Rank.SEVEN, "the-truth"))),
        )

        assertEquals(listOf(StepHealth.BROKEN), reading.health)
        assertTrue(reading.anyBroken)
        assertEquals(
            swapPlan().lanes.single().step,
            reading.plan.lanes.single().step,
            "a broken step was silently substituted rather than shown broken",
        )
    }

    @Test
    fun aRevealThatAgreesWithAClaimBreaksNothing() {
        val state = table(
            mineClaims = listOf(Claim(me, listOf(1), listOf(Rank.THREE))),
            matesClaims = listOf(Claim(mate, listOf(1), listOf(Rank.KING))),
        )

        val reading = readPlan(
            projectView(state, me),
            swapPlan(),
            reveals = listOf(PublicReveal(mate, 1, card(Rank.KING, "as-promised"))),
        )

        assertEquals(listOf(StepHealth.LIVE), reading.health)
    }

    @Test
    fun aBrokenStepTakesItsDependentsAndLeavesTheRest() {
        // Marking the whole rest of a plan broken would tell a coalition to start again when
        // most of what they agreed still holds.
        val state = table(
            mineClaims = listOf(
                Claim(me, listOf(0), listOf(Rank.TWO)),
                Claim(me, listOf(1), listOf(Rank.THREE)),
            ),
            matesClaims = listOf(
                Claim(mate, listOf(0), listOf(Rank.NINE)),
                Claim(mate, listOf(1), listOf(Rank.KING)),
            ),
        )
        val theirKing = CardAt(mate, 1, Claim(mate, listOf(1), listOf(Rank.KING)))
        val plan = CoalitionPlan(
            lanes = listOf(
                Lane(mate, Step.Swap(CardAt(me, 1, Claim(me, listOf(1), listOf(Rank.THREE))), theirKing)),
                // Depends on the same card the first step moves.
                Lane(me, Step.Swap(theirKing, CardAt(me, 0, Claim(me, listOf(0), listOf(Rank.TWO))))),
                // Touches neither, so it survives.
                Lane(mate, Step.Declare(Rank.NINE)),
            ),
        )

        val reading = readPlan(
            projectView(state, me),
            plan,
            reveals = listOf(PublicReveal(mate, 1, card(Rank.SEVEN, "the-truth"))),
        )

        assertEquals(
            listOf(StepHealth.BROKEN, StepHealth.BROKEN, StepHealth.LIVE),
            reading.health,
            "breakage spread further than the cards it actually touched",
        )
    }

    @Test
    fun aKingAimedAtARankNobodyHoldsAnyMoreIsBroken() {
        val state = table(matesClaims = listOf(Claim(mate, listOf(0), listOf(Rank.NINE))))
        val plan = CoalitionPlan(lanes = listOf(Lane(mate, Step.Declare(Rank.QUEEN))))

        val reading = readPlan(projectView(state, me), plan, reveals = emptyList())

        assertEquals(listOf(StepHealth.BROKEN), reading.health, "a King aimed at nothing read as live")
    }

    // ------------------------------------------------------------------ the rehearsal

    @Test
    fun aPlanIsPlayedBackAsTheMovesItWouldMake() {
        // Watched, not read. Four sentences take a paragraph to write and are hard to read in
        // any language; the same plan is one animation, and two players with no language in
        // common can agree it.
        val state = table(
            mineClaims = listOf(Claim(me, listOf(1), listOf(Rank.THREE))),
            matesClaims = listOf(Claim(mate, listOf(1), listOf(Rank.KING))),
        )

        val frames = rehearse(projectView(state, me), swapPlan())

        assertEquals(1, frames.size, "the plan drew no picture")
        assertTrue(frames.single().scenes.isNotEmpty(), "the swap animated nothing")
    }

    @Test
    fun theGhostTableEndsOnTheHandsThePlanProduces() {
        val state = table(
            mineClaims = listOf(Claim(me, listOf(1), listOf(Rank.THREE))),
            matesClaims = listOf(Claim(mate, listOf(1), listOf(Rank.KING))),
        )
        val view = projectView(state, me)
        val before = view.players.first { it.id == mate }.cards[1]

        val after = rehearse(view, swapPlan()).single().view

        assertEquals(
            before,
            after.players.first { it.id == me }.cards[1],
            "the card the plan moves did not arrive where the plan puts it",
        )
    }

    @Test
    fun aStepNamingACardThatIsNotThereDrawsNothing() {
        // A rehearsal of a broken plan would be a picture of something that cannot happen,
        // which is worse than no picture.
        val state = table()
        val plan = CoalitionPlan(
            lanes = listOf(Lane(mate, Step.Swap(CardAt(me, 9), CardAt(mate, 9)))),
        )

        assertTrue(rehearse(projectView(state, me), plan).isEmpty())
    }

    @Test
    fun theRehearsalNeverTurnsOverACardTheSeatWasNotShown() {
        // It transforms the *view*, so it cannot invent information: a card the seat could not
        // see before the plan cannot be seen after it.
        val state = table(matesClaims = listOf(Claim(mate, listOf(1), listOf(Rank.KING))))
        val view = projectView(state, me)
        val hiddenBefore = view.players.sumOf { seat -> seat.cards.count { it is game.vinto.engine.CardView.Hidden } }

        val frames = rehearse(view, swapPlan())

        frames.forEach { frame ->
            val hiddenAfter = frame.view.players.sumOf { seat ->
                seat.cards.count { it is game.vinto.engine.CardView.Hidden }
            }
            assertEquals(hiddenBefore, hiddenAfter, "a rehearsal turned a card over")
        }
    }
}
