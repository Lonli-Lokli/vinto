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
  - packages/engine/src/lib/cases/ (action handlers)
  - packages/engine/src/lib/validators/ (validation logic)

  ---