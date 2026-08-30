---
  description: "Test game engine with scenario"
  allowed-tools:
    - Read
    - Bash(./gradlew :shared:engine:*)
    - Write
  argument-hint: "scenario description"
  ---

  Create and run a game engine test for: $ARGUMENTS

  Steps:
  1. Review relevant scenarios in docs/game-engine/SCENARIOS.md
  2. Check existing tests in shared/engine/src/commonTest/
  3. Create a new test case following the pattern
  4. Run the test: ./gradlew :shared:engine:jvmTest

     This also replays the frozen parity corpus. If CorpusReplayTest goes red, the
     corpus is almost certainly the thing that was right — see
     fixtures/recordings/README.md, and never regenerate it to go green.
  5. Verify game state immutability and determinism