package game.vinto.client

import game.vinto.shapes.GameAction

/**
 * How long the table waits, and why.
 *
 * A card game between people is not a sequence of moves; it is a sequence of *pauses* with
 * moves in them. Somebody picks up a card, looks at it, thinks, decides, and puts it down —
 * and the thinking is what tells everybody else that a decision was being made. Three bots
 * that resolve a whole round of turns in the time it takes to blink have played a correct
 * game that nobody watched.
 *
 * The shape is the web client's, which was tuned by being played rather than reasoned about
 * (`legacy-web/packages/local-client/src/lib/adapters/botAIAdapter.ts`: a second before acting, three
 * before deciding what to do with a drawn card, longer again in the final round). The numbers
 * are shorter here because this client shows each move *as it animates* rather than jumping
 * to the end and narrating afterwards, so a pause no longer has to cover a card that has
 * already moved.
 *
 * Everything here is scaled by the player's pace setting, so calm and brisk stretch and
 * compress the pauses and the movements together — a table that sped its movements up but not
 * its pauses would read as jerky rather than as quick.
 */
object Pacing {

    /** Somebody else's turn arriving: the beat where a person would be thinking. */
    const val THINK_MS = 650L

    /**
     * After somebody else has put something on the table worth reading.
     *
     * A drawn card is the most informative moment of another player's turn — face up, theirs
     * for exactly one decision, and what they do with it is the tell. This is the pause that
     * makes it readable.
     */
    const val READ_MS = 1_200L

    /** After anything else: enough to separate two moves, not enough to wait on. */
    const val BEAT_MS = 320L

    /** Between two scenes of one move — a King declaring a rank, then doing its work. */
    const val BETWEEN_SCENES_MS = 140L

    /**
     * The pause before a frame is played.
     *
     * Only for somebody else's move, and only when the turn has changed hands: your own moves
     * are answered immediately, because you know what you decided and a delay there is lag
     * rather than pacing.
     */
    fun thinkBefore(frame: Frame, previousActor: String?, viewerId: String): Long = when {
        !frame.hasSomethingToSee -> 0L
        frame.actorId == null -> 0L
        frame.actorId == viewerId -> 0L
        frame.actorId == previousActor -> 0L
        else -> THINK_MS
    }

    /**
     * The pause after a frame has played.
     *
     * Long when somebody else has revealed something, short otherwise. Your own moves never
     * get the long one: you are the one who made it, and the table should answer you rather
     * than keep you waiting to be told what you already know.
     */
    fun dwellAfter(frame: Frame, viewerId: String): Long = when {
        !frame.hasSomethingToSee -> 0L
        frame.actorId == viewerId -> BEAT_MS
        frame.action.isWorthReading() -> READ_MS
        else -> BEAT_MS
    }

    /**
     * Whether a move puts something on the table the others have to read.
     *
     * Drawing, playing a card's action, taking one off the pile, throwing one in, naming a
     * rank, calling Vinto — the moves that change what everybody knows. Aiming a Jack at two
     * cards is not among them: the interesting part is the swap that follows, and that has a
     * frame of its own.
     */
    private fun GameAction.isWorthReading(): Boolean = when (this) {
        is GameAction.DrawCard,
        is GameAction.PlayDiscard,
        is GameAction.UseCardAction,
        is GameAction.SwapCard,
        is GameAction.DiscardCard,
        is GameAction.ParticipateInTossIn,
        is GameAction.DeclareKingAction,
        is GameAction.CallVinto,
        -> true

        else -> false
    }
}
