package game.vinto.client

/**
 * The plan's transport: one page per turn, a last page where the plan lands, and the film that
 * runs between them (design D14, redrawn as a pager).
 *
 * **A pager, not a scrubber.** The rail shows one turn at a time — swipe, or touch a stop, and
 * the next turn is there. Every turn is a page whether or not anybody has decided it, so ②
 * means the same turn to every member reading it (design D6), and the page after the last turn
 * is where the plan lands: every hand as the turns leave it, and the totals as the coalition
 * believes them. A stop is a **jump**, in either direction — cards do not fly for a swipe. What
 * plays the film is the pair of buttons beside the stops: this turn again, from the table it
 * starts on; or every turn from here to where the plan lands.
 *
 * Every control is a [Move.Quiet] over the screen's own [Question.ThePlan] — nothing here
 * travels, and nothing here can reach the engine.
 */
data class Transport(
    /** The page: 1..[turns] are the turns, [pages] is where the plan lands. */
    val at: Int,
    /** How many turns the plan has. */
    val turns: Int,
    /**
     * The page the film is travelling to, or null when the head is parked.
     *
     * The screen needs the destination as well as the fact of moving: it plays the frames
     * between [at] and here, and parks the head on arrival. See `PlanRunner`.
     */
    val travellingTo: Int?,
    /** Every page, in order: one per turn, then where the plan lands. */
    val stops: List<Stop>,
    /** Stop, and come to rest on the page reached. Null when it is not running. */
    val halt: Move.Quiet?,
    /**
     * Watch every turn from here to where the plan lands. Null while it runs, and for a plan
     * with nothing to watch.
     */
    val playAll: Move.Quiet? = null,
) {
    /** Whether it is running. Editing sleeps while it is. */
    val running: Boolean get() = travellingTo != null

    /** How many pages there are: the turns, and the one where the plan lands. */
    val pages: Int get() = turns + 1

    /** True on the last page, where the plan's arrival is read (design D10). */
    val arrived: Boolean get() = turns > 0 && at == pages
}

/**
 * One page the transport can be sent to.
 *
 * @param at the page: 1 is the first turn; `turns + 1` is where the plan lands.
 * @param seat whose turn this page is, or null for the last page — which is nobody's turn and
 *   is labelled as where the plan lands rather than as a player.
 * @param here whether this is the page on screen.
 * @param go where to press to come here. Null on the page already on screen, and null on every
 *   page while the transport is running — a film that could be redirected mid-flight would
 *   leave cards travelling to a page nobody is watching for.
 * @param nickname who the seat *is*, for the face the stop wears — a portrait is keyed on the
 *   nickname, and [seat] says only how to address them. Null on the last page.
 * @param replay watch this one turn again, from the table it starts on. Null on the last page,
 *   on a turn with nothing to watch, and while the film runs.
 */
data class Stop(
    val at: Int,
    val seat: Speaker?,
    val here: Boolean,
    val go: Move.Quiet?,
    val nickname: String? = null,
    val replay: Move.Quiet? = null,
)

/**
 * The transport for [focus] over the coalition's [seats], in turn order. See [Transport].
 *
 * @param drawable one per turn: whether that turn has anything to *watch*. A turn nobody has
 *   decided still has a page — it is somebody's turn either way — but there is nothing to play
 *   on the way to it.
 */
internal fun transportFor(
    focus: Question.ThePlan,
    seats: List<Speaker?>,
    drawable: List<Boolean> = List(seats.size) { true },
    names: List<String?> = List(seats.size) { null },
): Transport {
    val turns = seats.size
    val pages = turns + 1
    // Padded rather than trusted: a caller whose flags are shorter than the coalition is a
    // caller whose plan has fewer decisions than turns, and that is the ordinary case.
    val watchable = List(turns) { drawable.getOrElse(it) { false } }
    val at = focus.at.coerceIn(1, pages)
    val running = focus.running

    // A jump, forwards or back: the felt shows the table the page's turn starts from, and no
    // card flies. The film is the two buttons' business.
    fun goTo(target: Int): Move.Quiet? = when {
        running -> null
        target == at -> null
        else -> Move.Ask(focus.copy(at = target, picked = null, runningTo = null, landed = false))
    }

    // One turn again: the head goes back to the table the turn starts on and runs to its end.
    fun replay(page: Int): Move.Quiet? =
        if (running || page > turns || !watchable[page - 1]) {
            null
        } else {
            Move.Ask(focus.copy(at = page, picked = null, runningTo = page, landed = false))
        }

    // From the page on screen to where the plan lands; from the last page, the whole plan again.
    val from = if (at > turns) 1 else at
    val playAll = Move.Ask(focus.copy(at = from, picked = null, runningTo = pages, landed = false))
        .takeIf { !running && watchable.drop(from - 1).any { it } }

    return Transport(
        at = at,
        turns = turns,
        travellingTo = focus.runningTo,
        stops = (1..pages).map { page ->
            Stop(
                at = page,
                seat = seats.getOrNull(page - 1),
                here = page == at,
                go = goTo(page),
                nickname = names.getOrNull(page - 1),
                replay = replay(page),
            )
        },
        // Halting parks the head where it already is, on the result of whatever has played.
        halt = Move.Ask(focus.copy(picked = null, runningTo = null, landed = true)).takeIf { running },
        playAll = playAll,
    )
}
