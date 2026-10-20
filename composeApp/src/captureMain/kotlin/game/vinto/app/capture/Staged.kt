// Capture code (kupalinka-capture-a47d267ea8c1795fbb7df224): only in the capture build,
// gulnya/claude/capture-build.md
package game.vinto.app.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import game.vinto.app.FirstScreen
import game.vinto.client.Question
import game.vinto.client.Vault

/**
 * The app on the scene [handle] names ([MarketingScene]), or as a player opens it when it names
 * none, or none that exists.
 *
 * [app] is `App` with whatever else its caller passes, handed the first screen to open on: the
 * capture build's entries (`CaptureActivity`, `CaptureViewController`) pass nothing else, the
 * desktop's tests a seed, a vault and the edges a device keeps for itself.
 *
 * A filmed scene's moving part plays here, beside the app rather than inside it, from the
 * composition that puts its table up: a store build has no table that moves on its own. Keyed on
 * the film, so a rebuild does not start a second loop against the same table.
 */
@Composable
fun Staged(handle: String?, app: @Composable (firstScreen: (suspend (Vault) -> FirstScreen)?) -> Unit) {
    var filmed by remember { mutableStateOf<Filmed?>(null) }
    val firstScreen = remember(handle) { MarketingScene.named(handle)?.opener { filmed = it } }
    app(firstScreen)
    filmed?.let { film -> LaunchedEffect(film) { film.live(film.game) } }
}

/** The first screen this scene stages, as `App` asks for it. */
private fun MarketingScene.opener(film: (Filmed) -> Unit): suspend (Vault) -> FirstScreen =
    { vault -> firstScreen(vault, film) }

/**
 * Where each scene opens.
 *
 * Two are screens and cost nothing; most are a staged round at some moment; the lobby is
 * deliberately not staged at all. [MarketingScene] says why for each. A filmed scene hands its
 * moving part to [film].
 */
internal suspend fun MarketingScene.firstScreen(vault: Vault, film: (Filmed) -> Unit): FirstScreen =
    when (this) {
        // `canContinue` is asserted rather than read: the fuller menu is the one worth
        // photographing, and a capture machine has no saved game.
        MarketingScene.HOME -> FirstScreen.Home(canContinue = true)
        MarketingScene.TEACH -> FirstScreen.Lesson
        MarketingScene.TABLE -> FirstScreen.Table(stagedGame(vault, toTheEnd = false))
        MarketingScene.SCORE -> FirstScreen.Table(stagedGame(vault, toTheEnd = true))
        MarketingScene.LOBBY -> FirstScreen.Online.also { stageTheLobby(vault) }
        MarketingScene.PLAN -> FirstScreen.Table(coalitionGame(vault), question = Question.ThePlan())
        MarketingScene.DEMO -> filmed(Filmed(demoGame(vault), ::playOn), film)
        MarketingScene.KING -> filmed(kingFilm(vault), film)
        MarketingScene.QUEEN -> filmed(queenFilm(vault), film)
        MarketingScene.CALL -> filmed(callFilm(vault), film)
    }

private fun filmed(scene: Filmed, film: (Filmed) -> Unit): FirstScreen =
    FirstScreen.Table(scene.game).also { film(scene) }
