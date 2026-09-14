package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.projectView
import game.vinto.shapes.ALL_RANKS
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.Claim
import game.vinto.shapes.DeclareCardsPayload
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    /** Touch a rank on the rail, and get back the claim being built. */
    private fun name(view: PlayerView, question: Question, rank: Rank): Question {
        val move = tableFor(view, question).ranks.first { it.rank == rank }.move
        assertIs<Move.Ask>(move, "the rail does not offer $rank")
        return move.question
    }

    /** Press "Say it", and get back what it declares. */
    private fun declared(view: PlayerView, question: Question): DeclareCardsPayload {
        val move = tableFor(view, question).choices.first { it.label == Label.SayIt }.move
        assertIs<Move.Send>(move, "nothing to send")
        return (move.action as GameAction.DeclareCards).payload
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
        val payload = declared(view, name(view, question, Rank.SIX))

        assertEquals(me, payload.playerId, "a claim is the speaker's")
        assertEquals(mate, payload.about, "and it is about the card's owner")
        assertEquals(listOf(1), payload.claims.single().positions)
        assertEquals(listOf(Rank.SIX), payload.claims.single().ranks)
        assertTrue(payload.claims.single().covering, "one rank names the card exactly")
    }

    /**
     * The sentence the picker existed to make unsayable.
     *
     * "It is a 7 or an 8" is what a person has for most of the cards they have ever seen, and
     * `Claim` has carried it since the day it was written — one position, two ranks, not
     * covering. The rail sent on the first rank, so the only way to say it was to say
     * something else and hope. The coalition then planned on the guess as though it were read.
     */
    @Test
    fun namingASecondRankSaysTheCardIsOneOfThem() {
        val view = viewOf()
        var question = tapOn(tableFor(view), mate, 1)
        question = name(view, question, Rank.SEVEN)
        val claim = declared(view, name(view, question, Rank.EIGHT)).claims.single()

        assertEquals(listOf(1), claim.positions)
        assertEquals(listOf(Rank.SEVEN, Rank.EIGHT), claim.ranks)
        assertTrue(!claim.covering, "two ranks on one card is *one of*, not both")
    }

    @Test
    fun tappingANamedRankTakesItBackOut() {
        val view = viewOf()
        val question = Question.Claiming(mate, listOf(1), listOf(Rank.SEVEN, Rank.EIGHT))

        val after = tableFor(view, question).ranks.first { it.rank == Rank.SEVEN }.move
        assertIs<Move.Ask>(after)
        assertEquals(listOf(Rank.EIGHT), (after.question as Question.Claiming).ranks)
    }

    @Test
    fun theRanksAlreadyNamedAreMarkedAsSuch() {
        // Fourteen plaques in a grid say nothing about which of them are in the claim, and a
        // toggle whose state cannot be seen is a control that cannot be used.
        val view = viewOf()
        val question = Question.Claiming(mate, listOf(1), listOf(Rank.SEVEN))
        val rail = tableFor(view, question).ranks

        assertEquals(ALL_RANKS.size, rail.size, "the whole rail is offered, always")
        assertEquals(
            listOf(Rank.SEVEN),
            rail.filter { it.picked == true }.map { it.rank },
            "the rail does not show what has been named",
        )
        // Every other plaque says it is a toggle that is *off*, which is a different thing
        // from a plaque that is not a toggle. Both reach the screen reader.
        assertTrue(rail.all { it.picked != null }, "a claim rail with a plaque that is not a toggle")
    }

    @Test
    fun aKingsRailIsNotAToggleAndSaysSo() {
        // The same grid, a different question: each of the King's fourteen is a sentence that
        // sends on touch, so none of them is on or off.
        val view = viewOf()
        val king = tableFor(view, Question.CallRank(0)).ranks

        assertTrue(king.all { it.picked == null }, "the King's rail claims to be a set of toggles")
    }

    @Test
    fun thereIsNothingToSayUntilARankIsNamed() {
        val view = viewOf()
        val empty = tableFor(view, Question.Claiming(mate, listOf(1)))

        assertTrue(
            empty.choices.none { it.label == Label.SayIt },
            "a claim of no ranks was offered as something to say",
        )
        assertTrue(
            tableFor(view, Question.Claiming(mate, listOf(1), listOf(Rank.SEVEN)))
                .choices
                .any { it.label == Label.SayIt },
            "a named rank with no way to send it",
        )
    }

    // ------------------------------------------------------------------ two cards

    @Test
    fun twoCardsAndTwoRanksAskTheOnlyQuestionLeft() {
        val view = viewOf()
        var question = tapOn(tableFor(view), me, 0)
        question = tapOn(tableFor(view, question), me, 2)

        question = name(view, question, Rank.KING)
        question = name(view, question, Rank.ACE)

        val asked = tableFor(view, question)
        assertEquals(Ask.WhichWayRound, asked.prompt)

        // Three answers of equal standing, and the third is the one people usually have.
        val offered = asked.choices.map { it.label }
        assertEquals(2, offered.count { it is Label.ThisWayRound }, "both orderings: $offered")
        assertTrue(Label.NotSureWhichWayRound in offered, "no way to say you are not sure: $offered")

        // And the rail is still live under it: the pair question is a *refinement* of the
        // ranks named, so changing your mind about one of them must not mean starting again.
        assertEquals(ALL_RANKS.size, asked.ranks.size, "the rail went when the pair was named")
        assertEquals(2, asked.ranks.count { it.picked == true }, "the named pair is not marked")
    }

    /**
     * Two cards can be spoken about without the order ever coming up.
     *
     * "Those two are both low" is a pair claim with one rank; "they are among these three" has
     * more ranks than cards. Neither leaves an order to settle, so neither is asked for one —
     * the pair question belongs to the case that has exactly two of each.
     */
    @Test
    fun twoCardsAndOneRankSaysBothWithoutAskingTheOrder() {
        val view = viewOf()
        val question = Question.Claiming(me, listOf(0, 2))
        val claim = declared(view, name(view, question, Rank.KING)).claims.single()

        assertEquals(listOf(0, 2), claim.positions)
        assertEquals(listOf(Rank.KING), claim.ranks)
        assertTrue(!claim.covering, "one rank cannot cover two cards between them")
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

    // ------------------------------------------------------------------ the draft on the felt

    /**
     * What you are about to say, worn by the card you are about to say it about.
     *
     * The rail is a multiple choice and the thing it chooses about is on the felt, so with
     * nothing drawn until "Say it" a member naming three ranks could only check themselves by
     * reading fourteen plaques back. Reported from a phone: the card does not update until
     * afterwards.
     */
    @Test
    fun theCardYouAreNamingWearsWhatYouHaveNamedSoFar() {
        val view = viewOf()
        var question = tapOn(tableFor(view), mate, 1)
        question = name(view, question, Rank.SEVEN)
        question = name(view, question, Rank.EIGHT)

        val badge = tableFor(view, question).badges[CardRef(mate, 1)]
        assertNotNull(badge, "the card being named wears nothing")
        assertEquals("7/8", badge.text)
        assertTrue(badge.draft, "a draft that does not say it is one")
    }

    @Test
    fun aCardPickedWithNoRankYetStillSaysItIsTheOneBeingTalkedAbout() {
        val view = viewOf()
        val question = tapOn(tableFor(view), mate, 1)

        val badge = tableFor(view, question).badges[CardRef(mate, 1)]
        assertNotNull(badge, "the picked card is not marked at all")
        assertTrue(badge.draft)
        assertNull(badge.verdict)
    }

    @Test
    fun aDraftIsWornOnlyByTheCardsTheClaimNames() {
        val view = viewOf()
        val question = Question.Claiming(mate, listOf(0), listOf(Rank.FIVE))

        val badges = tableFor(view, question).badges
        assertEquals(setOf(CardRef(mate, 0)), badges.filterValues { it.draft }.keys)
    }

    /** And once it is said it is a claim like any other, drawn as one. */
    @Test
    fun whatHasBeenSaidIsNoLongerADraft() {
        val standing = mapOf(mate to listOf(Claim(me, listOf(0), listOf(Rank.FIVE))))
        val view = projectView(finalRound(standing), me)

        val badge = tableFor(view).badges[CardRef(mate, 0)]
        assertNotNull(badge)
        assertTrue(!badge.draft, "a claim on the table is still drawn as a draft")
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
        assertTrue(
            payload.claims.single().vacuous,
            "taking it back left something standing: ${payload.claims}",
        )
    }

    /**
     * And it takes back only the card being pointed at.
     *
     * The button sat in a picker scoped to one card and sent the empty claim list, which is
     * how a speaker unsays **everything** about that hand. So correcting one card cost a
     * player every other thing they had told the coalition about that seat, silently — and a
     * per-card take-back was already in the model, as the claim that names every rank.
     */
    @Test
    fun takingOneCardBackLeavesTheRestOfWhatYouSaidStanding() {
        val standing = mapOf(
            mate to listOf(
                Claim(me, listOf(0), listOf(Rank.FIVE)),
                Claim(me, listOf(1), listOf(Rank.SIX)),
            ),
        )
        val view = projectView(finalRound(standing), me)

        val withdraw = tableFor(view, Question.Claiming(mate, listOf(0)))
            .choices
            .first { it.label == Label.Withdraw }
            .move
        assertIs<Move.Send>(withdraw)
        val claims = (withdraw.action as GameAction.DeclareCards).payload.claims

        assertEquals(listOf(0), claims.single().positions, "it reached past the card tapped")
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
        assertTrue(table.badges.getValue(CardRef(me, 0)).text.contains("K"))
        assertTrue(table.badges.getValue(CardRef(me, 0)).text.contains("A"))
        assertTrue(table.badges.getValue(CardRef(me, 0)).paired, "the two halves of a pair are not linked")

        // A disagreement is drawn differently from one person being unsure, and names both.
        val disputed = table.badges.getValue(CardRef(mate, 0))
        assertTrue(disputed.text.contains("?"), "a dispute reads like a partial claim: $disputed")
        assertTrue(disputed.disputed)
        assertEquals(2, disputed.speakers.size, "a dispute without both speakers: $disputed")
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
