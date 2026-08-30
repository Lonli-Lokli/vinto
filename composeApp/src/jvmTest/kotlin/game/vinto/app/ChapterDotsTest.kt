package game.vinto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.client.Chapter
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every chapter of the lesson has a name a screen reader can read.
 *
 * The progress dots conveyed how far somebody had got by **colour alone** — nine unlabelled
 * circles — while the words for them sat unused in a `Chapter.label` field that nothing
 * rendered. This is the test that keeps them connected: adding a chapter without a name now
 * fails here rather than adding a silent tenth dot.
 */
@OptIn(ExperimentalTestApi::class)
class ChapterDotsTest {

    @Test
    fun everyChapterHasAWordForIt() = runComposeUiTest {
        val read = mutableStateOf(emptyList<String>())
        setContent { read.value = Chapter.entries.map { stringResource(it.label()) } }
        waitForIdle()

        val names by read
        assertEquals(Chapter.entries.size, names.size)
        for ((chapter, name) in Chapter.entries.zip(names)) {
            assertTrue(name.isNotBlank(), "$chapter has no name for a screen reader to read")
        }
        assertEquals(names.size, names.toSet().size, "two chapters share a name: $names")
    }
}
