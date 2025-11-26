---
  description: "Debug MCTS bot decision making"
  allowed-tools:
    - Read
    - Grep
  argument-hint: "bot player ID or scenario description"
  ---

  Analyze the MCTS bot decision-making logic for the scenario: $ARGUMENTS

  Look at packages/bot/ for:
  1. MCTS implementation
  2. Decision tree evaluation
  3. Current coalition-mode handling
  4. Performance optimizations

  Check for:
  - Correct game state evaluation
  - Proper action selection
  - Coalition mode logic (see COALITION_*.md files)
  - Edge cases from docs/game-engine/SCENARIOS.md