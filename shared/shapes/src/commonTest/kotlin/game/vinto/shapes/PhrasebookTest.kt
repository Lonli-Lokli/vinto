package game.vinto.shapes

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Table talk is a **phrasebook**, and this is what makes that true rather than intended.
 *
 * The invariant it serves is older than the feature: *nothing a player types reaches another
 * player's screen*. A vocabulary of typed values keeps it literally — nobody types anything —
 * and buys something a text box could not, since twenty locales ship: every sentence is
 * rendered by the reader's own client in the reader's own language, so two players with no
 * language in common can still agree a move.
 *
 * A `String` field is where that would quietly stop being true. One free-text field added in
 * good faith — a note, a reason, a nickname carried along — and the channel is a chat box with
 * extra steps, one content rating, and two players staring at words neither can read. So the
 * strings that are allowed are named here, one by one, and anything else fails.
 */
@OptIn(ExperimentalSerializationApi::class)
class PhrasebookTest {

    /**
     * The only strings a sentence may carry: **identifiers**, which name a seat the reader's
     * own client already knows how to draw.
     *
     * Adding to this list is a decision, not a formality. Anything that is not a seat id is
     * words, and words belong in `strings.xml` where they can be translated.
     */
    private val identifiers = setOf("by", "to", "from", "seat")

    @Test
    fun noSentenceCarriesFreeText() {
        val offenders = mutableListOf<String>()
        sentences().forEach { sentence ->
            (0 until sentence.elementsCount).forEach { index ->
                val name = sentence.getElementName(index)
                val element = sentence.getElementDescriptor(index)
                val here = "${sentence.serialName.substringAfterLast('.')}.$name"
                // A `GameAction` is a move, not prose: the same serialisable value the engine
                // already validates, and a proposal is one addressed to somebody else.
                val isMove = element.serialName.contains("GameAction")
                if (!isMove && element.kind == PrimitiveKind.STRING && name !in identifiers) {
                    offenders += here
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "table talk must carry no words, only typed values — found: $offenders",
        )
    }

    @Test
    fun everySentenceNamesItsSpeaker() {
        // Without this the channel is anonymous, and an anonymous claim attached to a shared
        // prize is an invitation to claim low cards for yourself so the coalition pushes you.
        val all = sentences()
        assertTrue(all.isNotEmpty(), "the phrasebook is empty")

        all.forEach { sentence ->
            val fields = (0 until sentence.elementsCount).map(sentence::getElementName)
            if ("by" !in fields) fail("${sentence.serialName} does not say who said it: $fields")
        }
    }

    /**
     * Every sentence in the phrasebook, listed rather than discovered.
     *
     * kotlinx does not expose a sealed hierarchy's members through its descriptor, so this is
     * a hand-written list — and that turns out to be the better shape anyway: adding a
     * sentence means adding a line here, which puts the rule about what a sentence may carry
     * in front of whoever is adding one, at the moment they are adding it.
     */
    private fun sentences(): List<SerialDescriptor> = listOf(
        TableTalk.Proposal.serializer().descriptor,
        TableTalk.GiveMe.serializer().descriptor,
        TableTalk.TakeThis.serializer().descriptor,
        TableTalk.IWill.serializer().descriptor,
        TableTalk.WillShed.serializer().descriptor,
        TableTalk.Standing.serializer().descriptor,
        TableTalk.PlayFor.serializer().descriptor,
        TableTalk.Answer.serializer().descriptor,
    )
}
