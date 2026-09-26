package game.vinto.app.theme

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Text that gets smaller until it fits, and never fits by breaking a word.
 *
 * For a player's name in a slot that cannot grow. A minted name is two words — "Dusty Pebble" —
 * and the room guarantees two players' differ, but only if both words can be read: cut to
 * "Dusty Peb…" it is the same player as "Dusty Pebbles", and that is what every plate on the
 * felt said until the names were allowed to shrink.
 *
 * Compose's own step-based sizing is almost this, and it stops at the first size where nothing
 * overflows. A line break *inside* a word is not an overflow, so at the largest size a name
 * like "Harbour" would come out as "Harbo" over "ur" and be judged a fit. This asks the one more
 * question: does every line end at a space? Only then is the size accepted.
 *
 * Below [least] it gives up shrinking and lets the text's own overflow have the last word; the
 * floor is there so a name is never drawn smaller than somebody can read.
 */
class WholeWords(
    private val least: TextUnit,
    private val most: TextUnit,
    private val step: TextUnit = STEP,
) : TextAutoSize {

    override fun TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints,
        text: AnnotatedString,
    ): TextUnit {
        var size = most.value
        while (size > least.value) {
            val laid = performLayout(constraints, text, size.sp)
            if (!laid.didOverflowHeight && !laid.didOverflowWidth && !laid.breaksAWord()) return size.sp
            size -= step.value
        }
        return least
    }

    override fun equals(other: Any?): Boolean =
        other is WholeWords && other.least == least && other.most == most && other.step == step

    override fun hashCode(): Int = (least.hashCode() * HASH + most.hashCode()) * HASH + step.hashCode()

    private companion object {
        val STEP = 0.5.sp
        const val HASH = 31
    }
}

/** Whether any line but the last ends part-way through a word. */
internal fun TextLayoutResult.breaksAWord(): Boolean {
    val text = layoutInput.text
    return (0 until lineCount - 1).any { line ->
        val end = getLineEnd(line)
        end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace()
    }
}
