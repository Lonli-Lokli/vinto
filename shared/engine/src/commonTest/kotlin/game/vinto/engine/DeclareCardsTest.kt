package game.vinto.engine

import game.vinto.shapes.ActionTarget
import game.vinto.shapes.Claim
import game.vinto.shapes.DeclareCardsPayload
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.believedAt
import game.vinto.shapes.hashGameState
import game.vinto.shapes.standingClaims
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DECLARE_CARDS — the coalition's whole voice.
 *
 * A seat says what it believes about somebody's cards: its own, a teammate's, or the caller's.
 * Claims are public, optional, **partial**, attributed to whoever spoke them, and never checked
 * against the real cards when they are made — being wrong is a memory problem, and the reveal
 * settles it.
 *
 * Two properties the shape exists for, and neither is expressible as a rank per position: a
 * claim can be about a card its speaker does not own, and a claim can admit it has lost the
 * order of a pair. The rest of this suite is what follows from those.
 */
class DeclareCardsTest {

    private fun say(speaker: String, about: String, vararg claims: Claim) =
        GameAction.DeclareCards(DeclareCardsPayload(speaker, about, claims.toList()))

    private fun exact(position: Int, rank: Rank) = Claim("", listOf(position), listOf(rank))

    private fun pair(a: Int, b: Int, first: Rank, second: Rank) =
        Claim("", listOf(a, b), listOf(first, second))

    private fun oneOf(position: Int, vararg ranks: Rank) =
        Claim("", listOf(position), ranks.toList(), covering = false)

    private fun finalRound(
        players: List<PlayerState>,
        subPhase: GameSubPhase = GameSubPhase.IDLE,
        currentPlayerIndex: Int = 1,
    ) = testState(
        phase = GamePhase.FINAL,
        subPhase = subPhase,
        currentPlayerIndex = currentPlayerIndex,
        finalTurnTriggered = true,
        vintoCallerId = "p1",
        players = players,
    )

    private fun caller() = testPlayer(
        "p1",
        "Player 1",
        isHuman = true,
        cards = listOf(testCard(Rank.KING, "p1c1"), testCard(Rank.TWO, "p1c2")),
    ).copy(isVintoCaller = true)

    private fun member(id: String, vararg ranks: Rank) = testPlayer(
        id,
        "Player $id",
        isHuman = false,
        cards = ranks.mapIndexed { i, rank -> testCard(rank, "${id}c$i") },
    )

    private fun table() = listOf(
        caller(),
        member("p2", Rank.FIVE, Rank.NINE),
        member("p3", Rank.SIX, Rank.THREE),
    )

    // ------------------------------------------------------------------ who may say what

    @Test
    fun aMemberMayReportWhatTheyHaveSeenOfTheCallersHand() {
        // The asymmetry this closes: the planner pooled every bot's private sighting of the
        // caller, so a person who had peeked the caller's card had nowhere to put it.
        val state = finalRound(table())
        val next = unsafeReduce(state, say("p2", "p1", exact(0, Rank.KING)))

        val claim = next.players.first { it.id == "p1" }.claims!!.single()
        assertEquals("p2", claim.by, "the claim is the speaker's, not the card owner's")
        assertEquals(listOf(Rank.KING), claim.ranks)
        assertNull(next.players.first { it.id == "p2" }.claims, "and it is filed against the card")
    }

    @Test
    fun aMemberMayReportATeammatesCard() {
        val state = finalRound(table())
        val next = unsafeReduce(state, say("p2", "p3", exact(1, Rank.THREE)))

        assertEquals("p2", next.players.first { it.id == "p3" }.claims!!.single().by)
    }

    @Test
    fun theCallerMaySpeakOnlyOfTheirOwnHand() {
        val state = finalRound(table())

        assertTrue(rejects(state, say("p1", "p2", exact(0, Rank.FIVE))), "the caller briefed the coalition")
        assertFalse(rejects(state, say("p1", "p1", exact(0, Rank.KING))), "a bluff about your own hand is fair")
    }

    @Test
    fun declarationsAreOnlyLegalInTheFinalRound() {
        val state = testState(players = table())
        assertTrue(rejects(state, say("p2", "p2", exact(0, Rank.FIVE))))
    }

    @Test
    fun anOutOfRangePositionIsRefused() {
        val state = finalRound(table())
        assertTrue(rejects(state, say("p2", "p2", exact(7, Rank.FIVE))))
    }

    @Test
    fun aClaimWithNoRanksSaysNothingAndIsRefused() {
        val state = finalRound(table())
        assertTrue(rejects(state, say("p2", "p2", Claim("", listOf(0), emptyList()))))
    }

    // ------------------------------------------------------------------ what may be said

    @Test
    fun aWrongClaimIsAcceptedWithoutBeingCheckedAgainstTheCard() {
        // p2's first card is a FIVE. Saying it is a Joker is a memory problem, not a rules one.
        val state = finalRound(table())
        val next = unsafeReduce(state, say("p2", "p2", exact(0, Rank.JOKER)))

        assertEquals(listOf(Rank.JOKER), next.players[1].claims!!.single().ranks)
        assertEquals(Rank.FIVE, next.players[1].cards[0].rank, "the real card is untouched")
    }

    @Test
    fun aPairMayBeClaimedWithoutItsOrder() {
        // The case an exact claim cannot express, and the commonest thing a person actually
        // holds ten turns after setup: the two ranks, and no idea which way round.
        val state = finalRound(table())
        val next = unsafeReduce(state, say("p2", "p2", pair(0, 1, Rank.KING, Rank.ACE)))

        val p2 = next.players.first { it.id == "p2" }
        for (position in 0..1) {
            val believed = believedAt(p2, position)
            assertEquals(setOf(Rank.KING, Rank.ACE), believed.candidates)
            assertFalse(believed.rankKnown, "an unassigned pair must not name a rank")
        }
    }

    @Test
    fun aCoveringClaimNeedsOneRankPerPosition() {
        val state = finalRound(table())
        assertTrue(
            rejects(state, say("p2", "p2", Claim("", listOf(0, 1), listOf(Rank.KING)))),
            "two positions and one rank is not a statement about either",
        )
    }

    @Test
    fun anUnassignedPairIsKnownInValueWhereItsCandidatesAgree() {
        // A Jack and a Queen are both worth ten, so the coalition can plan around the card
        // even though it cannot declare a rank from it.
        val state = finalRound(table())
        val next = unsafeReduce(state, say("p2", "p2", pair(0, 1, Rank.JACK, Rank.QUEEN)))

        val believed = believedAt(next.players.first { it.id == "p2" }, 0)
        assertFalse(believed.rankKnown)
        assertTrue(believed.valueKnown, "both candidates are worth ten")
        assertEquals(10, believed.value)
    }

    // ------------------------------------------------------------------ combining

    @Test
    fun consistentClaimsNarrowTheCard() {
        // The case worth designing for: two partial memories pooled make one better than
        // either. "An action card" and "a King or a Queen" leave King and Queen.
        var state = finalRound(table())
        state = unsafeReduce(state, say("p2", "p3", oneOf(0, Rank.JACK, Rank.QUEEN, Rank.KING)))
        state = unsafeReduce(state, say("p3", "p3", oneOf(0, Rank.KING, Rank.QUEEN)))

        val believed = believedAt(state.players.first { it.id == "p3" }, 0)
        assertEquals(setOf(Rank.KING, Rank.QUEEN), believed.candidates)
        assertFalse(believed.disputed, "agreement is not a disagreement")
    }

    @Test
    fun twoPartialClaimsCanMakeAnExactOne() {
        var state = finalRound(table())
        state = unsafeReduce(state, say("p2", "p3", oneOf(0, Rank.TWO, Rank.THREE)))
        state = unsafeReduce(state, say("p3", "p3", oneOf(0, Rank.THREE, Rank.NINE)))

        val believed = believedAt(state.players.first { it.id == "p3" }, 0)
        assertEquals(setOf(Rank.THREE), believed.candidates)
        assertTrue(believed.rankKnown, "pooling two partial claims should settle the card")
    }

    @Test
    fun inconsistentClaimsAreFlaggedAndBothAreKept() {
        var state = finalRound(table())
        state = unsafeReduce(state, say("p2", "p3", exact(0, Rank.KING)))
        state = unsafeReduce(state, say("p3", "p3", exact(0, Rank.SEVEN)))

        val believed = believedAt(state.players.first { it.id == "p3" }, 0)
        assertTrue(believed.disputed, "two seats disagreed and nobody was told")
        assertEquals(setOf(Rank.KING, Rank.SEVEN), believed.candidates)
        assertFalse(believed.rankKnown, "a disputed card cannot name a rank")
        assertEquals(listOf("p2", "p3"), believed.sources.map { it.by }, "and both speakers stand")
    }

    @Test
    fun correctingOneHalfOfYourOwnPairIsNotADisputeEither() {
        // The case exact-position matching got wrong, and a player reaches it easily: say
        // "these two are a King and an Ace, I forget which", then peek one and say what it is.
        // Under the old rule both claims stood, intersected to nothing, and the card was marked
        // disputed — a seat disagreeing with itself.
        var state = finalRound(table())
        state = unsafeReduce(state, say("p2", "p2", pair(0, 1, Rank.KING, Rank.ACE)))
        state = unsafeReduce(state, say("p2", "p2", exact(0, Rank.SEVEN)))

        val believed = believedAt(state.players.first { it.id == "p2" }, 0)
        assertFalse(believed.disputed, "a seat was made to disagree with itself")
        assertEquals(setOf(Rank.SEVEN), believed.candidates)
    }

    @Test
    fun aSeatCorrectingItselfIsNotADispute() {
        var state = finalRound(table())
        state = unsafeReduce(state, say("p2", "p2", exact(0, Rank.KING)))
        state = unsafeReduce(state, say("p2", "p2", exact(0, Rank.SEVEN)))

        val believed = believedAt(state.players.first { it.id == "p2" }, 0)
        assertFalse(believed.disputed, "changing your own mind is not disagreeing with yourself")
        assertEquals(setOf(Rank.SEVEN), believed.candidates)
        assertEquals(1, standingClaims(state.players.first { it.id == "p2" }).size)
    }

    @Test
    fun aSeatMayWithdrawWhatItSaidAndOnlyThat() {
        var state = finalRound(table())
        state = unsafeReduce(state, say("p2", "p3", exact(0, Rank.KING)))
        state = unsafeReduce(state, say("p3", "p3", exact(1, Rank.THREE)))
        state = unsafeReduce(state, say("p2", "p3"))

        val p3 = state.players.first { it.id == "p3" }
        assertEquals(listOf("p3"), standingClaims(p3).map { it.by }, "a withdrawal took another seat's claim")
        assertEquals(setOf(Rank.KING, Rank.SEVEN, Rank.ACE).size, 3) // keeps the import honest
    }

    // ------------------------------------------------------------------ following the card

    @Test
    fun aSwapCarriesClaimsWithTheCardsAndKeepsTheirSpeakers() {
        var state = finalRound(table())
        state = unsafeReduce(state, say("p2", "p2", exact(0, Rank.FIVE)))
        state = unsafeReduce(state, say("p3", "p3", exact(0, Rank.SIX)))

        val swapped = withSwappedCards(state, "p2", 0, "p3", 0)

        val p2 = swapped.players.first { it.id == "p2" }
        val p3 = swapped.players.first { it.id == "p3" }
        assertEquals(Rank.SIX, believedAt(p2, 0).candidates.single(), "the claim did not travel")
        assertEquals("p3", believedAt(p2, 0).sources.single().by, "and it lost whose it was")
        assertEquals(Rank.FIVE, believedAt(p3, 0).candidates.single())
        assertEquals("p2", believedAt(p3, 0).sources.single().by)
    }

    @Test
    fun aPairClaimIsDroppedWhenEitherOfItsCardsMoves() {
        // Half of "these two are a King and an Ace" is not a statement anybody made.
        var state = finalRound(table())
        state = unsafeReduce(state, say("p2", "p2", pair(0, 1, Rank.KING, Rank.ACE)))

        val swapped = withSwappedCards(state, "p2", 0, "p3", 0)

        assertNull(swapped.players.first { it.id == "p2" }.claims, "a broken pair was left standing")
    }

    // ------------------------------------------------------------------ the frozen corpus

    @Test
    fun aHandNobodyHasSpokenAboutMaterialisesNothing() {
        // The whole reason the field is `@EncodeDefault(NEVER)` and normalised back to null:
        // every state in every parity recording must serialise exactly as it did before this
        // feature existed.
        val state = finalRound(table())
        assertTrue(state.players.all { it.claims == null })

        var spoken = unsafeReduce(state, say("p2", "p2", exact(0, Rank.FIVE)))
        spoken = unsafeReduce(spoken, say("p2", "p2"))

        assertNull(
            spoken.players.first { it.id == "p2" }.claims,
            "an emptied list must go back to null or the hash moves",
        )
        assertEquals(hashGameState(state), hashGameState(spoken))
    }

    @Test
    fun aGameWithDeclarationsReplaysToTheSameHashes() {
        val start = finalRound(table())
        val actions = listOf(
            say("p2", "p2", exact(0, Rank.FIVE)),
            say("p3", "p3", exact(0, Rank.QUEEN)),
            say("p2", "p1", pair(0, 1, Rank.KING, Rank.TWO)),
            say("p2", "p2", exact(0, Rank.SIX)),
        )

        val once = actions.fold(start) { s, a -> unsafeReduce(s, a) }
        val twice = actions.fold(start) { s, a -> unsafeReduce(s, a) }

        assertEquals(hashGameState(once), hashGameState(twice))
        assertEquals(setOf(Rank.SIX), believedAt(once.players.first { it.id == "p2" }, 0).candidates)
    }

    /** A Jack swap, run through the engine so the claim rules are the real ones. */
    private fun withSwappedCards(
        state: game.vinto.shapes.GameState,
        a: String,
        positionA: Int,
        b: String,
        positionB: Int,
    ): game.vinto.shapes.GameState {
        val jack = testCard(Rank.JACK, "jack-for-swap")
        var s = state.copy(
            subPhase = GameSubPhase.SELECTING,
            pendingAction = pending(jack, a).copy(
                targets = listOf(ActionTarget(a, positionA), ActionTarget(b, positionB)),
            ),
        )
        s = unsafeReduce(s, GameAction.ExecuteJackSwap(PlayerIdPayload(a)))
        return s
    }
}
