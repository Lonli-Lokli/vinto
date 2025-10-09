# Clean Migration Plan - One Component at a Time

## ✅ Progress: 14/14 Components Migrated (100%) 🎉

**Completed migrations:**

1. **CoalitionTurnIndicator** ✅
   - Migrated from 2 stores → `useGameClient()`
   - Updated Avatar component to accept both Player and PlayerState types
   - Build passing ✅

2. **WaitingIndicator** ✅
   - Migrated from 5 stores → `useGameClient()`
   - Maps GameClient subPhases to UI logic
   - Simplified action context handling
   - Build passing ✅

3. **GameHeader** ✅
   - Migrated from 4 stores → `useGameClient()` + 1 store for actions
   - Displays turn/round/phase info from GameClient
   - Kept `gameStore.updateDifficulty()` for now (needs action in GameEngine)
   - Build passing ✅

4. **PlayerArea** ✅
   - Migrated from 1 store → `useGameClient()`
   - Reads humanPlayer and coalitionLeader from GameClient
   - All card visibility logic working with new state
   - Build passing ✅

5. **AceAction** ✅
   - Migrated from 3 stores → `useGameClient()` + 1 store for actions
   - Reads pendingAction and players from GameClient
   - Kept `gameStore.selectActionTarget()` and `gameStore.confirmPeekCompletion()` temporarily
   - Build passing ✅

6. **CardSwap** ✅
   - Migrated from 3 stores → `useGameClient()` + 2 stores for actions/swapTargets
   - Reads players from GameClient for name lookup
   - Kept `actionStore.swapTargets`, `actionStore.clearSwapTargets()` and `gameStore.confirmPeekCompletion()` temporarily
   - Build passing ✅

7. **KingDeclaration** ✅
   - Migrated from 2 stores → `useGameClient()` + 1 store for actions
   - Reads pendingAction card from GameClient
   - Kept `gameStore.declareKingAction()` temporarily
   - Build passing ✅

8. **OpponentCardPeek** ✅
   - Migrated from 3 stores → `useGameClient()` + 2 stores for actions/visibility
   - Reads pendingAction from GameClient
   - Kept `playerStore` for temporarilyVisibleCards (not in GameState yet)
   - Kept `gameStore.confirmPeekCompletion()` temporarily
   - Build passing ✅

9. **OwnCardPeek** ✅
   - Migrated from 3 stores → `useGameClient()` + 2 stores for actions/visibility
   - Reads pendingAction from GameClient
   - Kept `playerStore` for temporarilyVisibleCards (not in GameState yet)
   - Kept `gameStore.confirmPeekCompletion()` temporarily
   - Build passing ✅

10. **QueenAction** ✅
    - Migrated from 2 stores → `useGameClient()` + 2 stores for actions/peekTargets
    - Reads pendingAction from GameClient
    - Kept `actionStore.peekTargets` and `gameStore` action methods temporarily
    - Build passing ✅

11. **GameTable** ✅
    - Migrated from 6 stores → `useGameClient()` + 3 stores for actions/visibility/actionContext
    - Reads currentPlayer, phase, subPhase, discardPile from GameClient
    - Maps subPhases to old boolean flags (isSelectingSwapPosition, isAwaitingActionTarget, etc.)
    - Kept `playerStore` for old Player type (temporarilyVisibleCards, knownCardPositions, etc.)
    - Kept `actionStore` for actionContext, pendingCard, swapPosition (complex mapping)
    - Kept `gameStore` for all action methods
    - Build passing ✅

12. **GameInitializer** ✅
    - Migrated from 2 stores + 1 effect → `useGameClient()`
    - Removed `gameStore.initGame()` call (GameClient auto-initializes via GameClientProvider)
    - Reads phase and players from GameClient for loading state
    - Simplified logic - no longer calls initialization, just checks state
    - Build passing ✅

13. **ActionTargetSelector** ✅
    - Migrated from 3 stores → `useGameClient()`
    - Replaced `gamePhaseStore.isAwaitingActionTarget` with `gameClient.state.subPhase === 'awaiting_action'`
    - Replaced `actionStore.actionContext` with `gameClient.state.pendingAction`
    - Replaced `playerStore.players` with `gameClient.state.players`
    - Updated import for `TargetType` from engine types instead of stores
    - Build passing ✅

14. **GamePhaseIndicators** ✅
    - Migrated from 5 stores → `useGameClient()` + 1 store for actions
    - Reads phase, subPhase, players, pendingAction, discardPile from GameClient
    - Maps subPhases to UI flags (isSelectingSwapPosition, isAwaitingActionTarget, etc.)
    - Calculates setupPeeksRemaining directly from player's knownCardPositions
    - Updated child components to accept PlayerState instead of Player
    - Kept `gamePhaseStore.startSelectingPosition()` temporarily for action method
    - Kept `playerStore.getPlayer()` temporarily in nested TossInIndicator for filtering bot actions
    - Build passing ✅

---

## 🎉 State Migration Complete!

All 14 identified components have been successfully migrated to use GameClient for reading state!

**Current State:**
- ✅ All display components read from GameClient
- ✅ All action components read pendingAction from GameClient where applicable
- ✅ knownCardPositions now read from GameClient (PlayerState)
- ✅ **Action methods migrated to dispatch(GameActions.*)**
  - GameTable: drawCard, swapCard, selectActionTarget, participateInTossIn, peekSetupCard ✅
  - GameHeader: updateDifficulty ✅
  - AceAction: selectActionTarget, confirmPeek ✅
  - CardSwap: confirmPeek ✅
  - KingDeclaration: declareKingAction ✅
  - OpponentCardPeek: confirmPeek ✅
  - OwnCardPeek: confirmPeek ✅
  - QueenAction: executeQueenSwap, skipQueenSwap ✅
  - GamePhaseIndicators: finishSetup, finishTossInPeriod, playCardAction, discardCard ✅
- ✅ **UPDATE_DIFFICULTY action implemented in GameEngine**
  - Action type and creator added to GameAction.ts ✅
  - Handler created in cases/update-difficulty.ts ✅
  - Reducer case added to GameEngine.ts ✅
- ✅ **UIStore created for UI-specific state**
  - temporarilyVisibleCards moved to UIStore ✅
  - highlightedCards moved to UIStore ✅
  - showVintoConfirmation already in UIStore ✅
- ⚠️ Some UI-specific state still in old stores (position - player positioning)
- ⚠️ Some action-related state still in old stores (swapTargets, peekTargets, actionContext)

**Known Limitations:**
- `actionStore` still used for: swapTargets, peekTargets, actionContext (complex state not yet in GameState)
- `playerStore` still used for: temporarilyVisibleCards, highlightedCards, position (UI-specific state)

**Next Steps (Future Work):**
1. Move action-related state (swapTargets, peekTargets) to GameState.pendingAction
2. Move UI-specific state (temporarilyVisibleCards, highlightedCards, position) to GameState or keep in UI layer
3. Remove old stores once all state is migrated
4. Clean up DIProvider

---

## 🎯 New Strategy: Complete Migration Per Component

**Principle: Each component fully migrated or not at all**

No feature flags, no hybrid state, no sync bridge needed.

---

## ✅ Approach

### Rules

1. **One component at a time** - Focus on single component
2. **Complete migration** - Component uses ONLY GameClient
3. **All dependencies** - Migrate child components too
4. **End-to-end testing** - Verify works completely
5. **No rollback** - Once migrated, stays migrated

### Benefits

- ✅ No sync complexity
- ✅ No feature flags
- ✅ Clear progress
- ✅ Lower risk (small changes)
- ✅ Can test thoroughly

### Process

```
For each component:
1. Identify component + all its dependencies
2. Migrate component to use GameClient
3. Migrate all child components
4. Remove old version
5. Test end-to-end
6. Move to next component
```

---

## 📋 Component Migration Order

### Phase 1: Isolated Display Components (Lowest Risk)

These read state but don't trigger actions:

**✅ 1. CoalitionTurnIndicator** (COMPLETED)
- Complexity: Very Low
- Dependencies: Avatar component (updated to accept PlayerState)
- Effort: 30 minutes
- Reads: `gameClient.currentPlayer`, `gameClient.coalitionLeader`, `gameClient.state.phase`
- Actions: None
- **Status**: Fully migrated, build passing ✅

**✅ 2. WaitingIndicator** (COMPLETED)
- Complexity: Low-Medium
- Dependencies: None
- Effort: 45 minutes
- Reads: `gameClient.currentPlayer`, `gameClient.state.subPhase`, `gameClient.state.pendingAction`
- Actions: None (display-only)
- **Status**: Fully migrated, build passing ✅

**✅ 3. GameHeader** (COMPLETED)
- Complexity: Medium
- Dependencies: None
- Effort: 1 hour
- Reads: `gameClient.state.{turnCount, roundNumber, phase, finalTurnTriggered, drawPile, difficulty}`, `gameClient.currentPlayer`
- Actions: `gameClient.dispatch(GameActions.updateDifficulty())` ✅
- **Status**: Fully migrated, build passing ✅

**4. GameInfo / Score Display** (Next - Easy)
- Complexity: Very Low
- Dependencies: None
- Effort: 30 minutes
- Reads: `gameClient.state.turnCount`, `gameClient.state.roundNumber`
- Actions: None

**5. DeckDisplay** (Easy)
- Complexity: Low
- Dependencies: None
- Effort: 1 hour
- Reads: `gameClient.drawPileCount`, `gameClient.topDiscardCard`
- Actions: None

**6. TurnIndicator** (Easy)
- Complexity: Low
- Dependencies: None
- Effort: 1 hour
- Reads: `gameClient.currentPlayer`, `gameClient.state.phase`
- Actions: None

---

### Phase 2: Simple Interactive Components

**7. CallVintoButton** (Medium)
- Complexity: Medium
- Dependencies: None
- Effort: 2 hours
- Reads: `gameClient.isCurrentPlayerHuman`, `gameClient.isFinalTurn`
- Actions: `dispatch(GameActions.callVinto())`

**8. PlayerHand (own cards only)** (Medium-High)
- Complexity: Medium-High
- Dependencies: Card components
- Effort: 4-5 hours
- Reads: `gameClient.currentPlayer.cards`, `gameClient.state.subPhase`
- Actions: `dispatch(GameActions.swapCard())`, `dispatch(GameActions.peekSetupCard())`

---

### Phase 3: Complex Interactive Components

**9. GameControls** (High)
- Complexity: High
- Dependencies: Buttons
- Effort: 3-4 hours
- Reads: `gameClient.canDrawCard`, `gameClient.canTakeDiscard`
- Actions: `dispatch(GameActions.drawCard())`, `dispatch(GameActions.takeDiscard())`
- **Note: Already created but needs surrounding components migrated first**

**10. CardActionHandlers** (Very High)
- Complexity: Very High
- Dependencies: Multiple modal/overlay components
- Effort: 6-8 hours
- Reads: `gameClient.pendingCard`, `gameClient.state.subPhase`
- Actions: All card actions (Jack, Queen, King, etc.)

**11. GameTable / OpponentHands** (Very High)
- Complexity: Very High
- Dependencies: Everything
- Effort: 8-10 hours
- Reads: All game state
- Actions: Toss-in participation, etc.

---

## 🚀 Recommended Order

### Start Here: GameInfo (Score Display)

**Why:**
- Simplest component
- No dependencies
- No actions to dispatch
- Just reads state
- Quick win (30 minutes)
- Proves migration works

**Implementation:**

```typescript
// Before (Old)
const GameInfo = observer(() => {
  const { turnCount, roundNumber } = useGamePhaseStore();
  return (
    <div>
      <div>Turn: {turnCount}</div>
      <div>Round: {roundNumber}</div>
    </div>
  );
});

// After (New)
const GameInfo = observer(() => {
  const gameClient = useGameClient();
  return (
    <div>
      <div>Turn: {gameClient.state.turnCount}</div>
      <div>Round: {gameClient.state.roundNumber}</div>
    </div>
  );
});
```

**Testing:**
1. Component renders
2. Shows correct turn/round
3. Updates when game progresses
4. No errors

**Result:** One component fully migrated! ✅

---

### Next: DeckDisplay

**Why:**
- Still simple
- No actions
- Just reads deck state
- Another quick win (1 hour)

**Then: TurnIndicator, CallVintoButton, etc.**

Work up from simple → complex

---

## 🎯 Critical Path: What Blocks GameControls

**Problem:** GameControls dispatch actions, but if other components use old stores, they won't see updates.

**Solution:** Migrate in this specific order:

```
1. GameInfo ✅ (displays turn/round)
2. DeckDisplay ✅ (shows deck counts)
3. TurnIndicator ✅ (shows whose turn)

NOW GameControls has basic UI support

4. PlayerHand ✅ (shows cards, responds to swaps)

NOW GameControls can dispatch actions

5. GameControls ✅ (dispatch draw/take discard)

NOW basic game flow works

6. CardActionHandlers ✅ (handle card actions)
7. GameTable ✅ (full game state)

COMPLETE!
```

---

## 📊 Migration Checklist (Per Component)

### Before Starting

- [ ] Component identified
- [ ] Dependencies listed
- [ ] Current store usage documented
- [ ] GameClient API identified

### During Migration

- [ ] Remove old store hooks
- [ ] Add `useGameClient()` hook
- [ ] Replace store calls with `gameClient.state.*`
- [ ] Replace actions with `dispatch(GameActions.*)`
- [ ] Migrate child components
- [ ] Remove old component code

### After Migration

- [ ] Component renders
- [ ] Reads correct state
- [ ] Actions dispatch successfully
- [ ] UI updates correctly
- [ ] No console errors
- [ ] Manual testing passes

---

## 🔧 First Component: Let's Migrate GameInfo

**Current Location:**
Find the GameInfo or score display component

**Migration Steps:**

1. Find file (likely `game-info.tsx` or similar)
2. Identify what it reads from stores
3. Replace with GameClient equivalents
4. Test
5. Remove old code
6. Done!

**Time:** 30 minutes
**Risk:** Very low
**Value:** Proves migration pattern works

---

## 📝 Code Template

### Pattern for Every Component

```typescript
// ❌ OLD: Multiple store hooks
const MyComponent = observer(() => {
  const gameStore = useGameStore();
  const playerStore = usePlayerStore();
  const { phase } = useGamePhaseStore();

  return <div>{phase}</div>;
});

// ✅ NEW: Single GameClient hook
const MyComponent = observer(() => {
  const gameClient = useGameClient();

  return <div>{gameClient.state.phase}</div>;
});
```

### For Actions

```typescript
// ❌ OLD: Store methods
const handleClick = () => {
  gameStore.doSomething();
};

// ✅ NEW: Dispatch actions
const handleClick = () => {
  dispatch(GameActions.doSomething(playerId));
};
```

---

## ✅ Success Criteria

### Per Component

Component is successfully migrated when:
- [ ] Uses `useGameClient()` hook
- [ ] No old store hooks remain
- [ ] Actions use `dispatch(GameActions.*)`
- [ ] Component works identically to before
- [ ] Tests pass (if any)

### Overall Migration

Migration is complete when:
- [ ] All components use GameClient
- [ ] Old stores can be deleted
- [ ] DIProvider can be removed
- [ ] Game works end-to-end
- [ ] No regressions

---

## 🎯 Next Step: Find GameInfo Component

Let's start by finding the simplest component to migrate.

**Action:** Search for GameInfo, ScoreDisplay, or TurnDisplay component

Would you like me to find and migrate the first component now?

---

*Strategy: Complete migration per component, no hybrid state*
*Order: Simple → Complex*
*First target: GameInfo or score display*
