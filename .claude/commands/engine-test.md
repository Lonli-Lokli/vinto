---
  description: "Test game engine with scenario"
  allowed-tools:
    - Read
    - Bash(npx nx test:*)
    - Write
  argument-hint: "scenario description"
  ---

  Create and run a game engine test for: $ARGUMENTS

  Steps:
  1. Review relevant scenarios in docs/game-engine/SCENARIOS.md
  2. Check existing tests in shared/engine/src/commonTest/ (Kotlin) or
     legacy-web/packages/engine/src/lib/ (TypeScript)
  3. Create a new test case following the pattern
  4. Run the test: ./gradlew :shared:engine:jvmTest
     (TypeScript: cd legacy-web && npx nx test engine)
  5. Verify game state immutability and determinism