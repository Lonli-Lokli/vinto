package game.vinto.client

import game.vinto.engine.ActionValidator
import game.vinto.engine.Validation
import game.vinto.engine.createDeck
import game.vinto.engine.initializeTeachingGame
import game.vinto.shapes.Difficulty
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.ParticipateInTossInPayload
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.random.Random

/**
 * The deal every player is taught on.
 *
 * A lesson needs particular cards in particular places: something to peek at that pays off
 * later, a rank the player knowingly holds when a matching card goes down, an unused action
 * card left on the pile so taking from the discard can be shown, a Queen and a King to play,
 * an Ace sitting in somebody's hand to be found, and a Joker arriving on the last turn so the
 * round ends on the player's own good decision.
 *
 * None of that survives a shuffle, and none of it is worth searching a seed for — the
 * constraints are joint over a dozen named positions, and a seed that satisfied them today
 * would be quietly invalidated by the next engine or bot change. So the order is written down.
 * It is still a **real deck**: `initializeTeachingGame` refuses anything that is not a
 * permutation of the game's own 54 cards, so this cannot deal a hand that could not have been
 * shuffled.
 *
 * Positions 0–4 go to the player, 5–9 to Raph, 10–14 to Mikey, 15–19 to Don, and the rest is
 * the draw pile with position 20 on top.
 */
internal object TeachingDeal {

    /** The player's hand: a 7 and a Joker to peek at, an 8 to throw in later. */
    private val YOURS = listOf(Rank.THREE, Rank.SEVEN, Rank.EIGHT, Rank.JOKER, Rank.FIVE)

    /** Raph keeps a 3 he knows about, for the moment a bot throws one in. */
    private val RAPH = listOf(Rank.TWO, Rank.THREE, Rank.ACE, Rank.KING, Rank.TWO)

    /** Mikey's King is what the Queen steals. */
    private val MIKEY = listOf(Rank.KING, Rank.SIX, Rank.SIX, Rank.TEN, Rank.FOUR)

    /** Don's Ace is found with a 9 and then declared by a King. */
    private val DON = listOf(Rank.KING, Rank.ACE, Rank.TWO, Rank.TWO, Rank.SEVEN)

    /**
     * The top of the deck, in the order the lesson needs it.
     *
     * Your draws are the 4 (a plain card to keep and declare), the Queen, the King, and
     * finally the Joker in the final round. The bots' draws are the cards they put back down:
     * a 6 to watch, an 8 you can match, a 9 left unused on the pile, and fillers.
     */
    private val TOP = listOf(
        Rank.FOUR, Rank.SIX, Rank.EIGHT, Rank.NINE,
        Rank.FIVE, Rank.NINE, Rank.NINE, Rank.QUEEN,
        Rank.SIX, Rank.FIVE, Rank.THREE, Rank.KING,
        Rank.ACE, Rank.FOUR, Rank.TEN, Rank.THREE,
        Rank.JOKER,
    )

    /**
     * The whole deck, in deal order.
     *
     * Built by taking the named ranks out of a real deck one at a time and letting whatever is
     * left fall in behind them, which is what makes it a permutation by construction rather
     * than by hope. `TeachingDealTest` asserts it anyway — a stacked deck that is not a legal
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
 * @param callVintoFromTurn which turn the director may have a bot call Vinto on. Late enough
 *   that the player has met a card's action and a toss-in window; early enough that the final
 *   round is reached while they are still paying attention.
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
 * The turn a bot calls Vinto on.
 *
 * `turnNumber` counts *turns*, not rotations — with four seats, the player takes turns 1, 5
 * and 9, and Don takes 4, 8 and 12. Twelve is therefore Don's third turn, by which point the
 * player has had three of their own: long enough to have drawn, kept, declared, played an
 * action and answered a toss-in window, and short enough that the ending arrives while all of
 * that is still fresh.
 *
 * The first version of this said 4, which is Don's *first* turn — the lesson called Vinto
 * before the player had taken a second one.
 */
private const val VINTO_ON_TURN = 12

/**
 * Somebody deciding the bots' moves in place of the search.
 *
 * The deck says what a bot *draws*; it cannot say what a bot *does*, and the lesson needs
 * both — a 9 left unused on the pile so taking from the discard can be shown, and, at the
 * end, somebody calling Vinto so the final round and the coalition can be played rather than
 * described.
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
 * Two jobs, and it keeps out of the way otherwise.
 *
 * **Bots put their cards down.** Left to themselves the bots would swap good cards into their
 * hands, which is correct play and ruins every lesson that depends on what is on the pile —
 * the 8 you are meant to match, the 9 you are meant to take. So a directed bot holding a card
 * it drew discards it.
 *
 * **Don calls Vinto.** A bot will not do it on its own inside a short round: the rule wants
 * eight full rotations, a hand it knows entirely, and a total of zero or less. Waiting for
 * that is waiting forever, and the final round is half the game. So once the lesson has run
 * its course, Don calls it — a legal, validated, recorded action, taken on his own turn like
 * anybody else's.
 */
internal class TeachingDirector(private val callVintoFromTurn: Int) : BotDirector {

    /** So the call happens once, at the first moment it is legal. */
    private var called = false

    /** So a bot throws a card in exactly once, the first time one can. */
    private var demonstrated = false

    override fun nextAction(state: GameState): GameAction? {
        // Don has called and the coalition needs a leader. Outside the lesson the bots hold
        // this choice open for the human; here the script keeps moving, so the director
        // nominates the first bot the way the runner used to.
        if (state.vintoCallerId != null && state.coalitionLeaderId == null) {
            state.players.firstOrNull { it.isBot && it.id != state.vintoCallerId }?.let {
                return GameAction.SetCoalitionLeader(game.vinto.shapes.LeaderIdPayload(it.id))
            }
        }

        // Before anything else, and not restricted to whoever's turn it is: a toss-in belongs
        // to the whole table, which is the point being made.
        tossInDemo(state)?.let { return it }

        val actor = state.players.getOrNull(state.currentPlayerIndex) ?: return null
        if (actor.isHuman) return null

        if (timeToCall(state)) {
            called = true
            return GameAction.CallVinto(PlayerIdPayload(actor.id))
        }

        val pending = state.pendingAction

        // The seat just before the player draws rather than takes.
        //
        // The lesson claims there are two ways to start a turn, and the second one — taking an
        // unused action card off the pile — can only be *shown* if there is one there when the
        // player's turn begins. Left to themselves the bots take it first, correctly: it is
        // free value. So the bot sitting immediately before the player is made to draw, and
        // what it then throws away is what the player is offered.
        if (pending == null && actor.id == seatBeforeThePlayer(state)) {
            return GameAction.DrawCard(PlayerIdPayload(actor.id))
        }

        if (pending == null || pending.playerId != actor.id) return null
        if (pending.from != PendingCardOrigin.DRAWING) return null

        // Down it goes, face up, where the lesson can point at it.
        return GameAction.DiscardCard(PlayerIdPayload(actor.id))
    }

    /**
     * Whoever plays immediately before the person being taught.
     *
     * Found by seat order rather than hard-coded, so it stays right if the deal ever puts the
     * player somewhere other than seat zero.
     */
    private fun seatBeforeThePlayer(state: GameState): String? {
        val player = state.players.indexOfFirst { it.isHuman }.takeIf { it >= 0 } ?: return null
        val before = (player - 1 + state.players.size) % state.players.size
        return state.players[before].id
    }

    /**
     * A bot throwing in a match, once.
     *
     * The toss-in window is the one moment in Vinto that belongs to everybody at once, and a
     * player whose window only ever contains themselves learns it as "a prompt I dismiss". So
     * the first time a bot is holding a card it *knows* matches, it throws it in where the
     * player can watch it happen.
     *
     * "Knows" is the bot's own rule, not a convenience: guessing costs a penalty card and bars
     * you from the rest of the round, so a bot that tossed a card it had not seen would be
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
}
