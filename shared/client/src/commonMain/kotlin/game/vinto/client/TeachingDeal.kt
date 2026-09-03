package game.vinto.client

import game.vinto.engine.ActionValidator
import game.vinto.engine.Validation
import game.vinto.engine.createDeck
import game.vinto.engine.initializeTeachingGame
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.DeclareKingActionPayload
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.LeaderIdPayload
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PendingAction
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank
import game.vinto.shapes.SelectActionTargetPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.random.Random

/**
 * The deal every player is taught on — a round planned move by move.
 *
 * The lesson is eight turns long and every one of them has a job, which is what the product
 * owner asked for after playing the first version: a round in which the learner *does* each
 * thing the game has once, watches the bots do the rest, and ends it by calling Vinto on a
 * hand nothing can beat. In order:
 *
 *  1. **You** draw a 2 and swap it for the 8 you peeked, naming the 8 as it goes down — its
 *     look is yours for free, and it finds your Joker.
 *  2. **Raph** draws a 7 and puts it down; you throw in the 7 you peeked, and so does he, and
 *     your 7's look finds your first King.
 *  3. **Mikey** draws a 9 and plays it: he looks at one of your cards.
 *  4. **Don** draws a Queen and throws it away unplayed, which leaves it on the pile for you.
 *  5. **You** take the Queen off the pile — the second way to start a turn — look at your
 *     one unread card, a King, and at Raph's first, his Joker, and trade. Every card in your
 *     hand is now one you have seen, they add up to nothing, and both Jokers are yours: you
 *     call, and nothing at the table can finish below you.
 *  6. **Raph** draws an Ace and plays it: Mikey draws a penalty card.
 *  7. **Mikey** draws a King and plays it, naming one of his own 6s, which leaves his hand.
 *  8. **Don** draws a 9 and looks at one of Raph's cards. Then every hand goes face up.
 *
 * The arithmetic of the looks is the constraint that shaped the hand. A learner gets five
 * looks at their own cards before the call — two at the setup, one from the 8 they name, one
 * from the 7 they throw in, one from the Queen — and the coach only says "call" on a hand
 * every card of which has been seen. A 2 swapped in counts as seen; a 7 thrown in is a slot
 * gone. That is five looks for five slots, exactly, which is why the 7 and the 8 are the two
 * cards the peeks find and why the drawn card is a 2 rather than anything the 8's look could
 * have been better spent on.
 *
 * None of that survives a shuffle, and none of it is worth searching a seed for — the
 * constraints are joint over a dozen named positions, and a seed that satisfied them today
 * would be quietly invalidated by the next engine or bot change. So the order is written down.
 * It is still a **real deck**: `initializeTeachingGame` refuses anything that is not a
 * permutation of the game's own 54 cards, so this cannot deal a hand that could not have been
 * shuffled.
 *
 * Positions 0–4 go to the player, 5–9 to Raph, 10–14 to Mikey, 15–19 to Don, and the rest is
 * the draw pile with position 20 on top. The bots know their first two cards, as the deal
 * gives every seat; the player peeks where the coach points, which is the first two.
 */
internal object TeachingDeal {

    /**
     * The player's hand. A 7 and an 8 where the coach points the two peeks — the 8 to give
     * up for the drawn 2 and name, the 7 to throw in on Raph's; a Joker for the 8's look to
     * find; a King for the 7's; and a second King, the one card left unread, for the Queen
     * to read and trade for Raph's Joker. The finished hand is a 2, two Jokers and a King:
     * nothing, which no hand at the table can get under.
     */
    private val YOURS = listOf(Rank.SEVEN, Rank.EIGHT, Rank.JOKER, Rank.KING, Rank.KING)

    /** Raph knows his Joker and his 7: the 7 he throws in beside yours, the Joker your Queen takes. */
    private val RAPH = listOf(Rank.JOKER, Rank.SEVEN, Rank.TWO, Rank.THREE, Rank.TEN)

    /** Mikey knows two 6s, so his King in the final round has a card it can safely name. */
    private val MIKEY = listOf(Rank.SIX, Rank.SIX, Rank.KING, Rank.TEN, Rank.FOUR)

    /** Don holds nothing the script needs — and nothing that would let him throw in early. */
    private val DON = listOf(Rank.TWO, Rank.THREE, Rank.FIVE, Rank.TWO, Rank.SIX)

    /**
     * The top of the deck, one card per draw in the order above: your 2, Raph's 7, Mikey's
     * 9, Don's Queen; then the final round — Raph's Ace, the 5 it makes Mikey draw, Mikey's
     * King, Don's 9. Whatever is left falls in behind, for a learner who plays on instead of
     * calling.
     */
    private val TOP = listOf(
        Rank.TWO,
        Rank.SEVEN,
        Rank.NINE,
        Rank.QUEEN,
        Rank.ACE,
        Rank.FIVE,
        Rank.KING,
        Rank.NINE,
    )

    /**
     * The whole deck, in deal order.
     *
     * Built by taking the named ranks out of a real deck one at a time and letting whatever is
     * left fall in behind them, which is what makes it a permutation by construction rather
     * than by hope. `TeachingRoundTest` asserts it anyway — a stacked deck that is not a legal
     * deck is a silent rules change.
     */
    fun deck(): List<Card> {
        val remaining = createDeck().toMutableList()

        fun take(rank: Rank): Card {
            val index = remaining.indexOfFirst { it.rank == rank }
            require(index >= 0) { "the teaching deal wants a $rank the deck does not have" }
            return remaining.removeAt(index)
        }

        val ordered = (YOURS + RAPH + MIKEY + DON + TOP).map(::take)
        return ordered + remaining
    }
}

/**
 * The round the lesson is played on.
 *
 * A real `LocalGameSession` in every respect — same engine, same validator, same seat
 * boundary, same recorder — dealt from the written-down deck and with a director whispering to
 * the bots. It saves nothing: opening the lesson must not take somebody's half-played game
 * away.
 *
 * @param callVintoFromTurn the turn from which the director may have a bot call Vinto, for a
 *   learner who did not call it themselves. Late enough that they have had every chance.
 */
fun teachingSession(
    botDispatcher: CoroutineDispatcher? = null,
    callVintoFromTurn: Int = VINTO_ON_TURN,
): LocalGameSession = LocalGameSession(
    seed = TEACHING_SEED,
    difficulty = Difficulty.EASY,
    botDispatcher = botDispatcher,
    random = Random(TEACHING_SEED),
    dealt = initializeTeachingGame(TeachingDeal.deck(), Difficulty.EASY),
    director = TeachingDirector(callVintoFromTurn),
)

/** Only used for the bots' own randomness; the cards come from the deck above. */
private const val TEACHING_SEED = 20_260_820L

/**
 * The turn a bot calls Vinto on, if the player has not.
 *
 * The lesson wants the *player* to call, at the end of their second turn, on a hand of a 2,
 * two Jokers and a King. A learner who plays on instead is not wrong, and the round must still
 * end while they are paying attention: `turnNumber` counts turns, not rotations — with four
 * seats the player takes 1, 5 and 9, and Don takes 4, 8 and 12 — so twelve is Don's third
 * turn, after the player has had a third of their own.
 */
private const val VINTO_ON_TURN = 12

/**
 * Somebody deciding the bots' moves in place of the search.
 *
 * The deck says what a bot *draws*; it cannot say what a bot *does*, and the lesson needs
 * both — a Queen left unplayed on the pile so taking from the discard can be shown, a 9 and a
 * King and an Ace actually played so the learner sees what they do, and, if it comes to it,
 * somebody calling Vinto so the round ends.
 *
 * A director returns an action for the state in front of it, or null to let the real bot
 * think. Whatever it returns still goes through `ActionValidator` exactly as an MCTS move
 * does, and a refused move falls through to the bot rather than stalling the game — a script
 * that has drifted out of date should cost the lesson its shape, not its playability.
 *
 * There is deliberately no way for a *screen* to do this. The seat boundary in
 * `LocalGameSession.dispatch` is a rule worth keeping whole; a tutorial that acted for the
 * bots through the front door would be teaching the UI a habit that fails the moment there is
 * a server.
 */
fun interface BotDirector {
    fun nextAction(state: GameState): GameAction?
}

/**
 * The director for the lesson.
 *
 * **Bots draw, and then play or put down.** A directed bot never takes from the pile — the
 * deck is written down card by card, and it only lands if every bot turn consumes exactly one
 * card. What it drew it *plays* if it is a 9, a King or an Ace, each aimed by the script so the
 * learner watches the three cards they have not held do their work; anything else goes face
 * up on the pile, which is how the Queen the player is meant to take gets there. The King is
 * aimed at a plain card the bot knows it holds and names correctly — a bot guessing would be
 * demonstrating bad play, and a wrong guess draws a penalty card that shifts every scripted
 * position after it.
 *
 * **Don calls Vinto**, late, if the player has not. A bot will not do it on its own inside a
 * short round: the rule wants eight full rotations, a hand it knows entirely, and a total of
 * zero or less.
 */
internal class TeachingDirector(private val callVintoFromTurn: Int) : BotDirector {

    /** So the call happens once, at the first moment it is legal. */
    private var called = false

    /** So a bot throws a card in exactly once, the first time one can. */
    private var demonstrated = false

    override fun nextAction(state: GameState): GameAction? {
        // Somebody has called and the coalition needs a leader. Outside the lesson the bots
        // hold this choice open for the human; here the script keeps moving, so the director
        // nominates the first bot the way the runner used to.
        if (state.vintoCallerId != null && state.coalitionLeaderId == null) {
            state.players.firstOrNull { it.isBot && it.id != state.vintoCallerId }?.let {
                return GameAction.SetCoalitionLeader(LeaderIdPayload(it.id))
            }
        }

        // Before anything else, and not restricted to whoever's turn it is: a toss-in belongs
        // to the whole table, which is the point being made.
        tossInDemo(state)?.let { return it }

        // Every other bot window closes at once. The real bots toss on what they *believe*
        // they hold, and on the teaching difficulty a belief can be honestly wrong — a wrong
        // toss draws a penalty card, and one extra draw shifts every scripted deck position
        // after it. One demonstrated toss-in is the lesson; the rest is choreography.
        closeWindow(state)?.let { return it }

        val actor = state.players.getOrNull(state.currentPlayerIndex) ?: return null
        if (actor.isHuman) return null

        if (timeToCall(state)) {
            called = true
            return GameAction.CallVinto(PlayerIdPayload(actor.id))
        }

        val pending = state.pendingAction
            ?: return GameAction.DrawCard(PlayerIdPayload(actor.id))
        if (pending.playerId != actor.id || pending.from != PendingCardOrigin.DRAWING) return null

        return when (pending.actionPhase) {
            ActionPhase.CHOOSING_ACTION ->
                if (worthPlaying(state, actor, pending.card.rank)) {
                    GameAction.UseCardAction(PlayerIdPayload(actor.id))
                } else {
                    // Down it goes, face up, where the lesson can point at it.
                    GameAction.DiscardCard(PlayerIdPayload(actor.id))
                }

            ActionPhase.SELECTING_TARGET -> aim(state, actor, pending)
        }
    }

    private fun closeWindow(state: GameState): GameAction? {
        if (state.subPhase != GameSubPhase.TOSS_QUEUE_ACTIVE) return null
        val ready = state.activeTossIn?.playersReadyForNextTurn.orEmpty()
        for (bot in state.players.filter { it.isBot && it.id !in ready }) {
            val done = GameAction.PlayerTossInFinished(PlayerIdPayload(bot.id))
            if (ActionValidator.validate(state, done) is Validation.Valid) return done
        }
        return null
    }

    /**
     * The three cards a bot plays rather than puts down, when the script can aim them.
     *
     * A 9 needs somebody to look at, an Ace somebody to make draw, and a King a card the bot
     * knows well enough to name — without one of those the card is put down like any other,
     * which is what keeps a learner who wanders off the line on a playable table.
     */
    private fun worthPlaying(state: GameState, actor: PlayerState, rank: Rank): Boolean = when (rank) {
        Rank.NINE, Rank.TEN -> watched(state, actor) != null
        Rank.ACE -> victim(state, actor) != null
        Rank.KING -> plainKnown(actor) != null
        else -> false
    }

    /** The scripted aim for a card the bot has chosen to play. */
    private fun aim(state: GameState, actor: PlayerState, pending: PendingAction): GameAction? {
        val me = actor.id
        val first = pending.targets.firstOrNull()
        return when (pending.card.rank) {
            Rank.NINE, Rank.TEN ->
                if (first == null) {
                    val seat = watched(state, actor) ?: return null
                    GameAction.SelectActionTarget(SelectActionTargetPayload.Positional(me, seat.id, 0))
                } else {
                    GameAction.ConfirmPeek(PlayerIdPayload(me))
                }

            Rank.KING ->
                if (first == null) {
                    val position = plainKnown(actor) ?: return null
                    GameAction.SelectActionTarget(SelectActionTargetPayload.Positional(me, me, position))
                } else {
                    val rank = actor.cards.getOrNull(first.position)?.rank ?: return null
                    GameAction.DeclareKingAction(DeclareKingActionPayload(me, rank))
                }

            Rank.ACE ->
                if (first == null) {
                    val seat = victim(state, actor) ?: return null
                    GameAction.SelectActionTarget(SelectActionTargetPayload.Ace(me, seat.id))
                } else {
                    null
                }

            else -> null
        }
    }

    /**
     * Whose card a bot's 9 looks at: the learner's, so they watch it happen to them — unless
     * the learner has called, when the rules put their cards out of reach and the look goes
     * to the next seat round.
     */
    private fun watched(state: GameState, actor: PlayerState): PlayerState? {
        val human = state.players.firstOrNull { it.isHuman }
        if (human != null && human.id != state.vintoCallerId) return human
        return nextSeat(state, actor)
    }

    /** Who a bot's Ace makes draw: the next seat round that the rules allow. */
    private fun victim(state: GameState, actor: PlayerState): PlayerState? = nextSeat(state, actor)

    /** The next seat after [actor] that is a bot and not the caller. */
    private fun nextSeat(state: GameState, actor: PlayerState): PlayerState? {
        val from = state.players.indexOfFirst { it.id == actor.id }.takeIf { it >= 0 } ?: return null
        val count = state.players.size
        return (1 until count)
            .map { state.players[(from + it) % count] }
            .firstOrNull { it.isBot && it.id != state.vintoCallerId }
    }

    /** A plain card the bot knows it holds — one a King can name with nothing to play after. */
    private fun plainKnown(actor: PlayerState): Int? =
        actor.knownCardPositions.firstOrNull { actor.cards.getOrNull(it)?.rank in PLAIN }

    /**
     * A bot throwing in a match, once.
     *
     * The toss-in window is the one moment in Vinto that belongs to everybody at once, and a
     * player whose window only ever contains themselves learns it as "a prompt I dismiss". So
     * the first time a bot is holding a card it *knows* matches, it throws it in where the
     * player can watch it happen.
     *
     * "Knows" is the bot's own rule, not a convenience: guessing costs a penalty card and shuts
     * you out of the card you guessed at, so a bot that tossed a card it had not seen would be
     * demonstrating bad play rather than the rule.
     *
     * The move is validated here rather than hoped about, because the flag must only be spent
     * on a toss-in that actually happens.
     */
    private fun tossInDemo(state: GameState): GameAction? {
        if (demonstrated) return null
        if (state.subPhase != GameSubPhase.TOSS_QUEUE_ACTIVE) return null

        val wanted = state.activeTossIn?.ranks?.toSet() ?: return null

        for (bot in state.players.filter { it.isBot }) {
            val position = bot.knownCardPositions.firstOrNull { at ->
                bot.cards.getOrNull(at)?.rank in wanted
            } ?: continue

            val toss = GameAction.ParticipateInTossIn(
                ParticipateInTossInPayload(bot.id, listOf(position)),
            )
            if (ActionValidator.validate(state, toss) !is Validation.Valid) continue

            demonstrated = true
            return toss
        }
        return null
    }

    /**
     * Whether the round has taught what it can and should now end.
     *
     * Between turns rather than in the middle of one: a call is legal on the caller's own turn
     * with nothing in play, which is exactly the moment a person would make it.
     */
    private fun timeToCall(state: GameState): Boolean {
        if (called || state.phase != GamePhase.PLAYING) return false
        if (state.turnNumber < callVintoFromTurn) return false

        return state.vintoCallerId == null && state.pendingAction == null
    }

    private companion object {
        val PLAIN = setOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX)
    }
}
