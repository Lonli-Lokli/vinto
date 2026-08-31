package game.vinto.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import game.vinto.app.art.Res
import game.vinto.app.art.cinzel_bold
import game.vinto.app.art.fira_bold
import game.vinto.app.art.fira_medium
import game.vinto.app.art.fira_semibold
import org.jetbrains.compose.resources.Font

/**
 * The voice of the app, which used to be Roboto.
 *
 * A game that looks expensive is doing three or four things at once, and the loudest of them
 * is the type. Material's default face is the strongest single signal that a screen is an
 * *app* — it is what every form, every settings page and every bank on the phone is set in —
 * so a card table drawn in it reads as a form about a card game. Two faces replace it:
 *
 * * **Cinzel**, an engraved Roman, for the name of the game and nothing else. It is the
 *   brass plaque screwed to the table, used once per screen, which is why it can afford to
 *   be ornate.
 * * **Fira Sans Condensed** for everything a player reads. Condensed earns its place twice
 *   over: a caps label fits across a third of a phone, and the same word in German or
 *   Belarusian still fits, which a wide face would not.
 *
 * The other half of the effect is *case*. Controls shout in tracked caps — DISCARD, KEEP,
 * DRAW — the way the words on a chip or a plaque are stamped; the table's narration speaks
 * in sentence case. That division is what separates a control deck from a paragraph, and it
 * costs nothing but the discipline of applying it.
 */
@Composable
fun vintoTypography(): Typography {
    val ui = uiFamily()
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = ui),
        displayMedium = base.displayMedium.copy(fontFamily = ui),
        displaySmall = base.displaySmall.copy(fontFamily = ui),
        headlineLarge = base.headlineLarge.copy(fontFamily = ui, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = ui, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = ui, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = ui, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = ui, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = ui, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = ui, fontWeight = FontWeight.Medium),
        bodyMedium = base.bodyMedium.copy(fontFamily = ui, fontWeight = FontWeight.Medium),
        bodySmall = base.bodySmall.copy(fontFamily = ui, fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontFamily = ui, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = ui, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = ui, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
private fun uiFamily() = FontFamily(
    Font(Res.font.fira_medium, FontWeight.Medium),
    Font(Res.font.fira_semibold, FontWeight.SemiBold),
    Font(Res.font.fira_bold, FontWeight.Bold),
)

/** The name of the game, and nothing else. Latin only, which is all a proper noun needs. */
val Wordmark: FontFamily
    @Composable get() = FontFamily(Font(Res.font.cinzel_bold, FontWeight.Bold))

/**
 * A stamped label: words written *on* a control rather than said to a player.
 *
 * Caps and letterspaced, because that is how the words on a chip, a plaque or a button at a
 * real table are cut — and because tracked caps stay legible at the size a row of actions
 * across a phone leaves for them.
 */
@Composable
fun stamped(size: Int = LabelSize, weight: FontWeight = FontWeight.SemiBold): TextStyle =
    MaterialTheme.typography.labelLarge.copy(
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = Tracking.sp,
    )

private const val LabelSize = 15
private const val Tracking = 1.1
