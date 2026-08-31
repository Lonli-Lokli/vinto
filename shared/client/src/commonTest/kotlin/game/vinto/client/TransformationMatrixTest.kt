package game.vinto.client

import game.vinto.engine.CardView
import game.vinto.engine.GameEngine
import game.vinto.engine.PublicReveal
import game.vinto.engine.ReduceResult
import game.vinto.engine.initializeGame
import game.vinto.engine.projectView
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Card
import game.vinto.shapes.DeclareKingActionPayload
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.LeaderIdPayload
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.Pile
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SelectActionTargetPayload
import game.vinto.shapes.SwapCardPayload
import game.vinto.shapes.SwapHandWithDeckPayload
import game.vinto.shapes.actorId
import game.vinto.shapes.getCardShortDescription
import game.vinto.shapes.getCardValue
import game.vinto.shapes.hasAction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Table B of `docs/game-engine/VISIBILITY.md`, executable.
 *
 * The specification says, for every action, what the table draws for it — which cards
 * travel, which are lifted or shown, and crucially how the *same* action looks different
 * from different seats: a peek shows a face to the seat that made it and a blank lift to
 * everybody else. Each row here stages the action through the real engine, choreographs it
 * twice — once from the actor's view, once from a bystander's — and asserts the whole beat
 * script for both, the way `LocalGameSession` composes it: the choreography plus whatever
 * the action turned face up.
 *
 * The script notation, in full: `fly a→b` a card travelling (`face` when its face shows in
 * flight, `lit` when the table is being shown it); `peek a` a card lifted where it lies
 * (`face` or `blank`); `flourish a` a card played, swelling where it lies; `reveal a` a card
 * turned face up for the whole table; `verdict` the ring a declaration earns; `flinch` a
 * hand jolting; `attend` a seat lighting up; `say` a line; `borrowed` a King holding up the
 * rank it declared; `reshuffle n` the pile sweeping back into the deck. Beats joined with
 * `+` happen together; scenes joined with `;` happen in order. Seats are named for the
 * staging: `you` acts, `opp` is the seat most rows aim at, `bot2` watches.
 *
 * A final `hold a` clause is not a scene but a *state*: the cards the view still holds up
 * once the action has landed, which the table keeps lifted until an action resolves them
 * (see `heldUp` and `docs/kotlin/CHOREOGRAPHY.md`). A row without one leaves nothing in the
 * air — so the Queen's aim rows *ending* with two holds, and her execute and skip rows
 * ending with none, is the fix for the lowered-between-taps defect, written down.
 */
class TransformationMatrixTest {

    /**
     * One row of Table B: an action, and the full script of what each side of the table is
     * shown. Two columns rather than one because the difference between them *is* the
     * privacy model, drawn: wherever the columns differ, a face was withheld.
     */
    private class Row(
        val action: String,
        val actorSees: String,
        val othersSee: String,
        val stage: () -> Staged,
    )

    private class Staged(
        val action: GameAction,
        val before: GameState,
        val after: GameState,
        val revealed: List<PublicReveal>,
        val actor: String,
        val observer: String = "bot-2",
    )

    @Test
    fun everyRowOfTableBHolds() {
        val wrong = mutableListOf<String>()

        for (row in matrix()) {
            val staged = row.stage()
            val actor = script(staged, staged.actor)
            val others = script(staged, staged.observer)

            if (actor != row.actorSees) {
                wrong += "${row.action}: the actor sees [$actor], Table B says [${row.actorSees}]"
            }
            if (others != row.othersSee) {
                wrong += "${row.action}: a watcher sees [$others], Table B says [${row.othersSee}]"
            }
        }

        assertEquals(emptyList(), wrong, "the choreography disagrees with Table B of VISIBILITY.md")
    }

    /** The columns must differ exactly where a face is private, and nowhere else. */
    @Test
    fun theColumnsDifferOnlyByWithheldFaces() {
        fun faceless(script: String) = script.replace(" face", "").replace(" blank", "")

        for (row in matrix()) {
            if (row.actorSees == row.othersSee) continue
            assertEquals(
                faceless(row.actorSees),
                faceless(row.othersSee),
                "${row.action}: the viewpoints differ by more than who is shown a face",
            )
        }
    }

    // ------------------------------------------------------------------ the table

    private fun matrix(): List<Row> =
        turnRows() + swapRows() + peekRows() + queenAndJackRows() + kingRows() +
            tossRows() + endgameRows()

    private fun turnRows() = listOf(
        Row(
            "DRAW_CARD",
            actorSees = "fly deck→drawn face",
            othersSee = "fly deck→drawn face",
        ) {
            val play = Play()
            play.draw(Rank.NINE)
            play.last()
        },
        Row(
            "DISCARD_CARD",
            actorSees = "fly drawn→pile face",
            othersSee = "fly drawn→pile face",
        ) {
            val play = Play()
            play.draw(play.supply(copies = 1, withAction = false))
            play.act(GameAction.DiscardCard(PlayerIdPayload(play.me)))
            play.last()
        },
        Row(
            "USE_CARD_ACTION",
            actorSees = "flourish drawn; fly drawn→pile face lit",
            othersSee = "flourish drawn; fly drawn→pile face lit",
        ) {
            val play = Play()
            play.draw(Rank.NINE)
            play.act(GameAction.UseCardAction(PlayerIdPayload(play.me)))
            play.last()
        },
        Row(
            "PLAY_DISCARD",
            actorSees = "flourish pile",
            othersSee = "flourish pile",
        ) {
            val play = Play()
            play.draw(Rank.SEVEN)
            play.act(GameAction.DiscardCard(PlayerIdPayload(play.me)))
            play.everyoneDone()
            play.act(GameAction.PlayDiscard(PlayerIdPayload(play.opp)))
            play.last()
        },
    )

    private fun swapRows() = listOf(
        Row(
            "SWAP_CARD (no declaration)",
            actorSees = "fly drawn→you[2] face + fly you[2]→pile face",
            othersSee = "fly drawn→you[2] face + fly you[2]→pile face",
        ) {
            val play = Play()
            play.draw(play.supply(copies = 1, withAction = false))
            play.act(GameAction.SwapCard(SwapCardPayload(play.me, 2)))
            play.last()
        },
        Row(
            "SWAP_CARD (correct declaration)",
            actorSees = "fly drawn→you[2] face + fly you[2]→pile face lit; verdict green",
            othersSee = "fly drawn→you[2] face + fly you[2]→pile face lit; verdict green",
        ) {
            val play = Play()
            val planted = play.supply(copies = 1, withAction = true, excluding = setOf(Rank.KING))
            play.plant(play.me, 2, planted)
            play.draw(play.supply(copies = 1, withAction = false))
            play.act(GameAction.SwapCard(SwapCardPayload(play.me, 2, planted)))
            play.last()
        },
        Row(
            "SWAP_CARD (wrong declaration)",
            actorSees = "fly drawn→you[2] face + fly you[2]→pile face; verdict red; " +
                "fly deck→you[5] + flinch you[5] + attend you penalty + say you",
            othersSee = "fly drawn→you[2] face + fly you[2]→pile face; verdict red; " +
                "fly deck→you[5] + flinch you[5] + attend you penalty + say you",
        ) {
            val play = Play()
            val planted = play.supply(copies = 1, withAction = false)
            play.plant(play.me, 2, planted)
            play.draw(play.supply(copies = 1, withAction = false, excluding = setOf(planted)))
            play.act(
                GameAction.SwapCard(
                    SwapCardPayload(play.me, 2, Rank.entries.first { it != planted }),
                ),
            )
            play.last()
        },
    )

    private fun peekRows() = listOf(
        Row(
            "SELECT_ACTION_TARGET (7/8, your own card)",
            actorSees = "peek you[3] face; hold you[3] face",
            othersSee = "peek you[3] blank; hold you[3] blank",
        ) {
            val play = Play()
            play.draw(Rank.SEVEN)
            play.act(GameAction.UseCardAction(PlayerIdPayload(play.me)))
            play.aim(play.me, 3)
            play.last()
        },
        Row(
            "SELECT_ACTION_TARGET (9/10, an opponent's card)",
            actorSees = "peek opp[0] face; hold opp[0] face",
            othersSee = "peek opp[0] blank; hold opp[0] blank",
        ) {
            val play = Play()
            play.draw(Rank.NINE)
            play.act(GameAction.UseCardAction(PlayerIdPayload(play.me)))
            play.aim(play.opp, 0)
            play.last()
        },
        Row(
            "CONFIRM_PEEK",
            actorSees = "nothing",
            othersSee = "nothing",
        ) {
            val play = Play()
            play.draw(Rank.NINE)
            play.act(GameAction.UseCardAction(PlayerIdPayload(play.me)))
            play.aim(play.opp, 0)
            play.act(GameAction.ConfirmPeek(PlayerIdPayload(play.me)))
            play.last()
        },
        Row(
            "SELECT_ACTION_TARGET (Ace)",
            actorSees = "attend opp penalty + say opp; fly deck→opp[5] + flinch opp[5] + attend opp penalty",
            othersSee = "attend opp penalty + say opp; fly deck→opp[5] + flinch opp[5] + attend opp penalty",
        ) {
            val play = Play()
            play.draw(Rank.ACE)
            play.act(GameAction.UseCardAction(PlayerIdPayload(play.me)))
            play.act(
                GameAction.SelectActionTarget(SelectActionTargetPayload.Ace(play.me, play.opp)),
            )
            play.last()
        },
    )

    private fun queenAndJackRows() = listOf(
        Row(
            "SELECT_ACTION_TARGET (Queen, each look)",
            actorSees = "peek you[3] face; hold opp[0] face + hold you[3] face",
            othersSee = "peek you[3] blank; hold opp[0] blank + hold you[3] blank",
        ) {
            val play = Play().queenAimed()
            play.last()
        },
        Row(
            "EXECUTE_QUEEN_SWAP",
            actorSees = "fly opp[0]→you[3] face + fly you[3]→opp[0] face",
            othersSee = "fly opp[0]→you[3] + fly you[3]→opp[0]",
        ) {
            val play = Play().queenAimed()
            play.act(GameAction.ExecuteQueenSwap(PlayerIdPayload(play.me)))
            play.last()
        },
        Row(
            "SKIP_QUEEN_SWAP",
            actorSees = "flinch opp[0] + flinch you[3]",
            othersSee = "flinch opp[0] + flinch you[3]",
        ) {
            val play = Play().queenAimed()
            play.act(GameAction.SkipQueenSwap(PlayerIdPayload(play.me)))
            play.last()
        },
        Row(
            "SELECT_ACTION_TARGET (Jack)",
            actorSees = "peek you[3] blank; hold you[3] blank",
            othersSee = "peek you[3] blank; hold you[3] blank",
        ) {
            val play = Play()
            play.draw(Rank.JACK)
            play.act(GameAction.UseCardAction(PlayerIdPayload(play.me)))
            play.aim(play.me, 3)
            play.last()
        },
        Row(
            "EXECUTE_JACK_SWAP",
            actorSees = "fly you[3]→opp[0] + fly opp[0]→you[3]",
            othersSee = "fly you[3]→opp[0] + fly opp[0]→you[3]",
        ) {
            val play = Play().jackAimed()
            play.act(GameAction.ExecuteJackSwap(PlayerIdPayload(play.me)))
            play.last()
        },
        Row(
            "SKIP_JACK_SWAP",
            actorSees = "flinch you[3] + flinch opp[0]",
            othersSee = "flinch you[3] + flinch opp[0]",
        ) {
            val play = Play().jackAimed()
            play.act(GameAction.SkipJackSwap(PlayerIdPayload(play.me)))
            play.last()
        },
    )

    private fun kingRows() = listOf(
        Row(
            "SELECT_ACTION_TARGET (King)",
            actorSees = "peek you[3] blank; hold you[3] blank",
            othersSee = "peek you[3] blank; hold you[3] blank",
        ) {
            val play = Play()
            play.draw(Rank.KING)
            play.act(GameAction.UseCardAction(PlayerIdPayload(play.me)))
            play.aim(play.me, 3)
            play.last()
        },
        Row(
            "DECLARE_KING_ACTION (right, plain card)",
            actorSees = "borrowed + fly you[3]→pile face lit; verdict green",
            othersSee = "borrowed + fly you[3]→pile face lit; verdict green",
        ) {
            val play = Play().kingAimedAtPlanted(withAction = false)
            play.act(GameAction.DeclareKingAction(DeclareKingActionPayload(play.me, play.planted)))
            play.last()
        },
        Row(
            "DECLARE_KING_ACTION (right, action card)",
            actorSees = "borrowed + fly you[3]→pile face lit; verdict green",
            othersSee = "borrowed + fly you[3]→pile face lit; verdict green",
        ) {
            val play = Play().kingAimedAtPlanted(withAction = true)
            play.act(GameAction.DeclareKingAction(DeclareKingActionPayload(play.me, play.planted)))
            play.last()
        },
        Row(
            "DECLARE_KING_ACTION (wrong)",
            actorSees = "borrowed; verdict red; " +
                "fly deck→you[5] + flinch you[5] + attend you penalty; reveal you[3]",
            othersSee = "borrowed; verdict red; " +
                "fly deck→you[5] + flinch you[5] + attend you penalty; reveal you[3]",
        ) {
            val play = Play().kingAimedAtPlanted(withAction = false)
            play.act(
                GameAction.DeclareKingAction(
                    DeclareKingActionPayload(play.me, Rank.entries.first { it != play.planted }),
                ),
            )
            play.last()
        },
    )

    private fun tossRows() = listOf(
        Row(
            "PARTICIPATE_IN_TOSS_IN (right)",
            actorSees = "fly you[3]→pile face",
            othersSee = "fly you[3]→pile face",
        ) {
            val play = Play()
            val rank = play.supply(copies = 2, withAction = false)
            play.plant(play.me, 3, rank)
            play.draw(rank)
            play.act(GameAction.DiscardCard(PlayerIdPayload(play.me)))
            play.act(GameAction.ParticipateInTossIn(ParticipateInTossInPayload(play.me, listOf(3))))
            play.last()
        },
        Row(
            "PARTICIPATE_IN_TOSS_IN (wrong)",
            actorSees = "fly deck→you[5] + flinch you[5] + attend you penalty + say you; reveal you[3]",
            othersSee = "fly deck→you[5] + flinch you[5] + attend you penalty + say you; reveal you[3]",
        ) {
            val play = Play()
            val planted = play.supply(copies = 1, withAction = false)
            play.plant(play.me, 3, planted)
            play.draw(play.supply(copies = 1, withAction = false, excluding = setOf(planted)))
            play.act(GameAction.DiscardCard(PlayerIdPayload(play.me)))
            play.act(GameAction.ParticipateInTossIn(ParticipateInTossInPayload(play.me, listOf(3))))
            play.last()
        },
        Row(
            "PLAYER_TOSS_IN_FINISHED (queue starts)",
            actorSees = "flourish pile",
            othersSee = "flourish pile",
        ) {
            val play = Play()
            val rank = play.supply(copies = 2, withAction = true, excluding = setOf(Rank.KING))
            play.plant(play.me, 3, rank)
            play.draw(rank)
            play.act(GameAction.DiscardCard(PlayerIdPayload(play.me)))
            play.act(GameAction.ParticipateInTossIn(ParticipateInTossInPayload(play.me, listOf(3))))
            play.everyoneDone()
            play.last()
        },
    )

    private fun endgameRows() = listOf(
        Row(
            "CALL_VINTO",
            actorSees = "attend you vinto + say you; attend opp turn",
            othersSee = "attend you vinto + say you; attend opp turn",
        ) {
            val play = Play().vintoCalled()
            play.last()
        },
        Row(
            "SET_COALITION_LEADER",
            actorSees = "attend opp coalition",
            othersSee = "attend opp coalition",
        ) {
            val play = Play().vintoCalled()
            play.act(GameAction.SetCoalitionLeader(LeaderIdPayload(play.opp)))
            play.last()
        },
        Row(
            "reshuffle (the turn advancing)",
            actorSees = "reshuffle 3 + attend opp turn",
            othersSee = "reshuffle 3 + attend opp turn",
        ) {
            val play = Play(starvedDeck())
            play.act(GameAction.PlayerTossInFinished(PlayerIdPayload(play.me)))
            play.last()
        },
        Row(
            // The reveal at scoring: every hand turns over, one seat per scene, in table
            // order — the same script for every viewer, because favouring the viewer would
            // make two seats' scripts differ by more than a withheld face. Each seat here
            // holds one card, so each scene is one reveal.
            "END_ROUND (scoring)",
            actorSees = "reveal you[0]; reveal opp[0]; reveal bot2[0]; reveal bot3[0]",
            othersSee = "reveal you[0]; reveal opp[0]; reveal bot2[0]; reveal bot3[0]",
        ) {
            val play = Play(deadFinalRound())
            play.act(GameAction.EndRound(PlayerIdPayload(play.me)))
            play.last()
        },
    )

    // ------------------------------------------------------------------ the staging

    /**
     * A dealt game and the means to move it, remembering the last action and what
     * surrounded it — which is exactly what a frame is built from.
     */
    private class Play(start: GameState = ready()) {
        var state: GameState = start
            private set
        val me = "human-1"
        val opp = "bot-1"
        var planted: Rank = Rank.TWO
            private set

        private var staged: Staged? = null

        fun act(action: GameAction) {
            val before = state
            when (val result = GameEngine.reduce(state, action)) {
                is ReduceResult.Success -> {
                    state = result.state
                    staged = Staged(
                        action = action,
                        before = before,
                        after = result.state,
                        revealed = result.revealed,
                        actor = action.actorId ?: me,
                    )
                }

                is ReduceResult.Failure -> error("${action.type} refused: ${result.reason}")
            }
        }

        fun last(): Staged = checkNotNull(staged) { "nothing was staged" }

        fun draw(rank: Rank) {
            act(GameAction.SetNextDrawCard(RankPayload(rank)))
            act(GameAction.DrawCard(PlayerIdPayload(me)))
        }

        fun plant(seat: String, position: Int, rank: Rank) {
            act(GameAction.SwapHandWithDeck(SwapHandWithDeckPayload(seat, position, rank)))
            planted = rank
        }

        fun aim(target: String, position: Int) = act(
            GameAction.SelectActionTarget(
                SelectActionTargetPayload.Positional(me, target, position),
            ),
        )

        fun everyoneDone() = state.players.forEach {
            act(GameAction.PlayerTossInFinished(PlayerIdPayload(it.id)))
        }

        /** Same reasoning as the engine matrix: the row is about ranks matching, not Fives. */
        fun supply(copies: Int, withAction: Boolean, excluding: Set<Rank> = emptySet()): Rank =
            Rank.entries.first { rank ->
                rank !in excluding &&
                    hasAction(rank) == withAction &&
                    state.drawPile.toList().count { it.rank == rank } >= copies
            }
    }

    private fun Play.queenAimed(): Play {
        draw(Rank.QUEEN)
        act(GameAction.UseCardAction(PlayerIdPayload(me)))
        aim(opp, 0)
        aim(me, 3)
        return this
    }

    private fun Play.jackAimed(): Play {
        draw(Rank.JACK)
        act(GameAction.UseCardAction(PlayerIdPayload(me)))
        aim(me, 3)
        aim(opp, 0)
        return this
    }

    private fun Play.kingAimedAtPlanted(withAction: Boolean): Play {
        val rank = supply(copies = 1, withAction = withAction, excluding = setOf(Rank.KING))
        plant(me, 3, rank)
        draw(Rank.KING)
        act(GameAction.UseCardAction(PlayerIdPayload(me)))
        aim(me, 3)
        return this
    }

    private fun Play.vintoCalled(): Play {
        draw(supply(copies = 1, withAction = false))
        act(GameAction.DiscardCard(PlayerIdPayload(me)))
        act(GameAction.CallVinto(PlayerIdPayload(me)))
        return this
    }

    // ------------------------------------------------------------------ the measuring

    /**
     * What one seat is shown for the staged action, composed exactly as [LocalGameSession]
     * composes a frame: the choreography of the two views, then whatever the action turned
     * face up for the table.
     */
    private fun script(staged: Staged, viewer: String): String {
        val before = projectView(staged.before, viewer)
        val after = projectView(staged.after, viewer)
        val scenes = choreograph(staged.action, before, after) + revealScene(staged.revealed)

        val played = scenes.joinToString("; ") { scene ->
            scene.joinToString(" + ") { describe(it) }
        }

        // What the view leaves in the air — a state, not a scene, so it closes the script:
        // the table keeps these lifted until a later action resolves them.
        val holding = after.heldUp().entries.joinToString(" + ") { (anchor, card) ->
            "hold ${place(anchor)}" + if (card is CardView.Visible) " face" else " blank"
        }

        val parts = listOf(played, holding).filter { it.isNotEmpty() }
        return if (parts.isEmpty()) "nothing" else parts.joinToString("; ")
    }

    private fun describe(beat: Beat): String = when (beat) {
        is Beat.Move -> "fly ${place(beat.from)}→${place(beat.to)}" +
            (if (beat.card != null) " face" else "") + (if (beat.shown) " lit" else "")

        is Beat.Peek -> "peek ${place(beat.at)}" + if (beat.card != null) " face" else " blank"
        is Beat.Flourish -> "flourish ${place(beat.at)}"
        is Beat.Reveal -> "reveal ${place(beat.at)}"
        is Beat.Verdict -> if (beat.correct) "verdict green" else "verdict red"
        is Beat.Flinch -> "flinch ${place(beat.at)}"
        is Beat.Attend -> "attend ${name(beat.playerId)} ${beat.kind.name.lowercase()}"
        is Beat.Say -> "say ${name(beat.playerId)}"
        is Beat.Reshuffle -> "reshuffle ${beat.cards}"
        is Beat.Borrowed -> "borrowed"
    }

    private fun place(anchor: Anchor): String = when (anchor) {
        Anchor.Deck -> "deck"
        Anchor.Discard -> "pile"
        Anchor.Pending -> "drawn"
        is Anchor.Seat -> "${name(anchor.playerId)}[${anchor.position}]"
    }

    private fun name(id: String): String = when (id) {
        "human-1" -> "you"
        "bot-1" -> "opp"
        "bot-2" -> "bot2"
        "bot-3" -> "bot3"
        else -> id
    }

    private companion object {
        const val SEED = 7L

        /** A dealt game with setup finished, so a row starts at the top of a turn. */
        fun ready(): GameState {
            var state = initializeGame(SEED, Difficulty.EASY)
            listOf(0, 1).forEach { position ->
                state = reduceOrThrow(
                    state,
                    GameAction.PeekSetupCard(PositionPayload("human-1", position)),
                ).state
            }
            return reduceOrThrow(state, GameAction.FinishSetup(PlayerIdPayload("human-1"))).state
        }

        fun reduceOrThrow(state: GameState, action: GameAction): ReduceResult.Success =
            GameEngine.reduce(state, action) as? ReduceResult.Success
                ?: error("setup action ${action.type} refused")

        fun card(rank: Rank, id: String) = Card(
            id = id,
            rank = rank,
            value = getCardValue(rank),
            played = false,
            actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
        )

        fun seat(id: String, cards: List<Card>, caller: Boolean = false) = PlayerState(
            id = id,
            name = id,
            nickname = id,
            isHuman = id == "human-1",
            isBot = id != "human-1",
            cards = cards,
            knownCardPositions = emptyList(),
            isVintoCaller = caller,
            coalitionWith = emptyList(),
        )

        @Suppress("LongParameterList")
        fun table(
            phase: GamePhase,
            subPhase: GameSubPhase,
            players: List<PlayerState>,
            drawPile: List<Card>,
            discardPile: List<Card>,
            vintoCallerId: String? = null,
            activeTossIn: ActiveTossIn? = null,
        ) = GameState(
            gameId = "spec",
            roundNumber = 1,
            turnNumber = 0,
            phase = phase,
            subPhase = subPhase,
            finalTurnTriggered = vintoCallerId != null,
            players = players,
            currentPlayerIndex = 0,
            vintoCallerId = vintoCallerId,
            coalitionLeaderId = null,
            drawPile = Pile(drawPile),
            discardPile = Pile(discardPile),
            pendingAction = null,
            activeTossIn = activeTossIn,
            turnActions = emptyList(),
            roundActions = emptyList(),
            roundFailedAttempts = emptyList(),
            difficulty = Difficulty.EASY,
            rngState = 0,
        )

        /**
         * One card left to draw, everybody else already done with the toss-in window, so
         * the next confirmation advances the turn and sweeps the pile back into the deck.
         * Built by hand because the position is the end of a long game; the action under
         * test still passes through the real validator and reducer.
         */
        fun starvedDeck(): GameState = table(
            phase = GamePhase.PLAYING,
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            players = listOf(
                seat("human-1", emptyList()),
                seat("bot-1", emptyList()),
                seat("bot-2", emptyList()),
                seat("bot-3", emptyList()),
            ),
            drawPile = listOf(card(Rank.TWO, "deck_last")),
            discardPile = listOf(
                card(Rank.THREE, "pile_top"),
                card(Rank.FOUR, "pile_1"),
                card(Rank.FIVE, "pile_2"),
                card(Rank.SIX, "pile_3"),
            ),
            activeTossIn = ActiveTossIn(
                ranks = listOf(Rank.THREE),
                initiatorId = "human-1",
                originalPlayerIndex = 0,
                participants = emptyList(),
                queuedActions = emptyList(),
                waitingForInput = false,
                playersReadyForNextTurn = listOf("bot-1", "bot-2", "bot-3"),
            ),
        )

        /** A final round nobody can play on, one END_ROUND away from being scored. */
        fun deadFinalRound(): GameState = table(
            phase = GamePhase.FINAL,
            subPhase = GameSubPhase.IDLE,
            players = listOf(
                seat("human-1", listOf(card(Rank.FIVE, "end_h_0")), caller = true),
                seat("bot-1", listOf(card(Rank.KING, "end_b1_0"))),
                seat("bot-2", listOf(card(Rank.TWO, "end_b2_0"))),
                seat("bot-3", listOf(card(Rank.ACE, "end_b3_0"))),
            ),
            drawPile = emptyList(),
            discardPile = listOf(card(Rank.TWO, "end_pile_top")),
            vintoCallerId = "human-1",
        )
    }
}
