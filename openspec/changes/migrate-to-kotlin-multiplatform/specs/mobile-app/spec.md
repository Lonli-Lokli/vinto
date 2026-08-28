# mobile-app

## ADDED Requirements

### Requirement: One Compose Multiplatform app for Android and iOS

The game UI SHALL be implemented once in `composeApp` (Compose Multiplatform,
Material 3) and shipped as an Android app and an iOS app (via `iosApp` Xcode host), sharing
`shared/*` modules unchanged.

#### Scenario: Both platforms build from one UI source

- **WHEN** CI builds `composeApp`
- **THEN** an Android APK/AAB and an iOS framework/app are produced from the same `commonMain` UI code

### Requirement: Game modes

The app SHALL offer: single player vs bots (offline, `LocalGameSession`), create an
online 4-seat room (bots fill empty seats), join a room by code/link, and all-human
online tables of 4. Every game has exactly 4 players; there is no player-count setting. The game screen SHALL be seat-agnostic: it renders for `localPlayerId`
(any seat, not "the human"), and lobby/leader/toss-in prompts address the local seat.

#### Scenario: Create and start a room

- **WHEN** the host creates a 4-seat room, one friend joins and the host starts
- **THEN** both devices show the same public state, each sees only its own permitted cards, and the two remaining seats are bots

#### Scenario: Offline play

- **WHEN** the device has no connectivity
- **THEN** single player vs bots remains fully playable

### Requirement: Feature parity with the web game

The app SHALL support: new game with settings (difficulty, player name), setup-phase peeks, draw / take-discard, use
action / swap (with rank declaration) / discard, target selection for 7–10, J, Q, K, A,
toss-in participation and "continue", calling Vinto, coalition leader selection when a bot
calls Vinto, final-round indicators and coalition status, final scores with the coalition
result, in-game help for card actions, settings changes mid-game (difficulty),
light/dark theme.

#### Scenario: Complete game on device

- **WHEN** a player starts a game and plays until scoring
- **THEN** every phase and action above is reachable through the UI and the resulting recording replays in both engines

#### Scenario: Coalition final round visible

- **WHEN** the human calls Vinto
- **THEN** the UI shows the final-round/coalition status and the bots' coalition moves (swaps, declarations, toss-ins) with the same slower final-round pacing as the web app

### Requirement: Animation-synchronised visual state

Card movements (draw, discard, swap, toss-in, penalty, peeks) SHALL be animated and the
client's `visualState` SHALL only be advanced when the corresponding animation completes;
a reduced-motion preference SHALL shorten animations without breaking the sync contract.

#### Scenario: Bot waits for animation

- **WHEN** a bot's card is animating to the discard pile
- **THEN** the next bot action is not dispatched until the animation completes and visual state is synced

#### Scenario: Reduced motion

- **WHEN** the OS reduced-motion setting is enabled
- **THEN** animations complete near-instantly and the game remains playable and synchronised

### Requirement: Accessibility and responsiveness

Every card, pile and control SHALL expose semantics/labels (rank, owner, position, state)
for screen readers; touch targets SHALL be at least 44×44 dp/pt; layouts SHALL adapt to
phone portrait (primary) and landscape; large system font sizes SHALL not break the table
layout.

#### Scenario: Screen reader

- **WHEN** TalkBack/VoiceOver focuses the human's second card
- **THEN** it announces the card's known rank (or "face down"), owner and position

### Requirement: Recording export, share and local persistence

The app SHALL auto-save the current game's recording locally, restore it on relaunch,
and offer "Export game" (share sheet / file save) producing a v1 `GameRecording` JSON;
a debug screen SHALL list saved recordings and step through them (replay viewer).

#### Scenario: Export via share sheet

- **WHEN** the player taps "Export game"
- **THEN** a JSON file named `vinto-<gameId>.json` is offered through the platform share sheet

#### Scenario: Restore after relaunch

- **WHEN** the app is killed mid-game and relaunched
- **THEN** the last game is restored to its last recorded state

### Requirement: Performance and stability

Bot thinking SHALL not cause dropped frames; the app SHALL start to the home screen in
under 2 s on a mid-range 2022 phone; crashes SHALL be reported to Sentry (Sentry Kotlin
Multiplatform SDK) with the game id and last action index attached as breadcrumbs.

#### Scenario: Frame pacing during bot turn

- **WHEN** a hard bot thinks for up to its time budget
- **THEN** UI animations continue at the display refresh rate
