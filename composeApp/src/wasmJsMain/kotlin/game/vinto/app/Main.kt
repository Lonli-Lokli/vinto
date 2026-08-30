package game.vinto.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import game.vinto.app.link.offerOpenedLink
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // An invite opened in a browser is a path, not an intent: `vinto.kupalinka.app/r/7KQ2MP`
    // is a real page that Pages serves the SPA shell for, so the code arrives here and
    // nowhere else. Read before the first composition, so the table is what draws rather
    // than the home screen being replaced a frame later.
    offerOpenedLink(window.location.pathname)

    ComposeViewport(document.body!!) {
        App()
    }
}
