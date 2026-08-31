---
  description: "Verify implementation against official rules"
  allowed-tools:
    - Read
    - Grep
  argument-hint: "rule name or card type"
  ---

  Check if the implementation of "$ARGUMENTS" matches the official Vinto rules.

  Reference:
  - @docs/game-engine/VINTO_RULES.md for official rules
  - @docs/game-engine/SOURCE_NOTES.md for rule source documentation
  - @docs/game-engine/SCENARIOS.md for edge cases

  Look for implementation in:
  - shared/engine/src/commonMain/kotlin/game/vinto/engine/cases/ (action handlers)
  - shared/engine/src/commonMain/kotlin/game/vinto/engine/ (ActionValidator)

  Where the engine and the official PDF disagree, the decisions are recorded in the table
  at the foot of VINTO_RULES.md. Read it before reporting a difference as a bug: several
  are deliberate and one reverses an earlier decision.

  ---