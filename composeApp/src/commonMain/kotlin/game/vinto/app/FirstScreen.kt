package game.vinto.app

import game.vinto.client.LocalGame
import game.vinto.client.Question

/**
 * A screen [App] can be told to open on, in place of the home screen or an invitation's room.
 *
 * No player's launch passes one: every store build opens where it always has. It is how a caller
 * that stages the app says where to stand, and it names screens only. Which screens, and why, is
 * the caller's business, and in this repository that caller is the capture build alone
 * (`composeApp/src/captureMain`, gulnya/claude/capture-build.md), which no store build compiles.
 */
sealed interface FirstScreen {
    /** The home screen, offering to continue a game when [canContinue]. */
    data class Home(val canContinue: Boolean) : FirstScreen

    /** The lesson. */
    data object Lesson : FirstScreen

    /** The online menu. */
    data object Online : FirstScreen

    /** [game] on the felt, its rail opening on [question]. */
    data class Table(val game: LocalGame, val question: Question = Question.None) : FirstScreen
}
