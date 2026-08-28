package game.vinto.shapes

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * TypeScript distinguishes `field?: T` (absent when unset) from `field: T | null` (always
 * written, sometimes null), and the canonical form preserves that distinction — so it is
 * part of the cross-language hash.
 *
 * Kotlin has no `undefined`, so the two map as:
 *   - `field?: T`      -> `@EncodeDefault(NEVER) val field: T? = null`  (omitted when null)
 *   - `field: T | null` -> `val field: T?` with no default              (always written)
 *
 * Getting this wrong does not fail quietly: the canonical string changes and the state
 * hash stops matching TypeScript's.
 */
@OptIn(ExperimentalSerializationApi::class)
/**
 * Whether this card's action is still there for the taking.
 *
 * The difference between a card that was *played* and one that was merely *discarded*, and it
 * outlives the moment: a discarded action card sits on the pile with its action unused, and
 * the next player may take it and play it instead of drawing (Option B). A played one is
 * spent. Nothing about the two cards looks different once they have landed, which is why the
 * table draws a ring round this one.
 */
fun Card.actionIsLive(): Boolean = actionText != null && !played

@Serializable
data class Card(
    val id: String,
    val rank: Rank,
    val value: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val actionText: String? = null,
    val played: Boolean,
)

/**
 * An ordered stack of cards, top first.
 *
 * The TypeScript `Pile` mutates in place; this one is immutable and every operation
 * returns a new pile, because the Kotlin engine is a pure reducer (design D3). It
 * serialises as a bare JSON array — TypeScript's `Pile.toJSON()` does the same, so the
 * wire format is unchanged.
 */
@Serializable(with = PileSerializer::class)
class Pile(cards: List<Card> = emptyList()) {

    val cards: List<Card> = cards.toList()

    val size: Int get() = cards.size

    fun isEmpty(): Boolean = cards.isEmpty()

    /** Negative indices count from the end, matching the TypeScript `at()`. */
    fun at(index: Int): Card? {
        val normalized = if (index >= 0) index else cards.size + index
        return cards.getOrNull(normalized)
    }

    fun peekTop(): Card? = at(0)

    /** The top card and the pile without it; `null` card when empty. */
    fun drawTop(): Pair<Card?, Pile> =
        if (cards.isEmpty()) null to this else cards[0] to Pile(cards.drop(1))

    fun addToTop(card: Card): Pile = Pile(listOf(card) + cards)

    /** Inserts directly beneath the top card — used when a penalty card must not be drawn next. */
    fun addBeforeTop(card: Card): Pile =
        if (cards.isEmpty()) Pile(listOf(card))
        else Pile(listOf(cards[0]) + card + cards.drop(1))

    /** The card at [index] and the pile without it; `null` when out of range. */
    fun takeAt(index: Int): Pair<Card?, Pile> =
        if (index !in cards.indices) null to this
        else cards[index] to Pile(cards.filterIndexed { i, _ -> i != index })

    fun toList(): List<Card> = cards

    override fun equals(other: Any?): Boolean = other is Pile && other.cards == cards

    override fun hashCode(): Int = cards.hashCode()

    override fun toString(): String = "Pile(${cards.size} cards)"

    companion object {
        fun of(vararg cards: Card): Pile = Pile(cards.toList())
    }
}

object PileSerializer : KSerializer<Pile> {
    private val delegate = ListSerializer(Card.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Pile) =
        delegate.serialize(encoder, value.toList())

    override fun deserialize(decoder: Decoder): Pile = Pile(delegate.deserialize(decoder))
}
