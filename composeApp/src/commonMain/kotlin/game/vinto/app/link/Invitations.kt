package game.vinto.app.link

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState

/**
 * Invitations that arrive while the app is **already running**.
 *
 * The startup effect reads [takeOpenedLink] once, on the way in, which is right for the launch
 * that a link caused. It is the whole story only if a link never arrives afterwards — and the
 * commonest one does: a person taps a second invitation, or reads back the QR of the room they
 * just shared, and the platform brings the app forward rather than starting it. Android's
 * `onNewIntent` and iOS's `HandleOpenedLink` were both written for exactly that moment, and
 * both handed the link to a `var` nobody was reading. Nothing happened, which looks from the
 * outside like a broken link.
 *
 * A composable rather than a call, because the answer depends on where the app *is*: [atRoom]
 * is read at the moment a link lands, not when this was composed. [ready] holds it until the
 * startup effect has had its turn — the caller says so by having left its opening screen — so
 * one link cannot be honoured twice.
 *
 * A room being left this way is *stepped out of* rather than given up — the caller closes the
 * socket and the seat token still leads back to it, which is what `backedOutOf` does too.
 */
@Composable
internal fun Invitations(ready: Boolean, atRoom: () -> String?, onInvited: (String) -> Unit) {
    val here = rememberUpdatedState(atRoom)
    val act = rememberUpdatedState(onInvited)

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        openedLink.collect { pending ->
            if (pending == null) return@collect
            // Taken either way. A link for the room we are already in is *answered* — by
            // staying put — rather than left lying about to be honoured by some later launch.
            val code = invitationFrom(takeOpenedLink(), here.value())
            if (code != null) act.value(code)
        }
    }
}
