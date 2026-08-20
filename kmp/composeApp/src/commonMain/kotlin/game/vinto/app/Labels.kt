package game.vinto.app

import game.vinto.app.art.Res
import game.vinto.app.art.difficulty_easy
import game.vinto.app.art.difficulty_hard
import game.vinto.app.art.difficulty_moderate
import game.vinto.app.art.pace_brisk
import game.vinto.app.art.pace_calm
import game.vinto.app.art.pace_steady
import game.vinto.app.art.theme_dark
import game.vinto.app.art.theme_light
import game.vinto.app.art.theme_system
import game.vinto.client.Pace
import game.vinto.client.ThemeChoice
import game.vinto.shapes.Difficulty
import org.jetbrains.compose.resources.StringResource

/**
 * The names the player sees for the things they can choose.
 *
 * Kept here rather than on the types themselves, because a `Difficulty` is a rule the engine
 * plays by and its `serialName` is part of a saved game and a recording — a wire format, not a
 * word. Capitalising a wire value to put it on a button worked exactly as long as the app was
 * English: "moderate" is not "Памяркоўны", and no amount of `replaceFirstChar` will make it so.
 */
fun Difficulty.label(): StringResource = when (this) {
    Difficulty.EASY -> Res.string.difficulty_easy
    Difficulty.MODERATE -> Res.string.difficulty_moderate
    Difficulty.HARD -> Res.string.difficulty_hard
}

fun Pace.label(): StringResource = when (this) {
    Pace.CALM -> Res.string.pace_calm
    Pace.STEADY -> Res.string.pace_steady
    Pace.BRISK -> Res.string.pace_brisk
}

fun ThemeChoice.label(): StringResource = when (this) {
    ThemeChoice.SYSTEM -> Res.string.theme_system
    ThemeChoice.LIGHT -> Res.string.theme_light
    ThemeChoice.DARK -> Res.string.theme_dark
}
