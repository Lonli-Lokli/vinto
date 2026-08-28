package game.vinto.shapes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What the state hash must and must not notice. Ported from the `canonicalizeGameState` and
 * `hashGameState` blocks of `legacy-web/packages/shapes/src/lib/__tests__/canonical-json.test.ts`.
 *
 * This is the contract the whole migration is verified against, and it has two halves that
 * fail in opposite directions. If the hash misses something the rules depend on, two engines
 * can disagree and the corpus will still pass. If it notices something presentational, every
 * recording breaks the first time somebody edits a log line. Both halves are checked here on
 * a typed `GameState` rather than on hand-written JSON, so the serialiser is under test too.
 */
class StateHashTest {

    private fun card(id: String, rank: Rank) = Card(
        id = id,
        rank = rank,
        value = getCardValue(rank),
        played = false,
        actionText = getCardShortDescription(rank).takeIf { it.isNotEmpty() },
    )

    private fun player(
        id: String,
        cards: List<Card> = listOf(card("$id-c0", Rank.FIVE)),
        knownCardPositions: List<Int> = emptyList(),
        opponentKnowledge: Map<String, SerializedOpponentKnowledge>? = null,
    ) = PlayerState(
        id = id,
        name = id,
        nickname = id,
        isHuman = id == "p1",
        isBot = id != "p1",
        cards = cards,
        knownCardPositions = knownCardPositions,
        isVintoCaller = false,
        coalitionWith = emptyList(),
        opponentKnowledge = opponentKnowledge,
    )

    private fun state(
        players: List<PlayerState> = listOf(player("p1"), player("p2"), player("p3"), player("p4")),
        currentPlayerIndex: Int = 0,
        drawPile: Pile = Pile(listOf(card("d1", Rank.SEVEN))),
        discardPile: Pile = Pile(),
        turnActions: List<GameActionHistory> = emptyList(),
        rngState: Long = 0,
    ) = GameState(
        gameId = "g",
        roundNumber = 1,
        turnNumber = 0,
        phase = GamePhase.PLAYING,
        subPhase = GameSubPhase.IDLE,
        finalTurnTriggered = false,
        players = players,
        currentPlayerIndex = currentPlayerIndex,
        vintoCallerId = null,
        coalitionLeaderId = null,
        drawPile = drawPile,
        discardPile = discardPile,
        pendingAction = null,
        activeTossIn = null,
        turnActions = turnActions,
        roundActions = emptyList(),
        roundFailedAttempts = emptyList(),
        difficulty = Difficulty.MODERATE,
        rngState = rngState,
    )

    private fun history(description: String) = GameActionHistory(
        playerId = "p1",
        playerName = "P1",
        description = description,
        timestamp = 0,
        turnNumber = 1,
        roundNumber = 1,
    )

    // --- shape ------------------------------------------------------------------------------

    @Test
    fun theCanonicalFormHasNoWhitespaceAndSortedKeys() {
        // Number cards only, so every string in the document is space-free and any space
        // that shows up is structural — an action card's `actionText` is English prose.
        val canonical = canonicalizeGameState(
            state(drawPile = Pile(listOf(card("d1", Rank.FOUR)))),
        )

        assertTrue(!canonical.contains(" "), "whitespace leaked into the canonical form")
        assertTrue(!canonical.contains("\n"))

        // Sorted keys, checked as ordering rather than as a guess at the first one.
        val order = listOf("activeTossIn", "coalitionLeaderId", "currentPlayerIndex", "difficulty", "gameId")
        val positions = order.map { canonical.indexOf("\"$it\":") }
        assertTrue(positions.none { it < 0 }, "a top-level key is missing: $order")
        assertEquals(positions.sorted(), positions, "top-level keys are not in sorted order")
    }

    @Test
    fun aPileIsAPlainArrayWithTheTopCardFirst() {
        val canonical = canonicalizeGameState(
            state(drawPile = Pile(listOf(card("top", Rank.ACE), card("under", Rank.TWO)))),
        )

        assertTrue(canonical.contains("\"drawPile\":[{"), "the pile did not serialise as an array")
        assertTrue(
            canonical.indexOf("\"top\"") < canonical.indexOf("\"under\""),
            "the pile order was not preserved top-first",
        )
    }

    // --- sensitivity: game logic must move the hash -------------------------------------------

    @Test
    fun everyPieceOfGameStateMovesTheHash() {
        val baseline = canonicalizeGameState(state())

        val mutations = mapOf(
            "rngState" to state(rngState = 999),
            "a card id" to state(
                players = listOf(
                    player("p1", cards = listOf(card("other", Rank.FIVE))),
                    player("p2"), player("p3"), player("p4"),
                ),
            ),
            "knownCardPositions" to state(
                players = listOf(
                    player("p1", knownCardPositions = listOf(0)),
                    player("p2"), player("p3"), player("p4"),
                ),
            ),
            "currentPlayerIndex" to state(currentPlayerIndex = 2),
            "the draw pile" to state(drawPile = Pile(listOf(card("zz", Rank.FOUR)))),
        )

        for ((what, mutated) in mutations) {
            assertNotEquals(baseline, canonicalizeGameState(mutated), "$what did not change the hash")
        }
    }

    @Test
    fun opponentKnowledgeIsPartOfTheContract() {
        // The engine writes it deterministically, so two implementations must agree on it —
        // and it is what a peek produces, so leaving it out would hide a whole class of bug.
        val withKnowledge = state(
            players = listOf(
                player(
                    "p1",
                    opponentKnowledge = mapOf(
                        "p2" to SerializedOpponentKnowledge(knownCards = mapOf(0 to card("k", Rank.FIVE))),
                    ),
                ),
                player("p2"), player("p3"), player("p4"),
            ),
        )

        assertNotEquals(canonicalizeGameState(state()), canonicalizeGameState(withKnowledge))
    }

    @Test
    fun aStateWithoutDeclarationsHashesExactlyAsBefore() {
        // `declaredCards` is Kotlin-only; a null field must be *absent* from the canonical
        // form, or every TypeScript-recorded hash in the parity corpus would move.
        assertTrue("declaredCards" !in canonicalizeGameState(state()))
    }

    @Test
    fun declarationsChangeTheHashDeterministically() {
        val declared = state(
            players = listOf(
                player("p1").copy(declaredCards = mapOf(0 to Rank.QUEEN)),
                player("p2"), player("p3"), player("p4"),
            ),
        )

        assertNotEquals(canonicalizeGameState(state()), canonicalizeGameState(declared))
        assertEquals(canonicalizeGameState(declared), canonicalizeGameState(declared))
    }

    // --- exclusions: presentation must not -----------------------------------------------------

    @Test
    fun clientWrittenHistoryIsNotPartOfTheContract() {
        // The reducer never writes these, so a replayed state legitimately has none. If they
        // counted, no recording could ever be replayed.
        assertEquals(
            canonicalizeGameState(state()),
            canonicalizeGameState(state(turnActions = listOf(history("P1 drew a card")))),
        )
    }

    @Test
    fun theWordingOfALogLineIsNotPartOfTheContract() {
        assertEquals(
            canonicalizeGameState(state(turnActions = listOf(history("P1 drew a card")))),
            canonicalizeGameState(state(turnActions = listOf(history("Player One draws")))),
        )
    }

    // --- the digest ----------------------------------------------------------------------------

    @Test
    fun theDigestIsLowercaseHexAndSixtyFourCharacters() {
        val hash = hashGameState(state())

        assertEquals(64, hash.length)
        assertTrue(Regex("^[0-9a-f]{64}$").matches(hash), hash)
    }

    @Test
    fun theDigestIsStableAndMovesWithTheState() {
        assertEquals(hashGameState(state()), hashGameState(state()))
        assertNotEquals(hashGameState(state()), hashGameState(state(rngState = 1)))
    }

    @Test
    fun theDigestIsPlainSha256OfTheCanonicalForm() {
        // Not a bespoke construction: whatever else changes, the hash stays something another
        // implementation can reproduce with a stock SHA-256 over the canonical string.
        assertEquals(Sha256.hex(canonicalizeGameState(state())), hashGameState(state()))
    }
}
