package game.vinto.app

import androidx.compose.runtime.Composable
import game.vinto.app.art.Res
import game.vinto.app.art.trouble_broken
import game.vinto.app.art.trouble_busy
import game.vinto.app.art.trouble_closed
import game.vinto.app.art.trouble_no_such_room
import game.vinto.app.art.trouble_offline
import game.vinto.app.art.trouble_refused
import game.vinto.client.RoomTrouble
import org.jetbrains.compose.resources.stringResource

/**
 * What went wrong with the room service, in a sentence a player can act on.
 *
 * The screens used to show whatever string came back — a platform exception's `message`, or
 * the service's own words. Both are written for a developer. The deployed service's refusal
 * while online play is shut reads *"server-side action validation is not implemented yet (see
 * ActionValidator, task 4.4)"*, which is true, is addressed to somebody who works on this, and
 * tells a player nothing about what to do next.
 *
 * So the **trouble** picks the sentence and the service's words become a detail underneath. It
 * is a `when` with no `else`, so a seventh trouble is a compile error here rather than a screen
 * that silently says nothing.
 */
@Composable
fun troubled(trouble: RoomTrouble): String = stringResource(
    when (trouble) {
        RoomTrouble.OFFLINE -> Res.string.trouble_offline
        RoomTrouble.NO_SUCH_ROOM -> Res.string.trouble_no_such_room
        RoomTrouble.CLOSED -> Res.string.trouble_closed
        RoomTrouble.BUSY -> Res.string.trouble_busy
        RoomTrouble.REFUSED -> Res.string.trouble_refused
        RoomTrouble.BROKEN -> Res.string.trouble_broken
    },
)
