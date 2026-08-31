package game.vinto.engine

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.LeaderIdPayload
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SwapHandWithDeckPayload
import game.vinto.shapes.VintoJson
import game.vinto.shapes.hasAction
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Table A of `docs/game-engine/VISIBILITY.md`, executable.
 *
 * The specification says, for every action the game has, who may see the card the action
 * touches and whether the engine announces it to the whole table. This test *is* that table:
 * one row per line of the document, each row staged through the real engine and then measured
 * the way a client would be — every seat's view is serialised and searched for the card's id,
 * which carries the rank, so an id in a seat's bytes is that seat holding the answer whatever
 * field it arrived in. Both halves are asserted: the seat that must see the card, and every
 * seat that must not.
 *
 * Adding an action to the game means adding a row here and to the document. A row that
 * cannot be written is a rule that has not been decided, which is worth discovering before
 * the animation for it is.
 */
class VisibilityMatrixTest {

    /** Who a card may be shown to once the action has happened. */
    private enum class Shown { EVERYONE, ACTOR_ONLY, NOBODY }

    /**
     * One row of Table A: an action, the card worth watching, who may see it afterwards, and
     * whether the engine emitted a [PublicReveal] for it. [stage] plays the position through
     * the real engine, because what may be seen turns on what the engine set when the action
     * ran — a fixture that sets it itself is a fixture agreeing with itself.
     */
    private class Row(
        val action: String,
        val watched: String,
        val shown: Shown,
        val reveal: Boolean = false,
        val stage: () -> Staged,
    )

    private class Staged(
        val state: GameState,
        val revealed: List<PublicReveal>,
        val actor: String,
        val cardId: String,
        val leader: String? = null,
    )

    @Test
    fun everyRowOfTableAHolds() {
        val wrong = mutableListOf<String>()

        for (row in matrix()) {
            val staged = row.stage()
            val seats = staged.state.players.map { it.id }
            val entitled: Set<String> = when (row.shown) {
                Shown.EVERYONE -> seats.toSet()
                Shown.ACTOR_ONLY -> setOf(staged.actor)
                Shown.NOBODY -> emptySet()
            }

            for (seat in seats) {
                val sent = "\"${staged.cardId}\"" in bytes(staged.state, seat)
                val allowed = seat in entitled
                if (sent && !allowed) {
                    wrong += "${row.action}: ${row.watched} leaked to $seat"
                }
                if (!sent && allowed) {
                    wrong += "${row.action}: ${row.watched} was not sent to $seat, who may see it"
                }
            }

            val announced = staged.revealed.any { it.card.id == staged.cardId }
            if (announced != row.reveal) {
                wrong += "${row.action}: ${row.watched} " +
                    if (row.reveal) "was not announced as a PublicReveal" else "was announced to the table"
            }
        }

        assertEquals(emptyList(), wrong, "the code disagrees with Table A of VISIBILITY.md")
    }

    /** The counterweight: a matrix of empty stagings would pass. Every row really ran. */
    @Test
    fun theMatrixIsNotVacuous() {
        val rows = matrix()
        assertTrue(rows.size > 30, "Table A has more rows than ${rows.size}")
        assertTrue(rows.any { it.reveal }, "at least the wrong King and the wrong throw announce")
        assertTrue(rows.any { it.action == "SET_COALITION_LEADER" }, "the coalition is in the table")
    }

    // ------------------------------------------------------------------ the table

    private fun matrix(): List<Row> =
        setupRows() + turnRows() + swapRows() + peekRows() + queenRows() +
            jackRows() + kingRows() + aceAndTossRows() + endgameRows()

    private fun setupRows() = listOf(
        Row("PEEK_SETUP_CARD", "the card you peeked", Shown.ACTOR_ONLY) {
            val play = Play()
            play.act(GameAction.PeekSetupCard(PositionPayload(play.me, 0)))
            play.staged(play.cardAt(play.me, 0))
        },
        Row("FINISH_SETUP", "the same card, once the round starts", Shown.NOBODY) {
            val play = Play()
            play.finishSetup()
            play.staged(play.cardAt(play.me, 0))
        },
    )

    private fun turnRows() = listOf(
        Row("DRAW_CARD", "the drawn card", Shown.EVERYONE) {
            val play = Play()
            play.finishSetup()
            play.draw(Rank.NINE)
            play.staged(play.pendingCardId())
        },
        Row("USE_CARD_ACTION", "the card in play", Shown.EVERYONE) {
            val play = Play()
            play.finishSetup()
            play.draw(Rank.NINE)
            play.act(useCardAction(play.me))
            play.staged(play.pendingCardId())
        },
        Row("DISCARD_CARD", "the discarded card", Shown.EVERYONE) {
            val play = Play()
            play.finishSetup()
            play.draw(play.supply(copies = 1, withAction = false))
            val drawn = play.pendingCardId()
            play.act(discardCard(play.me))
            play.staged(drawn)
        },
        Row("PLAY_DISCARD", "the card taken to be played", Shown.EVERYONE) {
            val play = Play()
            play.finishSetup()
            play.draw(Rank.SEVEN)
            play.act(discardCard(play.me))
            play.everyoneDone()
            play.act(playDiscard(play.opponent))
            play.staged(play.pendingCardId(), actor = play.opponent)
        },
    )

    private fun swapRows() = listOf(
        Row("SWAP_CARD (no declaration)", "the card swapped in", Shown.NOBODY) {
            val play = Play()
            play.finishSetup()
            play.draw(play.supply(copies = 1, withAction = false))
            val drawn = play.pendingCardId()
            play.act(swapCard(play.me, 2))
            play.staged(drawn)
        },
        Row("SWAP_CARD (no declaration)", "the card swapped out", Shown.EVERYONE) {
            val play = Play()
            play.finishSetup()
            play.draw(play.supply(copies = 1, withAction = false))
            val out = play.cardAt(play.me, 2)
            play.act(swapCard(play.me, 2))
            play.staged(out)
        },
        Row("SWAP_CARD (correct declaration)", "the card swapped out, now in play", Shown.EVERYONE) {
            val play = Play()
            play.finishSetup()
            val planted = play.supply(copies = 1, withAction = true, excluding = setOf(Rank.KING))
            play.plant(play.me, 2, planted)
            play.draw(play.supply(copies = 1, withAction = false))
            val out = play.cardAt(play.me, 2)
            play.act(swapCard(play.me, 2, planted))
            play.staged(out)
        },
        Row("SWAP_CARD (wrong declaration)", "the penalty card", Shown.NOBODY) {
            val play = Play()
            play.finishSetup()
            val planted = play.supply(copies = 1, withAction = false)
            play.plant(play.me, 2, planted)
            play.draw(play.supply(copies = 1, withAction = false, excluding = setOf(planted)))
            play.act(swapCard(play.me, 2, Rank.entries.first { it != planted }))
            play.staged(play.lastCardOf(play.me))
        },
    )

    private fun peekRows() = listOf(
        Row("SELECT_ACTION_TARGET (7/8, your own card)", "the card being looked at", Shown.ACTOR_ONLY) {
            val play = Play()
            play.finishSetup()
            play.draw(Rank.SEVEN)
            play.act(useCardAction(play.me))
            play.act(selectTarget(play.me, play.me, 3))
            play.staged(play.cardAt(play.me, 3))
        },
        Row("SELECT_ACTION_TARGET (9/10, an opponent's card)", "the card being looked at", Shown.ACTOR_ONLY) {
            val play = Play()
            play.finishSetup()
            play.draw(Rank.NINE)
            play.act(useCardAction(play.me))
            play.act(selectTarget(play.me, play.opponent, 0))
            play.staged(play.cardAt(play.opponent, 0))
        },
        Row("CONFIRM_PEEK", "the card you just saw", Shown.NOBODY) {
            val play = Play()
            play.finishSetup()
            play.draw(Rank.NINE)
            play.act(useCardAction(play.me))
            play.act(selectTarget(play.me, play.opponent, 0))
            play.act(confirmPeek(play.me))
            play.staged(play.cardAt(play.opponent, 0))
        },
        Row("SKIP_PEEK", "the card you declined to see", Shown.NOBODY) {
            val play = Play()
            play.finishSetup()
            play.draw(Rank.SEVEN)
            play.act(useCardAction(play.me))
            play.act(selectTarget(play.me, play.me, 3))
            play.act(GameAction.SkipPeek(PlayerIdPayload(play.me)))
            play.staged(play.cardAt(play.me, 3))
        },
    )

    private fun queenRows() = listOf(
        Row("SELECT_ACTION_TARGET (Queen, first card)", "the first card looked at", Shown.ACTOR_ONLY) {
            val play = Play().queenAimedAt(first = true)
            play.staged(play.cardAt(play.opponent, 0))
        },
        Row("SELECT_ACTION_TARGET (Queen, second card)", "the second card looked at", Shown.ACTOR_ONLY) {
            val play = Play().queenAimedAt(first = false)
            play.staged(play.cardAt(play.me, 3))
        },
        Row("EXECUTE_QUEEN_SWAP", "a card once it has swapped", Shown.NOBODY) {
            val play = Play().queenAimedAt(first = false)
            val watched = play.cardAt(play.opponent, 0)
            play.act(GameAction.ExecuteQueenSwap(PlayerIdPayload(play.me)))
            play.staged(watched)
        },
        Row("SKIP_QUEEN_SWAP", "the cards you looked at", Shown.NOBODY) {
            val play = Play().queenAimedAt(first = false)
            val watched = play.cardAt(play.opponent, 0)
            play.act(GameAction.SkipQueenSwap(PlayerIdPayload(play.me)))
            play.staged(watched)
        },
    )

    private fun jackRows() = listOf(
        Row("SELECT_ACTION_TARGET (Jack, your card)", "the card being aimed at", Shown.NOBODY) {
            val play = Play().jackAimedAt(first = true)
            play.staged(play.cardAt(play.me, 3))
        },
        Row("SELECT_ACTION_TARGET (Jack, their card)", "the card being aimed at", Shown.NOBODY) {
            val play = Play().jackAimedAt(first = false)
            play.staged(play.cardAt(play.opponent, 0))
        },
        Row("EXECUTE_JACK_SWAP", "a card once it has swapped", Shown.NOBODY) {
            val play = Play().jackAimedAt(first = false)
            val watched = play.cardAt(play.opponent, 0)
            play.act(GameAction.ExecuteJackSwap(PlayerIdPayload(play.me)))
            play.staged(watched)
        },
        Row("SKIP_JACK_SWAP", "the cards left alone", Shown.NOBODY) {
            val play = Play().jackAimedAt(first = false)
            val watched = play.cardAt(play.opponent, 0)
            play.act(GameAction.SkipJackSwap(PlayerIdPayload(play.me)))
            play.staged(watched)
        },
    )

    private fun kingRows() = listOf(
        Row("SELECT_ACTION_TARGET (King)", "the card it points at", Shown.NOBODY) {
            val play = Play()
            play.finishSetup()
            play.draw(Rank.KING)
            play.act(useCardAction(play.me))
            play.act(selectTarget(play.me, play.me, 3))
            play.staged(play.cardAt(play.me, 3))
        },
        Row("DECLARE_KING_ACTION (right, plain card)", "the named card, on the pile", Shown.EVERYONE) {
            val play = Play()
            play.finishSetup()
            val planted = play.supply(copies = 1, withAction = false)
            play.plant(play.me, 3, planted)
            play.draw(Rank.KING)
            play.act(useCardAction(play.me))
            play.act(selectTarget(play.me, play.me, 3))
            val named = play.cardAt(play.me, 3)
            play.act(declareKing(play.me, planted))
            play.staged(named)
        },
        Row("DECLARE_KING_ACTION (right, action card)", "the named card, now in play", Shown.EVERYONE) {
            val play = Play()
            play.finishSetup()
            val planted = play.supply(copies = 1, withAction = true, excluding = setOf(Rank.KING))
            play.plant(play.me, 3, planted)
            play.draw(Rank.KING)
            play.act(useCardAction(play.me))
            play.act(selectTarget(play.me, play.me, 3))
            val named = play.cardAt(play.me, 3)
            play.act(declareKing(play.me, planted))
            play.staged(named)
        },
        Row("DECLARE_KING_ACTION (wrong)", "the named card", Shown.NOBODY, reveal = true) {
            val play = Play().kingDeclaredWrong()
            play.staged(play.cardAt(play.opponent, 0))
        },
        Row("DECLARE_KING_ACTION (wrong)", "the penalty card", Shown.NOBODY) {
            val play = Play().kingDeclaredWrong()
            play.staged(play.lastCardOf(play.me))
        },
    )

    private fun aceAndTossRows() = listOf(
        Row("SELECT_ACTION_TARGET (Ace)", "the card the target must draw", Shown.NOBODY) {
            val play = Play()
            play.finishSetup()
            play.draw(Rank.ACE)
            play.act(useCardAction(play.me))
            play.act(selectPlayerTarget(play.me, play.opponent))
            play.staged(play.lastCardOf(play.opponent))
        },
        Row("PARTICIPATE_IN_TOSS_IN (right)", "the thrown card, under the top of the pile", Shown.NOBODY) {
            val play = Play()
            play.finishSetup()
            val rank = play.supply(copies = 2, withAction = false)
            play.plant(play.me, 3, rank)
            play.draw(rank)
            play.act(discardCard(play.me))
            val thrown = play.cardAt(play.me, 3)
            play.act(participateInTossIn(play.me, listOf(3)))
            play.staged(thrown)
        },
        Row("PARTICIPATE_IN_TOSS_IN (wrong)", "the card that missed", Shown.NOBODY, reveal = true) {
            val play = Play().tossedWrong()
            play.staged(play.cardAt(play.me, 3))
        },
        Row("PARTICIPATE_IN_TOSS_IN (wrong)", "the penalty card", Shown.NOBODY) {
            val play = Play().tossedWrong()
            play.staged(play.lastCardOf(play.me))
        },
        Row("PLAYER_TOSS_IN_FINISHED (queue starts)", "the thrown action card, now in play", Shown.EVERYONE) {
            val play = Play()
            play.finishSetup()
            val rank = play.supply(copies = 2, withAction = true, excluding = setOf(Rank.KING))
            play.plant(play.me, 3, rank)
            play.draw(rank)
            play.act(discardCard(play.me))
            play.act(participateInTossIn(play.me, listOf(3)))
            play.everyoneDone()
            play.staged(play.pendingCardId())
        },
    )

    private fun endgameRows() = listOf(
        Row("CALL_VINTO", "the caller's own card", Shown.NOBODY) {
            val play = Play().vintoCalled()
            play.staged(play.cardAt(play.me, 0))
        },
        // The leader used to be sent every member's real hand; coalition knowledge now
        // travels as declared claims, so being nominated shows the leader nothing.
        Row("SET_COALITION_LEADER", "a coalition member's card", Shown.NOBODY) {
            val play = Play().vintoCalled()
            play.act(GameAction.SetCoalitionLeader(LeaderIdPayload(play.opponent)))
            val member = play.state.players[2]
            play.staged(member.cards[0].id, leader = play.opponent)
        },
        Row("SET_COALITION_LEADER", "the Vinto caller's card", Shown.NOBODY) {
            val play = Play().vintoCalled()
            play.act(GameAction.SetCoalitionLeader(LeaderIdPayload(play.opponent)))
            play.staged(play.cardAt(play.me, 0), leader = play.opponent)
        },
        Row("END_ROUND (scoring)", "any card at all", Shown.EVERYONE) {
            val start = deadFinalRound()
            val result = GameEngine.reduce(start, GameAction.EndRound(PlayerIdPayload("p1")))
            val state = (result as ReduceResult.Success).state
            Staged(state, result.revealed, actor = "p1", cardId = "end_p2_0")
        },
        Row("reshuffle (the turn advancing)", "a card the pile takes back", Shown.NOBODY) {
            val start = starvedDeck()
            val watched = start.discardPile.toList()[2].id
            val result = GameEngine.reduce(
                start,
                GameAction.PlayerTossInFinished(PlayerIdPayload("p1")),
            )
            val state = (result as ReduceResult.Success).state
            check(state.drawPile.size > start.drawPile.size) { "the deck did not refill" }
            Staged(state, result.revealed, actor = "p1", cardId = watched)
        },
    )

    // ------------------------------------------------------------------ the staging

    /** A dealt game and the means to move it, capturing what the last action announced. */
    private class Play(seed: Long = SEED) {
        var state: GameState = initializeGame(seed, Difficulty.EASY)
            private set
        var revealed: List<PublicReveal> = emptyList()
            private set

        val me: String = state.players.first { it.isHuman }.id
        val opponent: String = state.players.first { !it.isHuman }.id

        fun act(action: GameAction) {
            when (val result = GameEngine.reduce(state, action)) {
                is ReduceResult.Success -> {
                    state = result.state
                    revealed = result.revealed
                }

                is ReduceResult.Failure -> error("${action.type} refused: ${result.reason}")
            }
        }

        fun finishSetup() {
            act(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            act(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            act(GameAction.FinishSetup(PlayerIdPayload(me)))
        }

        fun draw(rank: Rank) {
            act(GameAction.SetNextDrawCard(RankPayload(rank)))
            act(drawCard(me))
        }

        /** Puts a card of [rank] at [position] of [seat]'s hand, so a row can rely on it. */
        fun plant(seat: String, position: Int, rank: Rank) =
            act(GameAction.SwapHandWithDeck(SwapHandWithDeckPayload(seat, position, rank)))

        /**
         * A rank the draw pile can still supply [copies] of. The deal is seeded, so which
         * ranks remain in the pile is an accident of the seed; a row that says "plant a Five
         * and draw a Five" fails on a deal that dealt all four Fives, and the row is about
         * matching ranks, not about Fives.
         */
        fun supply(copies: Int, withAction: Boolean, excluding: Set<Rank> = emptySet()): Rank =
            Rank.entries.first { rank ->
                rank !in excluding &&
                    hasAction(rank) == withAction &&
                    state.drawPile.toList().count { it.rank == rank } >= copies
            }

        fun everyoneDone() = state.players.forEach {
            act(GameAction.PlayerTossInFinished(PlayerIdPayload(it.id)))
        }

        fun cardAt(seat: String, position: Int): String =
            state.players.first { it.id == seat }.cards[position].id

        fun lastCardOf(seat: String): String =
            state.players.first { it.id == seat }.cards.last().id

        fun pendingCardId(): String = checkNotNull(state.pendingAction).card.id

        fun staged(cardId: String, actor: String = me, leader: String? = null) =
            Staged(state, revealed, actor, cardId, leader)
    }

    private fun Play.queenAimedAt(first: Boolean): Play {
        finishSetup()
        draw(Rank.QUEEN)
        act(useCardAction(me))
        act(selectTarget(me, opponent, 0))
        if (!first) act(selectTarget(me, me, 3))
        return this
    }

    private fun Play.jackAimedAt(first: Boolean): Play {
        finishSetup()
        draw(Rank.JACK)
        act(useCardAction(me))
        act(selectTarget(me, me, 3))
        if (!first) act(selectTarget(me, opponent, 0))
        return this
    }

    private fun Play.kingDeclaredWrong(): Play {
        finishSetup()
        val planted = supply(copies = 1, withAction = false)
        plant(opponent, 0, planted)
        draw(Rank.KING)
        act(useCardAction(me))
        act(selectTarget(me, opponent, 0))
        act(declareKing(me, Rank.entries.first { it != planted }))
        return this
    }

    private fun Play.tossedWrong(): Play {
        finishSetup()
        val planted = supply(copies = 1, withAction = false)
        plant(me, 3, planted)
        draw(supply(copies = 1, withAction = false, excluding = setOf(planted)))
        act(discardCard(me))
        act(participateInTossIn(me, listOf(3)))
        return this
    }

    private fun Play.vintoCalled(): Play {
        finishSetup()
        draw(supply(copies = 1, withAction = false))
        act(discardCard(me))
        act(callVinto(me))
        return this
    }

    /**
     * A final round nobody can play on: empty deck, nothing takeable, no action in flight.
     * Built by hand because the position is the *end* of a long game, and the action under
     * test still passes through the real validator and reducer.
     */
    private fun deadFinalRound(): GameState {
        val players = listOf(
            testPlayer("p1", "P1", isHuman = true, cards = listOf(testCard(Rank.FIVE, "end_p1_0"))),
            testPlayer("p2", "P2", isHuman = false, cards = listOf(testCard(Rank.KING, "end_p2_0"))),
            testPlayer("p3", "P3", isHuman = false, cards = listOf(testCard(Rank.TWO, "end_p3_0"))),
            testPlayer("p4", "P4", isHuman = false, cards = listOf(testCard(Rank.ACE, "end_p4_0"))),
        ).map {
            if (it.id == "p1") {
                it.copy(isVintoCaller = true)
            } else {
                it.copy(coalitionWith = listOf("p2", "p3", "p4") - it.id)
            }
        }
        return testState(
            players = players,
            phase = GamePhase.FINAL,
            vintoCallerId = "p1",
            discardPile = pileOf(testCard(Rank.TWO, "end_pile_top")),
        )
    }

    /** One card left to draw, so the next turn advance sweeps the pile back into the deck. */
    private fun starvedDeck(): GameState = testState(
        subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
        drawPile = pileOf(testCard(Rank.TWO, "deck_last")),
        discardPile = pileOf(
            testCard(Rank.THREE, "pile_top"),
            testCard(Rank.FOUR, "pile_1"),
            testCard(Rank.FIVE, "pile_2"),
            testCard(Rank.SIX, "pile_3"),
        ),
        activeTossIn = tossIn(
            ranks = listOf(Rank.THREE),
            initiatorId = "p1",
            playersReadyForNextTurn = listOf("p2", "p3", "p4"),
        ),
    )

    // ------------------------------------------------------------------ the measuring

    /** One seat's whole view, as the bytes a client would receive. */
    private fun bytes(state: GameState, seat: String): String =
        VintoJson.encodeToString(projectView(state, seat))

    private companion object {
        const val SEED = 7L
    }
}
