# Vinto Game Engine — Developer Overview

This folder documents the *Vinto* engine abstractions and how to integrate them into server or client code.

## What is modeled
- Players, deck, draw/discard piles
- Turn structure (Option A: draw from deck, Option B: take actionable discard)
- Reaction window: **Toss In**
- Card actions
- Round end / Final Round trigger (**"Vinto"**) and coalition restriction
- Scoring for round and game

## Source of Truth
The rules were normalized from the official Vinto composite document (see `/docs/game-engine/SOURCE_NOTES.md`). If behavior here differs from your expectations, open an issue referencing the rule and example.

## Implementation Style
- No randomness outside deck shuffling; use a seed for reproducibility.
- All rule checks occur in the engine, not the UI.

## File Map
- `/docs/game-engine/SCENARIOS.md` – worked examples and edge cases.

