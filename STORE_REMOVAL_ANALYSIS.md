# Store Removal Analysis

## Current Status: What's Blocking Complete Store Removal

### Summary
- **14/14 components** migrated to read state from GameClient ✅
- **Action methods** mostly migrated to `dispatch(GameActions.*)` ✅
- **Remaining blockers** for complete store removal identified below ⚠️

---

## Components Still Using Old Stores

### 1. **deck-manager-popover.tsx** (Debug/Dev Tool)
**Uses:** `useDeckStore()`
**Why:**
- Debug tool for manipulating draw pile during development
- Calls `deckStore.setNextDrawCard(rank)` and reads `deckStore.drawPile`
- **Not critical** - this is a development utility

**Migration needed:**
- Either remove this debug tool or migrate to use GameClient state + dispatch actions

---

### 2. **own-card-peek.tsx**
**Uses:** `usePlayerStore()`
**Why:**
- Reads `playerStore.humanPlayer` (old Player type)
- Calls `playerStore.clearTemporaryCardVisibility()`
- Checks `humanPlayer.temporarilyVisibleCards.size > 0`

**Migration needed:**
- ✅ UIStore now has temporarilyVisibleCards
- ⚠️ Need to update component to use UIStore instead of playerStore
- ⚠️ Need to convert from old Player type to PlayerState type

---

### 3. **opponent-card-peek.tsx**
**Uses:** `usePlayerStore()`
**Why:**
- Calls `playerStore.clearTemporaryCardVisibility()`
- Checks `playerStore.players.some(p => p.temporarilyVisibleCards.size > 0)`

**Migration needed:**
- ✅ UIStore now has temporarilyVisibleCards
- ⚠️ Need to update component to use UIStore instead of playerStore

---

### 4. **game-phase-indicators.tsx**
**Uses:** `useGamePhaseStore()`, `usePlayerStore()`
**Why:**
- Calls `gamePhaseStore.startSelectingPosition()` (action method)
- Nested TossInIndicator calls `playerStore.getPlayer()` to filter bot actions

**Migration needed:**
- ⚠️ Need to create a GameAction for "startSelectingPosition" or handle this differently
- ⚠️ `playerStore.getPlayer()` could use GameClient.getPlayer() instead

---

### 5. **game-table.tsx**
**Uses:** `usePlayerStore()`
**Why:**
- Reads `playerStore.humanPlayer` (old Player type)
- Reads `playerStore.setupPeeksRemaining`
- Reads `playerStore.players` (old Player[] type)
- Checks `playerStore.players.some(p => p.temporarilyVisibleCards.size > 0)`

**Migration needed:**
- ✅ UIStore now has temporarilyVisibleCards
- ⚠️ Still needs old Player type for PlayerArea component
- ⚠️ setupPeeksRemaining can be calculated from GameClient state
- **This is the biggest blocker** - PlayerArea component needs migration

---

### 6. **modals/coalition-leader-modal.tsx**
**Uses:** `useGamePhaseStore()`, `usePlayerStore()`
**Why:**
- Reads `phaseStore.showCoalitionLeaderSelection` (UI state)
- Calls `phaseStore.closeCoalitionLeaderSelection()` (UI action)
- Reads `playerStore.players` (old Player[] type)

**Migration needed:**
- ⚠️ Move `showCoalitionLeaderSelection` to UIStore
- ⚠️ Need to handle coalition leader selection with GameClient players

---

### 7. **rank-declaration.tsx**
**Uses:** `useGamePhaseStore()`
**Why:**
- Reads `phaseStore.isDeclaringRank` (subPhase check)

**Migration needed:**
- ✅ Can use `gameClient.state.subPhase === 'declaring_rank'` instead

---

## Action Methods Still Using Stores

### GamePhaseStore
- `startSelectingPosition()` - used in GamePhaseIndicators
- `closeCoalitionLeaderSelection()` - used in CoalitionLeaderModal
- `showCoalitionLeaderSelection` (property) - used in CoalitionLeaderModal

### PlayerStore
- `clearTemporaryCardVisibility()` - used in OwnCardPeek, OpponentCardPeek
- `getPlayer(id)` - used in GamePhaseIndicators (nested TossInIndicator)
- `humanPlayer` - used in multiple components (returns old Player type)
- `players` - used in multiple components (returns old Player[] type)
- `setupPeeksRemaining` - used in GameTable

### DeckStore
- `setNextDrawCard(rank)` - used in DeckManagerPopover (debug tool)
- `drawPile` - used in DeckManagerPopover (debug tool)

---

## Biggest Blockers

### 1. **Player Type Mismatch** (CRITICAL)
**Problem:** Many components still need the old `Player` type from shapes.ts, which includes:
- `temporarilyVisibleCards: Set<number>`
- `highlightedCards: Set<number>`
- `position: 'bottom' | 'left' | 'top' | 'right'`
- `opponentKnowledge: Map<...>`

**GameClient provides:** `PlayerState` type which doesn't have these properties

**Solution:**
- ✅ temporarilyVisibleCards → moved to UIStore
- ✅ highlightedCards → moved to UIStore
- ⚠️ position → needs to be added to UIStore or calculated
- ⚠️ opponentKnowledge → this is complex, might stay in old store

**Components affected:**
- GameTable (biggest blocker)
- PlayerArea (nested in GameTable)
- OwnCardPeek
- OpponentCardPeek

---

### 2. **UI State Not in GameEngine** (IMPORTANT)
**Problem:** Some UI state doesn't belong in GameEngine but is currently in old stores:
- `showCoalitionLeaderSelection` (modal state)
- `startSelectingPosition` (subPhase transition trigger)

**Solution:**
- ✅ UIStore exists for this purpose
- ⚠️ Need to migrate modal states to UIStore
- ⚠️ Need to handle subPhase transitions via GameActions

---

### 3. **Helper Methods on Stores** (MODERATE)
**Problem:** Some stores have convenient helper methods:
- `playerStore.getPlayer(id)` - finds player by ID
- `playerStore.humanPlayer` - finds human player
- `playerStore.setupPeeksRemaining` - calculates remaining peeks

**Solution:**
- ✅ GameClient already has `getPlayer(id)` method
- ⚠️ Need to use `gameClient.state.players.find(p => p.isHuman)` instead
- ⚠️ Calculate setupPeeksRemaining directly from knownCardPositions

---

## Migration Priority

### High Priority (Required for Store Removal)

1. **Migrate PlayerArea to use PlayerState** ⭐⭐⭐
   - Currently uses old Player type
   - Biggest blocker for removing PlayerStore
   - Needs temporarilyVisibleCards from UIStore

2. **Update GameTable to use UIStore** ⭐⭐⭐
   - Remove dependency on `playerStore.players` (old type)
   - Use UIStore for temporarilyVisibleCards
   - Calculate setupPeeksRemaining locally

3. **Migrate OwnCardPeek & OpponentCardPeek to UIStore** ⭐⭐
   - Replace `playerStore.clearTemporaryCardVisibility()` with UIStore
   - Replace temporarilyVisibleCards checks with UIStore

4. **Move coalition modal state to UIStore** ⭐⭐
   - Add `showCoalitionLeaderSelection` to UIStore
   - Remove dependency on GamePhaseStore for this

### Medium Priority (Code Cleanup)

5. **Replace GamePhaseIndicators store usage** ⭐
   - Replace `gamePhaseStore.startSelectingPosition()` with GameAction or remove
   - Replace `playerStore.getPlayer()` with `gameClient.getPlayer()`

6. **Migrate RankDeclaration** ⭐
   - Replace `phaseStore.isDeclaringRank` with subPhase check

### Low Priority (Optional)

7. **Remove or migrate DeckManagerPopover**
   - This is a debug tool, can be removed or kept as-is

---

## Recommended Migration Order

### Phase 1: UI State to UIStore ✅ COMPLETED
```
1. ✅ Add showCoalitionLeaderSelection to UIStore
2. ✅ Update CoalitionLeaderModal to use UIStore
3. ✅ Update OwnCardPeek to use UIStore.temporarilyVisibleCards
4. ✅ Update OpponentCardPeek to use UIStore.temporarilyVisibleCards
```

**Status:** All 4 components migrated to use UIStore instead of PlayerStore/GamePhaseStore for UI state!

### Phase 2: PlayerArea Migration (CRITICAL PATH) ✅ COMPLETED
```
5. ✅ Update PlayerArea to accept PlayerState + UIStore
   - Pass temporarilyVisibleCards from UIStore
   - Pass highlightedCards from UIStore
   - Handle position separately (can be prop or UIStore)
6. ✅ Update GameTable to use new PlayerArea signature
   - Calculate player positions based on index
   - Remove playerStore dependency
   - Use UIStore for temporary card visibility
```

### Phase 3: Clean Up Remaining Store Usage ✅ COMPLETED
```
7. ✅ Update GamePhaseIndicators to use gameClient.getPlayer()
8. ✅ Update GameTable to calculate setupPeeksRemaining locally
9. ✅ Update RankDeclaration to use subPhase check
10. ✅ Handle startSelectingPosition - moved to UIStore as UI-only state
```

### Phase 4: Remove Stores
```
11. Remove PlayerStore (once all Player type usage is gone)
12. Remove GamePhaseStore (once all UI state is in UIStore)
13. Remove DeckStore (once debug tool is handled)
14. Remove ActionStore (if swapTargets/peekTargets are in GameState)
15. Remove TossInStore (already migrated to GameState)
```

---

## Technical Debt Items

### Position Property
**Current:** `player.position: 'bottom' | 'left' | 'top' | 'right'`
**Usage:** Determines where to render player on game table

**Options:**
1. Add to UIStore as `Map<playerId, position>`
2. Calculate based on player index (player 0 = bottom, 1 = left, etc.)
3. Pass as prop from parent component

**Recommendation:** Calculate from index (simplest, no extra state)

### OpponentKnowledge
**Current:** Complex Map structure in old Player type
**Usage:** Bot AI tracking of known opponent cards

**Options:**
1. Keep in old PlayerStore temporarily (complex migration)
2. Add to PlayerState.botMemory (better long-term)
3. Create separate BotKnowledgeStore for AI state

**Recommendation:** Keep in old store for now, migrate as separate task

---

## Summary Statistics

**Components migrated to GameClient:** 14/14 (100%) ✅
**Components still using old stores:** 1 file (DeckManagerPopover - debug tool only)
**Action methods migrated:** 100% ✅
**Phase 1-3 Complete:** All production code migrated! 🎉

**Completed in Phases 1-3:**
1. ✅ Update OwnCardPeek & OpponentCardPeek to use UIStore
2. ✅ Migrate PlayerArea to accept PlayerState ⭐ CRITICAL
3. ✅ Update GameTable to use new PlayerArea
4. ✅ Move coalition modal state to UIStore
5. ✅ Update RankDeclaration to use subPhase check
6. ✅ Update GamePhaseIndicators - removed all store dependencies
7. ✅ Move swap selection to UIStore (isSelectingSwapPosition)
8. ✅ Make GameClient.state readonly to prevent direct mutations

**Architecture Improvements:**
- ✅ GameClient.state is now readonly (prevents accidental mutations from UI)
- ✅ All UI-only state lives in UIStore (temporarilyVisibleCards, highlightedCards, modal states, swap selection)
- ✅ All game state changes go through GameClient.dispatch(GameAction)
- ✅ Clean separation: GameEngine (pure) → GameClient (observable) → UI Components

**Remaining (non-critical):**
- DeckManagerPopover - debug/dev tool, uses DeckStore (can be removed or migrated separately)

**Ready for Phase 4:** PlayerStore and GamePhaseStore can now be safely removed! 🎉

---

## Phase 4 Complete: Command/Replay System Removed ✅

**What was removed:**
1. ✅ Command/replay system (GameCommandGroup, ReplayControls, ReplayLoader)
2. ✅ CommandHistory and GameStateManager from DI container
3. ✅ ReplayStore and all related infrastructure
4. ✅ GamePhaseStore, ActionStore, TossInStore exports from DI

**What was added:**
1. ✅ Action history in GameState (`recentActions: GameActionHistory[]`)
2. ✅ Automatic action tracking in GameClient.dispatch()
3. ✅ TossInIndicator now uses GameState.recentActions instead of CommandHistory

**Architecture Now:**
```
GameEngine (pure reducer)
    ↓
GameClient (observable wrapper, readonly state, action history tracking)
    ↓
UI Components → dispatch(GameAction) ONLY
              → read from gameClient.state (readonly)
              → UI-only state in UIStore
              → Action history from gameClient.state.recentActions
```

**Final State:**
- ✅ All production components migrated to GameClient
- ✅ GameClient.state is readonly
- ✅ Action history built into GameState
- ✅ Command/replay system completely removed
- ✅ Build passes with no errors

**Remaining (non-critical):**
- DeckManagerPopover - debug tool (uses DeckStore/PlayerStore)
- Old store files can be deleted when ready (not imported by components)
