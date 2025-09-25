# Scenarios & Edge Cases

This file provides deterministic walkthroughs that code generators and tests can follow.

## S-001: Basic Turn — Draw and Play Action
**Given**: Start of Alice’s turn; she draws the top deck card, it’s a 9 (action).  
**When**: She reveals it, chooses **Play Action**.  
**Then**: She peeks at one card of Bob. Turn ends.

## S-002: Draw, Swap, Guess — Correct
Draw reveals a 5 (no action). Alice swaps it with one of her facedown cards and discards that card (face-up). She announces a **Guess** of “Jack”. The discarded card is indeed a Jack.  
**Effect**: She immediately performs Jack’s action: swap any **two** facedown cards on the table. No penalty. Turn ends.

## S-003: Draw, Swap, Guess — Incorrect
As above, but her guess is wrong.  
**Effect**: Draw **one penalty** card face-down from deck and add to hand.

## S-004: Take from Discard (Action Card Only)
Top discard is a Queen with **unused** action. Alice takes it and **must** immediately play: peek any two, optionally swap.  
**Restriction**: She **cannot** swap this Queen into her hand.

## S-005: Toss In — Success
Bob discards a 10. Carol immediately **Tosses In** her own 10 face-up and performs 10’s action (peek one of another player’s cards). The window is immediate; if multiple attempt, resolve in table order.

## S-006: Toss In — Failure
Dave tosses in a 9 but the discard was an 8.  
**Effect**: Dave takes back his 9 and draws **one penalty** card face-down.

## S-007: Declaring Vinto
At the **end** of Eve’s turn she declares **“Vinto”**.  
**Effect**: Final Round begins. Each other player gets **exactly one** final turn. No one may interact with Eve’s cards during this phase.

## S-008: Round Scoring — Coalition Wins
After Final Round, totals are: Vinto=12; Coalition’s lowest=9.  
**Points**: Vinto −1; each Coalition +3.

## S-009: Joker Semantics
Joker has value **−1** and no action. If swapped/discarded, no action trigger.

