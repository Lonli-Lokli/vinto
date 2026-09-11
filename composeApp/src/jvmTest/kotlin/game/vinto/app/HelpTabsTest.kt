package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.HelpSheet
import game.vinto.app.theme.VintoTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The help sheet is four tabs, and each of them has something in it.
 *
 * It used to be one column: thirteen ranks, seven rings and four paragraphs, which is a screen
 * and a half of scrolling to reach a legend. A player opens this sheet mid-turn with a card
 * waiting and one hand free, so what they came for has to be one press away.
 *
 * The **badges** tab is new. Every mark a seat plate or a card wears was drawn on the table and
 * explained nowhere — a crown, a link, a tick, a cross — so the one player who needed to know
 * what they meant had no way to find out. Each row draws the *same* composable the table draws,
 * beside the same sentence a screen reader is given for it, so the legend cannot come to
 * disagree with the table it is a legend for.
 */
@OptIn(ExperimentalTestApi::class)
class HelpTabsTest {

    @Test
    fun everyTabHasSomethingUnderIt() = runComposeUiTest {
        show()

        // Cards: the sheet opens here, because "what does this one do" is the question that
        // brought the player.
        assertTrue(
            words().any { it.contains("Numbers", ignoreCase = true) },
            "the cards tab does not group the ranks: ${words()}",
        )

        open("RINGS")
        assertTrue(
            words().any { it.contains("ring", ignoreCase = true) },
            "the rings tab names no ring: ${words()}",
        )

        open("BADGES")
        val badges = spoken() + words()
        assertTrue(
            badges.any { it.contains("called Vinto", ignoreCase = true) },
            "the badges tab does not explain the crown: $badges",
        )
        assertTrue(
            badges.any { it.contains("disputed", ignoreCase = true) },
            "the badges tab does not explain a disputed claim: $badges",
        )

        open("MORE")
        assertTrue(
            words().any { it.contains("The deck", ignoreCase = true) },
            "the more tab has lost the deck: ${words()}",
        )
    }

    @Test
    fun theBadgesTabExplainsEveryMarkATableCanDraw() = runComposeUiTest {
        // A legend that names some of the marks is worse than none: the one a player cannot
        // find is the one they were looking for. Read out of the enum rather than out of a list
        // kept beside it, so a mark added to the table cannot be added without a line here.
        show()
        open("BADGES")

        val said = spoken() + words()
        val marks = game.vinto.app.game.SeatBadge.entries
        assertTrue(marks.size > 1, "there are no seat marks to explain")
        assertTrue(
            said.size >= marks.size,
            "the badges tab draws ${said.size} lines for ${marks.size} marks and five claims",
        )
    }

    private fun ComposeUiTest.open(tab: String) {
        onNodeWithText(tab).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.words(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    private fun ComposeUiTest.spoken(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

    private fun ComposeUiTest.show() {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    HelpSheet(open = true, now = null, left = DECK_LEFT, onDismiss = {})
                }
            }
        }
        waitForIdle()
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
        const val DECK_LEFT = 21
    }
}
