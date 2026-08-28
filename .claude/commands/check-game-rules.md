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
  - shared/engine/src/commonMain/kotlin/game/vinto/engine/cases/ (Kotlin action handlers)
  - legacy-web/packages/engine/src/lib/cases/ (TypeScript action handlers)
  - shared/engine/src/commonMain/kotlin/game/vinto/engine/ (ActionValidator)
  - legacy-web/packages/engine/src/lib/validators/ (TypeScript validation logic)

  ---