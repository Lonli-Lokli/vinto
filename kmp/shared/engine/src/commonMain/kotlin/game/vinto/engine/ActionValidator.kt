package game.vinto.engine

import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState

sealed interface Validation {
    data object Valid : Validation
    data class Invalid(val reason: String) : Validation
}

/**
 * Legality checks, run before any handler.
 *
 * **Not ported yet** — `packages/engine/src/lib/action-validator.ts` is 727 lines and is
 * phase 4 work. Until then this permits everything.
 *
 * That is safe for the parity gate and unsafe for anything else, and the distinction
 * matters. Every action in `fixtures/recordings/` was accepted by the TypeScript engine
 * when it was recorded, so a permissive validator cannot change a replay's outcome — which
 * also means replay can never demonstrate that this is finished. It needs the ported
 * TypeScript validator tests (task 4.4), not the corpus.
 *
 * Nothing should run this engine against untrusted input before then; the Durable Object
 * in particular depends on server-side validation being real (design D9).
 */
object ActionValidator {
    fun validate(
        @Suppress("UNUSED_PARAMETER") state: GameState,
        @Suppress("UNUSED_PARAMETER") action: GameAction,
    ): Validation = Validation.Valid
}
