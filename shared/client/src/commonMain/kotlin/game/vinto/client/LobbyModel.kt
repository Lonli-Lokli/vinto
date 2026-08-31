package game.vinto.client

import game.vinto.protocol.LobbyView
import game.vinto.protocol.RoomPhase

/**
 * The lobby, digested for a screen — the same shape of split `tableFor` gives the table:
 * every decision lives here as a pure, tested function, and the composables above draw what
 * they are told.
 */
data class LobbyUi(
    val seats: List<LobbySeatUi>,
    /** Whether there is anything left to try: set only when the room was never reached. */
    val canRetry: Boolean,
    /** Whether the add-a-bot button does anything: a free seat, and a live connection. */
    val canAddBot: Boolean,
    /** Milliseconds to the deal, while a countdown is running. Null otherwise. */
    val msUntilStart: Double?,
    /** The one line under the seats saying what everybody is waiting for. */
    val word: LobbyWord,
)

data class LobbySeatUi(
    val index: Int,
    val occupied: Boolean,
    val isBot: Boolean,
    /** True only for a filler bot, which is the only kind with a remove control. */
    val removable: Boolean,
    val nickname: String?,
    val isMine: Boolean,
)

/** What the lobby is waiting for, as a word the screen maps to a sentence. */
enum class LobbyWord {
    /** No lobby yet: the socket is still on its way, first time or again. */
    CONNECTING,

    /** Seats are open and one human is not a game: somebody else has to join. */
    NEEDS_ANOTHER_HUMAN,

    /** Two humans are in; the empty seats want people or bots. */
    FILL_THE_SEATS,

    /** Everything is set and the countdown is running. */
    COUNTING_DOWN,

    /**
     * Nobody else came, so the room filled the seats itself and is about to deal.
     *
     * Distinct from [COUNTING_DOWN] because the two are the same state and a different event.
     * A countdown somebody started by tapping "add a bot" needs no explanation — they did it,
     * a second ago, and they are looking at the screen. This one arrives unprompted after five
     * minutes of waiting, at somebody who may have put the phone down, and a countdown that
     * does not say where it came from reads as the room deciding on their behalf rather than
     * offering. It is an offer: taking a bot back out cancels it.
     */
    OFFERED_BOTS,

    /** The room is gone — closed, ended, or left. */
    OVER,

    /**
     * It was never reached: a code nobody has, a service that is closed, a network that never
     * answered.
     *
     * Distinct from [OVER] because the two want opposite things from the player. A room that
     * ended is finished and the only move is to leave; a room that was never reached may
     * simply be on the other side of a lift, and the screen should offer another go. They read
     * identically in the state — both are `Closed` — until the trouble is carried with it.
     */
    UNREACHABLE,
}

/** Digests what the room reports into what the lobby screen draws. */
fun lobbyUi(lobby: LobbyView?, connection: ConnectionState, mySeat: Int?): LobbyUi {
    val seats = lobby?.seats?.map { seat ->
        LobbySeatUi(
            index = seat.index,
            occupied = seat.occupied,
            isBot = seat.isBot,
            removable = seat.removable,
            nickname = seat.nickname,
            isMine = seat.index == mySeat,
        )
    }.orEmpty()

    val connected = connection is ConnectionState.Connected
    val word = when {
        connection is ConnectionState.Closed && connection.trouble != null -> LobbyWord.UNREACHABLE
        connection is ConnectionState.Closed -> LobbyWord.OVER
        !connected || lobby == null -> LobbyWord.CONNECTING
        lobby.phase == RoomPhase.STARTING && lobby.botsOffered -> LobbyWord.OFFERED_BOTS
        lobby.phase == RoomPhase.STARTING -> LobbyWord.COUNTING_DOWN
        lobby.humans < MIN_HUMANS -> LobbyWord.NEEDS_ANOTHER_HUMAN
        else -> LobbyWord.FILL_THE_SEATS
    }

    return LobbyUi(
        seats = seats,
        canRetry = word == LobbyWord.UNREACHABLE,
        canAddBot = connected && seats.any { !it.occupied },
        msUntilStart = lobby?.msUntilStart
            .takeIf { word == LobbyWord.COUNTING_DOWN || word == LobbyWord.OFFERED_BOTS },
        word = word,
    )
}

/** The rule the room enforces (design R2a); repeated here only to word the lobby honestly. */
private const val MIN_HUMANS = 2
