# Source Notes for Vinto Engine

This engine specification encodes the following rule sections:

- **Overview & Objective**: minimize hand sum; 4–5 players; timed multi-round play with rank-based game points.  
- **Setup**: 54-card deck; deal five face-down per player; players may peek at **any two** of their own cards once; draw and discard piles.  
- **Turn Options**:
  - **Option A (Draw)**: reveal the drawn card publicly; then either play its action (if 7–K) and end turn, or swap with one of your facedown cards then discard your swapped-out card face-up. After swapping, you may **guess** the discarded card’s rank; correct → play that card’s action; incorrect → draw one penalty card face-down.  
  - **Option B (Take from Discard)**: only if the top discard is an **unused action card (7–K)**; take and immediately play its action; you **cannot** swap it into your hand.  
- **Reaction: Toss In**: immediately after any card is placed on discard, any player may toss in a card **of the same rank** and immediately perform its action; wrong rank → take it back and draw one penalty card face-down.  
- **Actions & Values**:  
  - 2–6: no action; values 2–6.  
  - 7–8: peek at one of your own cards.  
  - 9–10: peek at one card of another player.  
  - J: swap any two facedown cards from two different players.  
  - Q: peek any two cards from two different players, then optionally swap them.  
  - K: value 0; declare the value of any card and **play its action**.  
  - A: value 1; choose a player to take 1 card from the deck.  
  - Joker: value −1; no action.  
- **Final Round (“Vinto”)**: may be declared **at the end of your turn**; triggers one final turn for each other player (the **Coalition**). During the Final Round, **no one may interact with the Vinto player’s cards**.  
- **Scoring**: reveal all; compare the Vinto player’s total vs the **single lowest** Coalition total; awards:  
  - If Vinto ≤ lowest Coalition: Vinto +3; each Coalition −1.  
  - If lowest Coalition < Vinto: Vinto −1; each Coalition +3.  
  - If tie: Vinto +3; Coalition 0.  
- **Game Win**: play for a set time (e.g., 30 minutes). After time, finish the round, rank players by cumulative scores, then award game points (1st=5, 2nd=3, 3rd=2); highest game points wins.

See the original rule excerpts in the repository history if needed.

