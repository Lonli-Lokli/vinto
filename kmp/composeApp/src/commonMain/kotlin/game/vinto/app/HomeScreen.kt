package game.vinto.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.app_name
import game.vinto.app.art.card_back
import game.vinto.app.art.home_continue
import game.vinto.app.art.home_new_game
import game.vinto.app.art.home_online
import game.vinto.app.art.home_play
import game.vinto.app.art.home_settings
import game.vinto.app.art.home_solo_title
import game.vinto.app.art.home_tagline
import game.vinto.app.art.home_teach
import game.vinto.app.art.home_version
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.Rail
import game.vinto.app.theme.Wordmark
import game.vinto.app.theme.feltGradient
import game.vinto.app.theme.feltGold
import game.vinto.app.theme.onFelt
import game.vinto.client.Settings
import game.vinto.shapes.Difficulty
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val Pad = 20.dp
private val Gap = 12.dp
private val Tight = 6.dp
private val ColumnMax = 420.dp
private val PanelCorner = 12.dp

/**
 * Everywhere the home screen can send somebody.
 *
 * One parameter rather than five, because they arrive together and are read together: a
 * signature of five lambdas is one nobody can check the order of, and the compiler cannot
 * either — they are all `() -> Unit`.
 */
data class HomeActions(
    val continueGame: () -> Unit,
    val newGame: () -> Unit,
    val teach: () -> Unit,
    val online: () -> Unit,
    val settings: () -> Unit,
)

/**
 * Where a game starts, and everything that is not a game.
 *
 * On the felt, not on a page. This is the first thing anybody sees, and a settings screen in
 * front of a card table tells them what kind of thing they have opened before they have seen
 * the table. The deck itself does the introducing: five real cards from the game's own art,
 * dealt into a fan as the screen arrives, so the menu is made of the thing being played.
 *
 * One tap to a table is the rule the arrangement is built around — the mode everybody wants is
 * the one already open, and
 * the primary button under a thumb. Everything else is a row of quieter buttons beneath it.
 */
@Composable
fun HomeScreen(
    settings: Settings,
    canContinue: Boolean,
    go: HomeActions,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(MaterialTheme.colorScheme.feltGradient())),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                // Scrollable for the screen this menu was not drawn for: a phone on its
                // side, where the fan, the panel and the buttons stand taller than the
                // screen. On every other screen the content fits and the scroll is inert.
                .verticalScroll(rememberScrollState())
                .padding(Pad)
                .widthIn(max = ColumnMax),
            verticalArrangement = Arrangement.spacedBy(Gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Hero()

            SoloPanel(
                difficulty = settings.difficulty,
                canContinue = canContinue,
                onContinue = go.continueGame,
                onPlay = go.newGame,
            )

            // Not "coming soon" as a disabled button. The room and its server exist and the
            // client that joins one does not, which is a real answer and worth giving when
            // somebody asks — so the button works and says so.
            GameButton(
                label = stringResource(Res.string.home_online),
                tone = ButtonTone.NEUTRAL,
                onClick = go.online,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Gap),
            ) {
                GameButton(
                    label = stringResource(Res.string.home_teach),
                    tone = ButtonTone.NEUTRAL,
                    onClick = go.teach,
                    modifier = Modifier.weight(1f),
                )
                GameButton(
                    label = stringResource(Res.string.home_settings),
                    tone = ButtonTone.NEUTRAL,
                    onClick = go.settings,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(
            text = stringResource(Res.string.home_version, VERSION),
            fontSize = FootnoteSize,
            color = MaterialTheme.colorScheme.onFelt().copy(alpha = Quiet),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Gap),
        )
    }
}

/**
 * The wordmark, dealt in.
 *
 * A logo that is simply *there* on the first frame is the one part of a card game that never
 * moves; the fan arriving card by card is both the app introducing itself and the first thing
 * it teaches — this is a hand, and the hand is the game. It plays once per launch, and the
 * whole thing is over in three quarters of a second.
 */
@Composable
private fun Hero() {
    val deal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { deal.animateTo(1f, tween(DealMs, easing = FastOutSlowInEasing)) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tight),
    ) {
        Box(
            modifier = Modifier.height(FanHeight).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            FAN.forEachIndexed { index, angle ->
                // Staggered, so it reads as five cards dealt rather than one shape opening.
                val start = index * FanStagger
                val t = ((deal.value - start) / (1f - start)).coerceIn(0f, 1f)
                val lift = (index - (FAN.size - 1) / 2f).let { it * it } * FanArch

                Image(
                    painter = painterResource(Res.drawable.card_back),
                    contentDescription = null,
                    modifier = Modifier
                        .size(CardW, CardH)
                        .graphicsLayer {
                            rotationZ = angle * t
                            translationX = angle * FanSpread * t
                            translationY = (lift + (1f - t) * FanDrop) * density
                            alpha = t
                        },
                )
            }
        }

        Text(
            stringResource(Res.string.app_name),
            fontFamily = Wordmark,
            fontSize = TitleSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = TitleTracking,
            // Gold leaf on baize. Not the rail's brass, which is 2.7:1 on the lighter felt.
            color = MaterialTheme.colorScheme.feltGold(),
        )
        Text(
            stringResource(Res.string.home_tagline),
            fontSize = BodySize,
            color = MaterialTheme.colorScheme.onFelt().copy(alpha = Quiet),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The mode that is finished, open and ready.
 *
 * A panel rather than loose buttons, because the difficulty belongs *to* single player and
 * nowhere else: a chip row floating on the felt reads as a global setting, which it is not —
 * an online room will have its own answer, decided by whoever opens it.
 */
@Composable
private fun SoloPanel(
    difficulty: Difficulty,
    canContinue: Boolean,
    onContinue: () -> Unit,
    onPlay: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PanelCorner),
        color = Rail.fill,
        border = BorderStroke(1.dp, Rail.line),
    ) {
        Column(
            modifier = Modifier.padding(Gap),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Text(
                stringResource(Res.string.home_solo_title, stringResource(difficulty.label())),
                fontSize = LabelSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Rail.inkDim,
            )

            // Continuing comes first when there is something to continue. A game left
            // half-played is the reason the app was opened; starting a new one over the top of
            // it is the rarer intent and the destructive one.
            if (canContinue) {
                GameButton(
                    label = stringResource(Res.string.home_continue),
                    tone = ButtonTone.PLAY,
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
                GameButton(
                    label = stringResource(Res.string.home_new_game),
                    tone = ButtonTone.NEUTRAL,
                    onClick = onPlay,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                GameButton(
                    label = stringResource(Res.string.home_play),
                    tone = ButtonTone.PLAY,
                    onClick = onPlay,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** How each card in the fan is turned, in degrees. */
private val FAN = listOf(-22f, -11f, 0f, 11f, 22f)

private const val DealMs = 720
private const val FanStagger = 0.12f

/** How far a card slides sideways per degree of turn, and how much the ends ride up. */
private const val FanSpread = 2.6f
private const val FanArch = 0.9f

/** Where a card comes from: below the fan, on the way in. */
private const val FanDrop = 40f

private val CardW = 64.dp
private val CardH = 90.dp
private val FanHeight = 130.dp

private val TitleSize = 46.sp
private val TitleTracking = 6.sp
private val BodySize = 15.sp
private val LabelSize = 12.sp
/** Second-rank text on the felt: present, and not competing with the wordmark. */
private const val Quiet = 0.75f

private val FootnoteSize = 12.sp
