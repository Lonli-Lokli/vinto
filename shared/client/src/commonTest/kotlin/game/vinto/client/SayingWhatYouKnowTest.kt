package game.vinto.client

import game.vinto.engine.projectView
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.Claim
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.TargetType
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Saying what you know, with no text box anywhere in it.
 *
 * The claim is built by **tapping** — the cards, then the ranks — and finished by the one
 * question a pair leaves. Its third answer is "not sure", which is the reason the control
 * exists: ten turns after setup the pair without its order is what a person actually holds,
 * and a picker that could only say exact ranks would make them guess, after which the
 * coalition plans on a coin toss dressed as a fact.
 */
class SayingWhatYouKnowTest {

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

    private fun finalRound(claims: Map<String, List<Claim>> = emptyMap()): GameState = GameState(
        gameId = "claiming",
        roundNumber = 1,
        turnNumber = 9,
        phase = GamePhase.FINAL,
        subPhase = GameSubPhase.AI_THINKING,
        finalTurnTriggered = true,
        players = listOf(
            seat(me, listOf(Rank.NINE, Rank.FOUR, Rank.SEVEN), claims[me]),
            seat(caller, listOf(Rank.KING, Rank.TWO), claims[caller]),
            seat(mate, listOf(Rank.FIVE, Rank.SIX), claims[mate]),
        ),
        currentPlayerIndex = 1,
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

    private fun viewOf(state: GameState = finalRound()) = projectView(state, me)

    private fun tapOn(table: Table, seatId: String, position: Int): Question {
        val move = table.taps[CardRef(seatId, position)]
        assertIs<Move.Ask>(move, "no tap offered on $seatId's card $position")
        return move.question
    }

    // ------------------------------------------------------------------ what may be tapped

    @Test
    fun anySeatsCardsMayBeSpokenAbout() {
        // The asymmetry this closes: a member who peeked the caller's card had nowhere to put
        // it, while the bots pooled their own sightings privately.
        val table = tableFor(viewOf())

        assertIs<Move.Ask>(table.taps[CardRef(me, 0)], "my own hand")
        assertIs<Move.Ask>(table.taps[CardRef(mate, 0)], "a teammate's hand")
        assertIs<Move.Ask>(table.taps[CardRef(caller, 0)], "what I have seen of the caller")
    }

    @Test
    fun theCallerIsNotOfferedTheirOpponentsCards() {
        val fromTheCallersChair = projectView(finalRound(), caller)
        val table = tableFor(fromTheCallersChair)

        assertTrue(table.taps.isEmpty(), "the caller was offered somebody to brief")
    }

    // ------------------------------------------------------------------ one card

    @Test
    fun oneCardAndOneRankIsAnExactClaim() {
        val view = viewOf()
        val question = tapOn(tableFor(view), mate, 1)
        val picking = tableFor(view, question)

        val send = picking.ranks.first { it.rank == Rank.SIX }.move
        assertIs<Move.Send>(send)
        val payload = (send.action as GameAction.DeclareCards).payload

        assertEquals(me, payload.playerId, "a claim is the speaker's")
        assertEquals(mate, payload.about, "and it is about the card's owner")
        assertEquals(listOf(1), payload.claims.single().positions)
        assertEquals(listOf(Rank.SIX), payload.claims.single().ranks)
    }

    // ------------------------------------------------------------------ two cards

    @Test
    fun twoCardsAndTwoRanksAskTheOnlyQuestionLeft() {
        val view = viewOf()
        var question = tapOn(tableFor(view), me, 0)
        question = tapOn(tableFor(view, question), me, 2)

        val ranks = tableFor(view, question)
        question = (ranks.ranks.first { it.rank == Rank.KING }.move as Move.Ask).question
        val second = tableFor(view, question)
        question = (second.ranks.first { it.rank == Rank.ACE }.move as Move.Ask).question

        val asked = tableFor(view, question)
        assertEquals(Ask.WhichWayRound, asked.prompt)

        // Three answers of equal standing, and the third is the one people usually have.
        val offered = asked.choices.map { it.label }
        assertEquals(2, offered.count { it is Label.ThisWayRound }, "both orderings: $offered")
        assertTrue(Label.NotSureWhichWayRound in offered, "no way to say you are not sure: $offered")
    }

    @Test
    fun notSureSendsOnePairClaimRatherThanAGuess() {
        val view = viewOf()
        val question = Question.Claiming(me, listOf(0, 2), listOf(Rank.KING, Rank.ACE))

        val send = tableFor(view, question)
            .choices
            .first { it.label == Label.NotSureWhichWayRound }
            .move
        assertIs<Move.Send>(send)
        val claim = (send.action as GameAction.DeclareCards).payload.claims.single()

        assertEquals(listOf(0, 2), claim.positions)
        assertEquals(listOf(Rank.KING, Rank.ACE), claim.ranks)
        assertTrue(claim.covering, "the pair is what those two hold between them")
    }

    @Test
    fun anOrderingSendsTwoExactClaimsInOneAction() {
        val view = viewOf()
        val question = Question.Claiming(me, listOf(0, 2), listOf(Rank.KING, Rank.ACE))

        val send = tableFor(view, question)
            .choices
            .first { it.label is Label.ThisWayRound }
            .move
        assertIs<Move.Send>(send)
        val claims = (send.action as GameAction.DeclareCards).payload.claims

        assertEquals(2, claims.size, "an ordering is two exact claims, not a pair")
        assertTrue(claims.all { it.positions.size == 1 })
        assertEquals(setOf(Rank.KING, Rank.ACE), claims.flatMap { it.ranks }.toSet())
    }

    @Test
    fun aThirdCardIsNotOfferedBecauseSixOrderingsIsNotAQuestion() {
        val view = viewOf()
        val question = Question.Claiming(me, listOf(0, 2))
        val table = tableFor(view, question)

        val third = table.taps[CardRef(me, 1)]
        assertTrue(
            third == null || ((third as Move.Ask).question as Question.Claiming).positions.size <= 2,
            "a claim grew past a pair",
        )
    }

    @Test
    fun tappingAPickedCardAgainTakesItBackOut() {
        val view = viewOf()
        val question = Question.Claiming(me, listOf(0, 2))

        val again = tapOn(tableFor(view, question), me, 0) as Question.Claiming
        assertEquals(listOf(2), again.positions)
    }

    // ------------------------------------------------------------------ taking it back

    @Test
    fun aSpeakerMayWithdrawWhatTheySaid() {
        val standing = mapOf(mate to listOf(Claim(me, listOf(0), listOf(Rank.FIVE))))
        val view = projectView(finalRound(standing), me)
        val question = Question.Claiming(mate, listOf(0))

        val withdraw = tableFor(view, question).choices.first { it.label == Label.Withdraw }.move
        assertIs<Move.Send>(withdraw)
        val payload = (withdraw.action as GameAction.DeclareCards).payload

        assertEquals(mate, payload.about)
        assertTrue(payload.claims.isEmpty(), "an empty claim list is how a speaker takes it back")
    }

    @Test
    fun thereIsNothingToWithdrawFromSomebodyElsesClaim() {
        val standing = mapOf(mate to listOf(Claim(mate, listOf(0), listOf(Rank.FIVE))))
        val view = projectView(finalRound(standing), me)
        val question = Question.Claiming(mate, listOf(0))

        assertTrue(
            tableFor(view, question).choices.none { it.label == Label.Withdraw },
            "one seat was offered the withdrawal of another's claim",
        )
    }

    // ------------------------------------------------------------------ what the felt shows

    @Test
    fun aPairIsWornByBothCardsAndADisputeIsMarkedAsOne() {
        val standing = mapOf(
            me to listOf(Claim(me, listOf(0, 2), listOf(Rank.KING, Rank.ACE))),
            mate to listOf(
                Claim(me, listOf(0), listOf(Rank.FIVE)),
                Claim(mate, listOf(0), listOf(Rank.NINE)),
            ),
        )
        val table = tableFor(projectView(finalRound(standing), me))

        // Both halves of the pair wear both candidates, so it reads as one statement.
        assertEquals(table.badges[CardRef(me, 0)], table.badges[CardRef(me, 2)])
        assertTrue(table.badges.getValue(CardRef(me, 0)).contains("K"))
        assertTrue(table.badges.getValue(CardRef(me, 0)).contains("A"))

        // A disagreement is drawn differently from one person being unsure.
        val disputed = table.badges.getValue(CardRef(mate, 0))
        assertTrue(disputed.contains("?"), "a dispute reads like a partial claim: $disputed")
    }

    // ------------------------------------------------------------------ the reveal

    @Test
    fun scoringSettlesEveryClaimIncludingTheCallersOwn() {
        // Nothing is checked when it is said. Everything is checked when the hands turn over,
        // which is what gives table talk a cost and an honest claim its worth.
        val standing = mapOf(
            // Right: p3's first card really is a FIVE.
            mate to listOf(Claim(me, listOf(0), listOf(Rank.FIVE))),
            // Wrong, and the caller's own bluff about their own hand — same terms as anyone's.
            caller to listOf(Claim(caller, listOf(0), listOf(Rank.TWO))),
        )
        val scored = finalRound(standing).copy(phase = GamePhase.SCORING)
        val table = tableFor(projectView(scored, me))

        assertTrue(CardRef(caller, 0) in table.brokenClaims, "a bluff went unchallenged by the reveal")
        assertTrue(CardRef(mate, 0) !in table.brokenClaims, "a true claim was marked wrong")
        assertTrue(table.badges.containsKey(CardRef(caller, 0)), "the claim vanished instead of being settled")
    }

    @Test
    fun aPartialClaimIsTrueIfTheCardIsOneOfItsCandidates() {
        // "A King or a Queen" about a Queen was a useful thing to say, not a miss.
        val standing = mapOf(
            mate to listOf(Claim(me, listOf(1), listOf(Rank.SIX, Rank.KING), covering = false)),
        )
        val scored = finalRound(standing).copy(phase = GamePhase.SCORING)
        val table = tableFor(projectView(scored, me))

        assertTrue(CardRef(mate, 1) !in table.brokenClaims, "a claim that included the right rank was marked wrong")
    }

    @Test
    fun nothingIsSettledWhileTheRoundIsStillRunning() {
        val standing = mapOf(caller to listOf(Claim(caller, listOf(0), listOf(Rank.TWO))))
        val table = tableFor(projectView(finalRound(standing), me))

        assertTrue(table.brokenClaims.isEmpty(), "the app adjudicated a claim mid-round")
    }

    // ------------------------------------------------------------------ the Ace

    @Test
    fun anAceInTheFinalRoundSaysWhatItWillCost() {
        // Every legal target is a teammate — the caller is out of bounds — so an Ace here
        // hands a penalty card to one's own side, possibly to the hand still able to win.
        // The bots have always known; a person was asked the same question with no guidance.
        val aiming = finalRound().copy(
            currentPlayerIndex = 0,
            subPhase = GameSubPhase.SELECTING,
            pendingAction = PendingAction(
                card = card(Rank.ACE, "ace-in-hand"),
                playerId = me,
                actionPhase = ActionPhase.SELECTING_TARGET,
                from = PendingCardOrigin.DRAWING,
                targetType = TargetType.FORCE_DRAW,
                targets = emptyList(),
            ),
        )

        val table = tableFor(projectView(aiming, me))

        assertEquals(Detail.AnAceOnlyHurtsYourOwnSide, table.detail)
        // And every legal target is still on offer: the rule is the player's to break.
        assertTrue(table.seats.isNotEmpty(), "the warning removed the choices instead of framing them")
    }

    // ------------------------------------------------------------------ opting out

    @Test
    fun aPlayerWhoSaysNothingIsNeverBlockedOrNagged() {
        // Somebody who never claims, never proposes and never opens the plan must be able to
        // play the final round exactly as they would without any of it. A channel that has to
        // be answered is not a channel, it is a form.
        val view = viewOf()
        val table = tableFor(view)

        // Talk is offered as taps, never as the prompt or a required choice.
        assertTrue(table.choices.none { it.label == Label.Withdraw }, "a withdrawal was demanded")
        assertTrue(table.prompt !is Ask.WhatDoYouSayThisCardIs, "the table asked for a claim")
        assertTrue(table.prompt !is Ask.WhichWayRound)
        assertTrue(!table.waiting || table.taps.isNotEmpty(), "waiting with nothing to do is fine")
    }

    @Test
    fun myOwnTurnStillWorksWithoutHavingSaidAnything() {
        val myTurn = finalRound().copy(currentPlayerIndex = 0, subPhase = GameSubPhase.IDLE)
        val table = tableFor(projectView(myTurn, me))

        // The ordinary opening of a turn, unchanged by the fact that nobody has spoken.
        assertTrue(
            table.choices.any { it.label == Label.DrawCard },
            "a silent player could not take their turn: ${table.choices.map { it.label }}",
        )
    }
}
