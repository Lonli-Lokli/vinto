---
  description: "Debug MCTS bot decision making"
  allowed-tools:
    - Read
    - Grep
  argument-hint: "bot player ID or scenario description"
  ---

  Analyze the MCTS bot decision-making logic for the scenario: $ARGUMENTS

  Look at shared/bot/ for:
  1. MCTS implementation
  2. Decision tree evaluation
  3. Current coalition-mode handling
  4. Performance optimizations

  Check for:
  - Correct game state evaluation
  - Proper action selection
  - Coalition mode logic (CoalitionPlanner.kt)
  - Edge cases from docs/game-engine/SCENARIOS.md

  The bot is verified by rule-following rather than decision parity — SelfPlayGateTest
  puts every proposed action through ActionValidator. Strength is measured separately,
  against a committed self-play baseline (docs/kotlin/GATES.md §6k):

      ./gradlew :shared:bot:jvmTest --tests '*TournamentTest*' -Ptournament