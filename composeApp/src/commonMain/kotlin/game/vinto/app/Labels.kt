package game.vinto.app

import game.vinto.app.art.Res
import game.vinto.app.art.chapter_actions
import game.vinto.app.art.chapter_declare
import game.vinto.app.art.chapter_draw
import game.vinto.app.art.chapter_keep
import game.vinto.app.art.chapter_peek
import game.vinto.app.art.chapter_score
import game.vinto.app.art.chapter_table
import game.vinto.app.art.chapter_toss
import game.vinto.app.art.chapter_vinto
import game.vinto.app.art.difficulty_easy
import game.vinto.app.art.difficulty_hard
import game.vinto.app.art.difficulty_moderate
import game.vinto.app.art.motion_full
import game.vinto.app.art.motion_reduced
import game.vinto.app.art.motion_system
import game.vinto.app.art.pace_brisk
import game.vinto.app.art.pace_calm
import game.vinto.app.art.pace_steady
import game.vinto.app.art.theme_dark
import game.vinto.app.art.theme_light
import game.vinto.app.art.theme_system
import game.vinto.client.Chapter
import game.vinto.client.MotionChoice
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

fun MotionChoice.label(): StringResource = when (this) {
    MotionChoice.SYSTEM -> Res.string.motion_system
    MotionChoice.FULL -> Res.string.motion_full
    MotionChoice.REDUCED -> Res.string.motion_reduced
}

/**
 * The lesson's chapters, in words.
 *
 * Here for the same reason [Difficulty.label] is here rather than on the enum: a `Chapter` is
 * a part of the game, and the sentence naming it is display. It used to be an English `label`
 * on the enum itself in `shared/client` — a module with no resources — and, worse, nothing
 * ever drew it. See [Chapter].
 */
fun Chapter.label(): StringResource = when (this) {
    Chapter.TABLE -> Res.string.chapter_table
    Chapter.PEEK -> Res.string.chapter_peek
    Chapter.DRAW -> Res.string.chapter_draw
    Chapter.KEEP -> Res.string.chapter_keep
    Chapter.DECLARE -> Res.string.chapter_declare
    Chapter.ACTIONS -> Res.string.chapter_actions
    Chapter.TOSS -> Res.string.chapter_toss
    Chapter.VINTO -> Res.string.chapter_vinto
    Chapter.SCORE -> Res.string.chapter_score
}
