# The app itself — the phone, the front door, the lesson, the lobby and the endgame

What was built around the game rather than in it: a launcher icon and both orientations, a
menu with a real tutorial behind it, a lobby that says where every wait is, and a final round
that is watched rather than skipped.

Split out of [`README.md`](README.md) — this workspace's index, state and setup — when that
file grew past the size a tool will read in one go. The section numbers are unchanged, so an
older reference of the form `docs/kotlin/README.md §6c` still names the paragraph it meant.

---
## 6f. Putting it on a phone

The solo game is playable on Android today — `./gradlew :androidApp:installDebug` and it is on
the device. What was missing was everything around the game rather than in it, and four of
those are now done. None of them is the Play release (task 8.1 proper): there is still no CI,
no upload key, no track.

**A launcher icon.** The web app's own orange V (`legacy-web/apps/vinto/public/favicon.png`), regenerated
into the three shapes Android has asked for over the years by
`tools/make-launcher-icons.py` — adaptive for API 26+, the legacy square/round pair for the
24–25 the app still supports, and a monochrome layer for themed icons on API 33+. The generated
PNGs are committed; nothing at build time runs the script. It is the same mark as the browser
deliberately: a different icon for the phone would make it a different game to anybody who has
played both.

**Both orientations** (task 7.6, done). The screen's shape picks the arrangement in
`TableLayout.forScreen(width, height)` (`CardScale.kt`): portrait keeps the rail under the felt
on a fixed share of the height; landscape stands the rail beside the felt on the same fixed
share of the *width*, because the portrait rail's own minimum height is most of a rotated
phone's screen. The felt is the same four-sided table in both — only the join moves, and the
final-round banner rides at the head of the side rail where the felt has no height to spare.

Landscape spans three very different machines, and two numbers change with the screen rather
than the platform: the cards **step up** to a third size (`Grand`) on the felt heights only a
tablet or desktop has — a portrait tablet lands on the same step — and the felt's width is
**capped** (by aspect, and absolutely) with the felt-and-rail group centred in what remains,
so a desktop table keeps a table's shape on the app's dark surround instead of stretching the
seats to opposite horizons. A rotated phone has no width to spare, so there the felt still
takes everything beside the rail.

The Android manifest no longer locks orientation; iOS always allowed rotation (it squeezed the
portrait design until this landed) and the browser was always free. `LandscapeTableTest` holds
the rotated phone to the same bar `CrowdedTableTest` holds the upright one to, and holds the
desktop window to the cap and the centring.

**A window theme of its own** (`values/themes.xml`). It was inheriting
`Theme.Material.Light.NoActionBar`, which meant dark status-bar icons over a dark rail and a
white flash before the first composition. `Theme.Vinto` is dark Material with the rail as its
window background, so the bars carry the light icon set by inheritance rather than by
overriding a per-API flag, and the cold-start frames are the colour of the app.

**A release variant that assembles anywhere.** `assembleRelease` signs with the upload key
named by `keystore.properties` when that file exists, and with the debug key when it does
not. The fallback is the point: a release build that fails on a missing secret is one that goes
untested until the day it has to work. A debug-signed release APK installs and plays; it cannot
be published, and cannot be upgraded in place by a properly signed build later, because Android
treats a change of signing key as a different app.

To sign it properly, create the key once and write `keystore.properties` (gitignored, and
it names the keystore rather than containing it):

```bash
keytool -genkeypair -v -keystore ~/keys/vinto-upload.jks -alias vinto \
  -keyalg RSA -keysize 4096 -validity 10000
```

```properties
storeFile=/Users/you/keys/vinto-upload.jks
storePassword=...
keyAlias=vinto
keyPassword=...
```

## 6g. The menu, the settings and the lesson

The app opened straight onto a title and two buttons, because single player was the only
thing there was. It now has a front door, and three things behind it.

**Home.** A fan of five real cards from the game's own deck deals itself in behind the
wordmark, and under it a panel holding the mode that is finished: single player, its
difficulty on show rather than buried, and one button to a table. Below that, the three
things that are not a game — online, the lesson, the settings.

The arrangement follows what the premium card apps have converged on rather than what a
settings-first Android app does. Two ideas were worth taking. Marvel Snap's UI team put it
as *the cards take precedence in the visual hierarchy and the interface exists to highlight
them*, which is why the menu is made of the deck instead of illustrated with it. The poker
lobbies (Zynga, PokerStars) are built so the table everybody came for is one tap away and
nothing is between you and it — hence the difficulty chips sitting *in* the play panel
rather than behind a settings screen.

**Online is a button that plays.** It was a dialog explaining what existed and what did not,
back when this app's half was the missing one; phase 9 built that half, and the button now
opens the real thing: a name, a room code, a public/private choice, and a browser for the
rooms that chose to be listed.

Three things were added once it was possible to sit at a real table over a real network, all
three of them about the gap between touching something and it having happened:

- **A spinner of the app's own** (`theme/Progress.kt`), because Material's ring on felt reads
  as a form submitting. It is drawn like everything else here — a track and a brighter sweep,
  round-capped, in the ink of whatever it sits on — and it honours reduced motion by *not
  moving*: the still version is a complete ring at the sweep's weight, which is visibly a busy
  indicator and never a frozen animation. `GameButton` takes `busy`, which swaps its label for
  one and swallows its own taps; "create room" pressed twice is two rooms.
- **Local before global.** A pending seat spins on *that seat* (`RemoteRoom.pendingSeats`,
  cleared by the next lobby broadcast or a five-second timeout), the connection badge spins
  instead of showing a settled dot while it is still trying, a move on the wire says so under
  the heading, and only a first load of the public list takes the middle of the screen — a
  refresh keeps the list and puts a small one beside the title, because taking a list away
  from somebody reading it is the rudest thing a lobby can do.
- **An invite, not a code to transcribe.** The lobby shows the code monospaced and spaced out,
  with Share and Copy over the `shareText`/`copyToClipboard` seam — which now has real
  implementations on the desktop clipboard, the browser's Web Share and clipboard, and iOS's
  pasteboard, where three of the four used to answer `false`. Where a platform can do neither,
  the code is still on the screen and a line says to read it out.

**Settings** are four choices, each written as what it *does*:

- **Bots**, the difficulty, shared with the home panel.
- **Pace** — calm, steady, brisk — a single multiplier over every duration in the animation
  layer, so the movements and the pauses keep their proportions at either end of the dial.
  It exists because the right speed is not the same for somebody learning the game and
  somebody on their twentieth round.
- **Theme**, because a phone's night setting is not always the one you want at a table.
- **Haptics**, and see below.

They live under their own key in the same vault as the saved game, deliberately not inside
`SavedGame`: a preference outlives the round it was set in, and abandoning a game must not
reset a speed the player has already decided they dislike.

**How to play is a real round, on a deck somebody arranged.** Not a page of rules, and not a
scripted walk with every button but one disabled — a tutorial that refuses your moves teaches
the sequence rather than the game, and has to say no the first time you deviate.

The design came out of a session with the `fable` model against the repo (`VINTO_RULES.md`,
`SCENARIOS.md`, the client sources); what shipped follows it closely. Five parts:

- **A stacked deal, planned move by move.** `initializeTeachingGame(deck)` takes a deck order
  instead of a seed, and refuses anything that is not a permutation of the real 54 cards — so
  the lesson cannot deal a hand that could not have been shuffled. `TeachingDeal` writes the
  order down for an eight-turn round in which the learner does each thing the game has once
  and watches the bots do the rest (product owner's brief, after playing the first version):
  turn 1 you draw a 4 and swap it for the 7 you peeked, naming it, and its look finds a King;
  turn 2 Raph puts a 4 down and you throw yours in beside his; turn 3 Mikey plays a 9 at one
  of your cards; turn 4 Don throws a Queen away unplayed; turn 5 you take that Queen off the
  pile, look at your one unread card and Raph's first, trade your 8 for his Joker — and call,
  on two Jokers and two Kings, which no hand can get under; then Raph's Ace, Mikey's King and
  Don's 9 are played in the final round where you can watch them. Seed search was considered
  and rejected: the constraints are joint over a dozen named positions, and a seed found today
  would be silently invalidated by the next bot or engine change. The recording contract is
  untouched, because `GameRecording` carries `initialState` in full and `replayRecording`
  starts from it. `TeachingRoundTest.theRoundRunsAsTheCoachTellsIt` walks the whole line by
  following the coach's pointer and asserts every claim above against the engine.
- **A director for the bots.** The deck says what a bot *draws*, not what it does. A
  `BotDirector` on `LocalGameSession` may name a bot's move before the search is asked;
  whatever it names still passes `ActionValidator`, and a refused move falls through to MCTS —
  a script that has drifted costs the lesson its shape, not its playability. Bots draw rather
  than take, so the written-down deck lands; what they draw they *play* if it is a 9, a King
  or an Ace — each aimed by the script, the King at a plain card the bot knows it holds — and
  put down otherwise, which is how the Queen reaches the pile. And, if the learner plays on
  instead of calling, **Don calls Vinto** late, so the round still ends while they are
  watching. Left alone a bot will not call inside a short round: the rule wants eight
  rotations, a fully known hand and a total of zero or less.
- **A coach derived from the position.** `lessonFor(view, table, taught)` is a pure function:
  it reads the table and says what is in front of the player. That is what lets every legal
  move stay legal — deviate and it simply talks about wherever you got to. Ordered talk beats
  are the exception (the object of the game, the tour, the Vinto call, the coalition, the
  scoring), because explaining scoring before the round is scored is not explaining.
- **A pointing hand.** One white outlined arrow, at one thing, from just outside it: a card, a
  seat plate, a rank chip, a button, the deck badge, the log box, the "?". White because every
  other colour on this table already means something. It points down from above by default —
  pointing up from below sits it on the next rank chip in the grid, naming the wrong card — and
  from inside the left end of a full-width button, where it cannot cover the button below.
- **The deck first, card by card.** Before the table is toured and before a card is dealt with,
  the lesson holds up every rank in the order they get harder: the plain 2–6, the two that look
  at one of yours, the two that look at one of theirs, then the three that nobody works out by
  watching — the Jack that swaps blind, the Queen that looks first, and the King. The King gets
  three beats, because it is the one nobody works out and it has two separate ideas in it.
  First what it does. Then **what you name is what you get to play** — name a 7 or an 8 for a
  look at one of your own, a Jack for its blind swap with *you* choosing both cards (which is
  how a Joker you have spotted comes to you and your worst card goes the other way), a Queen to
  look at two before trading. Then **whose card you name**: the named card leaves that hand, so
  your own 10 comes off your total and an opponent's Joker comes off theirs. Neither idea is
  derivable from the rules text; both come from reading `handleDeclareKingAction`.
- **Cards explained as they are met, with the card.** The first time a rank becomes visible,
  the coach gives its name, value and action in `CARD_CONFIGS`' own words — beside the actual
  picture, dealt from the same art the table uses. The help sheet's gallery does the same, as
  the web app's card reference already did: a player who learned "Q" from a list still has to
  match it against a picture on the felt, and showing the picture skips that step.

The Call Vinto button is hidden until the coach has something to say about it: the hand is
one to call on — every card seen, the total at or below zero, `readyToCall` — or a bot has
called first. The taught round reaches the first at the end of the second turn, and the
`CallNow` beat points at the gold button over the toss-in window that offers it; the endgame
beats then come from the caller's chair (`YouCalled`, `CoalitionAgainstYou`), with the
bot-called versions kept for a learner who played on. The fallback call is timed on
`turnNumber`, which counts *turns* and not rotations — the first version called it on turn 4,
which is the third bot's **first** turn, and ended the lesson before the player had taken a
second one.

**The coach reads memory wherever a card's face matters**, not only for the swap advice: the
rank chip it points at for a declaration, the card it points at in a toss-in window, and the
slot a peek should be spent on all come from `rememberedHand`. Each of the three was reading
the view, which shows none of your cards after the setup peeks, so each pointed at nothing or
at card one — the 7 went down unnamed in the first round anybody played. A declaration on a
slot never looked at gets `DoNotGuess` instead. A toss-in is only pointed at for a card worth
being rid of: a King costs nothing to hold, and throwing one in buys a declaration.

**Somebody else's turn points at nothing** — the arrow at the box of recent moves for as long
as the gloss was unsaid was an arrow at a paragraph while three bots moved cards over it — and
the shut coach's one line names a bot's action card as it is played: "Mikey plays the Nine —
Peek at one card of another player", from `CARD_CONFIGS`. That line, and the final round's
Ace, King and 9, are the learner's only sight of the three cards the round never puts in
their hand.

**The coalition's play is explained as it happens.** Two more talk beats after the learner's
call, held over the felt: when the coalition names whose hand it plays (`CoalitionLeader`),
and each time a member has an action card in play (`FinalPlay`, once per rank) — the frame
with the card engaged is drawn, the stage holds, the rule is read, and "Go on" lets the card
do its work. The taught round has the bots play an Ace, a King and a 9 there, which are the
three cards the learner never holds. The rail also shows the card a peek *turned up* rather
than the card that bought the look (the 8 was held up and explained while the Joker it had
found lay on the felt), and a spent discard keeps its face while the window it opened is
still the viewer's to answer — a card back under "the 8 went down — toss in a match?" read
as an empty pile — and turns over when they press Continue.

**A talking coach is sized to leave its target uncovered.** It picks whichever end of the felt
leaves more room clear of the pointer's target and shrinks its body to fit that room, down to
a floor it scrolls inside. It used to pick an end by which half the target was in and keep the
full body regardless, and on a phone whose felt is barely taller than the coach that put the
seats beat over the very plate it was pointing at. The body is the fixed height only for a
beat that points at nothing, which is the card tour, where "Go on" staying put matters most.

It also makes **one bot throw a card into a toss-in window**, once, the first time a bot is
holding a match it has actually seen. The window is the one moment in Vinto that belongs to
the whole table at once, and a player whose window only ever contains themselves learns it as
"a prompt I dismiss"; when it happens the coach names whoever did it. "Has actually seen" is
the bots' own rule rather than a convenience — guessing costs a penalty card and bars you from
the rest of the round, so a bot tossing a card it had not read would be demonstrating bad play.

The coach also answers, once and without reproach, the first time somebody presses a button
other than the one being pointed at. That press is a player quietly testing whether this is a
real game or a rail, and it deserves an answer.

Three things learned by tapping through it on a phone, all of them invisible in code review:
the coach's box is a **fixed** height while it talks, so "Go on" does not move under a thumb
that has twelve beats to press; the cards sit directly under the title rather than after the
words, so a long paragraph cannot push them below the fold; and `**markdown**` in the copy
reaches the player as literal asterisks, because Compose's `Text` is not a renderer.

Two things are deliberately not free: the lesson runs at no less than **calm** pace whatever
the setting says, and **Call Vinto is hidden until a bot calls it** — the one tap that ends the
lesson before it starts, cannot be undone, and means nothing yet to the person pressing it.

**The coach floats above the rail rather than living in it.** It began inside the control
panel, which was wrong in a way only a phone shows: the panel became as tall as a lesson and
the felt as short as whatever was left, so four hands, two piles and three name plates ended up
crushed into a third of the screen with the side seats' cards re-flowing into rows. A tutorial
that deforms the game it is teaching is teaching a different game. It is now a card over the
top of the felt, and the table underneath keeps exactly the layout it has in a real round.

It is also **shut while the game is being played** — one line and the progress dots, one tap to
open — because everything under it is something the player has to see and touch. A talk beat
opens it, since the table is held for it anyway.

**Where it lies is decided by what it is pointing at.** Two reports from a phone: the tour
beat pointed at the discard from under the coach's own edge, and the seats beat pointed at a
plate the coach was lying on; and once play started, the shut coach at the top covered the
opposite seat's whole row for the length of the round. So a *talking* coach goes to whichever
end of the felt is away from its pointer — the top by default, the bottom when the target's
centre is in the felt's upper half, measured from the stage's own bounds — and a *playing*
coach lies in the band of empty felt between the side seats, under the piles and above your
hand, measured the same way. In the band the dots go under the title rather than beside it,
because a title beside nine dots in 200 dp is a title wrapped to four lines. If the table has
not been measured yet or leaves no band worth the name, the top is the fallback.

**Nothing on the table can be touched while it talks.** `Table.heldStill()` strips the taps,
the buttons, the seats and the rank chips for as long as a talk beat is up. The stage is held
for the beat, so a move made during one would be made against a table the player has not
seen the last moves of — and the first thing a newcomer did with five breathing cards under
the welcome was tap one, which peeked it under the paragraph.

**It knows what you have seen.** The swap advice used to read the *view*, which after the setup
peeks shows none of your cards — the view hides what you have seen, on purpose — so every
card scored the same and "give up your worst card" pointed at card one whatever it was.
Reported with the exact hand: a 3 and a 7 peeked, a 4 drawn, the coach pointing at the 3.
`LocalGameSession.rememberedHand()` now hands the coach the seat's `knownCardPositions` with
the cards at them — what the player has looked at and nothing they have not — and the advice
has three answers in order: the highest known card worse than the drawn one (the 7, which the
declaration beat then has you name); failing that, a slot never looked at, provided the drawn
card is a 5 or lower (the deck averages about five and a half); failing that, go back and
throw it away. The keep-or-throw beat points at Swap or Discard by the same reading.

**The dots count the intro while the intro is being read.** Fourteen talk beats come before a
card is dealt with, and the chapters are met by playing, so the row of nine did not move
through fourteen taps of "Go on". `INTRO_BEATS` is the run, `introStep` says where a lesson is
in it, and the coach draws one dot per beat until the table is handed over. A chapter is also
met when its lesson is *heard* now, not only when a move proves it: the call and the scoring
are taught in words over things a bot does, and a player who never called Vinto themselves
used to finish with those two dots empty.

**Fixed heights, so nothing blinks.** The talk body is a fixed height rather than a ceiling —
it was documented as fixed and implemented as `heightIn(max)`, so the box grew and shrank
with every paragraph — and the row of held-up cards is a fixed height too, with the cards
centred and as large as the row allows (one card fills its height; five share the width). The
last cause was on the web only: `painterResource` answers an empty painter until the drawable
has loaded, and an image sized from an empty painter is zero pixels tall, so every card the
lesson held up drew its row collapsed and popped it open a frame later. `CardPicture` states
the deck's aspect now instead of reading it off the painter.

**The opening says the game is memory.** A second beat, `Teaches.Memory`, before the card
tour: you see two of your five and the rest only if a card lets you, so the round is played on
what you remember, and a 9 or a 10 that bought you a look was a fair trade early on. The old
opening said every card counts and stopped, and the plain-cards beat then called small cards
"what a winning hand is made of" — which is untrue: the hand that wins a round is usually at
zero or below, and a coalition can nearly always reach that (product owner). `LessonCopyTest`
pins both corrections.

The old note about the panel's reserved height, kept because it is why the coach was ever put
there:
Stacking it above cost the felt 150 dp and the side seats' hands re-flowed into rows — the
lesson was being taught on a table that was not the one being learned. It is bounded and
scrolls inside its own box, so a King's fourteen rank chips plus a three-line prompt plus a
lesson cannot squeeze the felt out of existence.

**A wrong toss-in shuts you out of that card, not the round.** It used to be the whole deal in
every phase, which is what the rules note in `VINTO_RULES.md` recorded — and it meant one wrong
read on the second seat's discard cost a player every window until scoring, including windows
opened by cards they could not have known about when they guessed. Reported by the product owner
from a real round: the toss-in is the one moment that belongs to the whole table at once, and a
player shut out of it for ten minutes stops touching it. The **final round** keeps the long
version, because there the coalition plays one hand against the caller and there is no later
window to earn it back in.

It is a rule in `ActionValidator` and `projectView`, not in state: `isBarredFromTossIn` chooses
between `activeTossIn.failedAttempts` and `roundFailedAttempts` by phase. `roundFailedAttempts`
still records every failure for the whole round — it is history, and it is inside the canonical
hash — so the frozen parity corpus is untouched, and `CorpusReplayTest` stayed green without a
fixture being regenerated. A validator that refuses *less* can never reject a recorded action.
`TossInBarTest` pins both halves, including the case that prompted it: failing on one bot's
discard does not bar you from the next bot's.

**Haptics.** Three kicks and no more: something touched, a move committed, a rule bitten —
that last only for the hand it happened to, since a buzz for a bot's penalty is a buzz for
something that is not your problem. Off is one setting away, which is what keeps the three
that remain meaningful.

**Settings are reachable from the table, not only from the front door.** The header's gear sits
beside the "?" and the bug, and it carries the way back with it: `Screen.Settings` holds the
screen it was opened from, so closing it returns to that exact table — the same `LocalGame`,
mid-round, nothing re-dealt and no socket re-opened. It is the same for the lesson and for an
online room, and the system back button follows the same route.

The reason is one setting: **pace** is the thing somebody wants to change *while* a round is too
slow to sit through, and it lived where changing it meant abandoning that round. Nobody pays
that price; they put the phone down instead. Theme and haptics are the same shape of want.
`HeaderControlsTest` pins both halves — the gear opens the settings, and coming out of them
lands back on the table rather than at the front door, which is the half that is easy to get
wrong and impossible to see in a diff.

**Back works.** `SystemBack` is an `expect`/`actual` around Android's `BackHandler`; the other
targets no-op and use the on-screen button. Without it, back from the settings screen closed
the app, which looks exactly like a crash.

### What the lesson covers, against the rules

Audited beat by beat against `docs/game-engine/VINTO_RULES.md`:

| Rule | Where it is taught |
| --- | --- |
| Objective — lowest hand wins | opening beat |
| You cannot look at your own cards; the round is played on memory | the second beat |
| Four players, five cards each, face down | opening beat |
| Peek at two of your own, once | setup lesson, pointed |
| Every rank's value and action (2–6, 7·8, 9·10, J, Q, K, A, Joker) | eight card beats, each holding up the cards |
| King: names any card, right takes it out of that hand and gives you its action | three beats, with the worked example; then Mikey's King, watched in the final round |
| Option A — draw from the deck | turn lesson, pointed |
| Keep it, throw it, or play its action now | keep-or-throw lesson |
| Declare the rank you put down; right plays its action, wrong costs a card | declaration lesson, pointed at a rank you have seen |
| Option B — take an unused action card off the pile and play it at once | turn lesson, pointed at Don's Queen on turn 5 |
| Toss-in: anybody may match the rank; wrong costs a card and bars you | toss-in lesson, plus a bot demonstrating it |
| Calling Vinto at the end of your own turn | the "call it" beat, over the window at the end of the learner's second turn |
| Final round — one more turn each | the call beat |
| Coalition — best single hand counts, caller's cards untouchable | the coalition beat, from the caller's chair |
| Scoring — +3 / −1 / level counts as the caller's | the scoring beat, pinned by a test |
| A session is rounds; 5 / 3 / 2 game points by rank | the session beat |
| The deck running out and the pile going back into it | help sheet ("what the table is telling you") |

Re-audited against the **official composite PDF** (the 4-page rules document) rather than only
against the repo's markdown, which turned out to be wrong in places. Three more rules were
added to the lesson as a result: the coalition **may confer and pool what they know**, the
**?** is the reminder card the boxed game ships one of per player, and a session is played to
**a clock agreed beforehand** before the 5 / 3 / 2 game points are awarded.

Every difference between the engines and the official text has now been decided, and the table
recording those decisions is at the foot of `docs/game-engine/VINTO_RULES.md`. Three closed as
"the PDF is loose and the engines are right" (Jack/Queen targeting, tossing in on your own turn,
an Ace off the discard); one was a real bug and is fixed in **both** engines — a wrong toss-in
no longer clears itself after one lap of the table.

That bar's *lifetime* has since been decided again, against play rather than against the text:
it is the window you guessed wrong in, and the whole round only in the final round. The reasoning
is under **Haptics** above and in `VINTO_RULES.md`, whose decision table records the reversal
rather than quietly replacing the old line.

Two things worth knowing about that table:

- The scoring line **was wrong** until it was audited: it said a caller who finishes lower
  takes +3 "while the rest take nothing", when the rules and `calculateRoundPoints` both charge
  the others a point each — nothing is what a *tie* costs them. `TeachScriptTest` now pins all
  three outcomes, because a tutorial that teaches a scoring rule incorrectly is worse than one
  that skips it: the player believes it.
- An earlier pass recorded a deviation here that was not one: the repo's markdown said the deal
  places a card face up to start the discard pile, and the engine does not. The **official PDF
  agrees with the engine** — "The Discard Pile is formed by the first card played or discarded"
  — so the markdown was the bug, and it has been corrected.

**A hand too wide for one row wraps, and steps down a size first.** Five cards is the deal and
not the limit — a wrong guess, a wrong toss-in and an Ace each add one, and only the end of the
round takes any away — so eight in front of a player is an ordinary way to be losing. Eight did
not fit, and what the line did about it was slide them over each other until they did. Past about
seven that stops reading as a hand of cards: the backs are a repeating pattern, so the seam
between two overlapping cards is invisible, and a player with eight of them cannot count their own
hand, let alone aim at a card in it. It was reported from a phone, with a screenshot, which is the
only way this kind of thing is ever found.

It now does what the web client did (`legacy-web/.../horizontal-player-cards.tsx`: `flex-wrap`,
with the card size chosen from the count) — and both halves are needed. Wrapping alone was tried
before and taken out, because a second row of full-size cards doubles the seat's height and
squeezes the felt until the side seats have a single card's height to lay nine cards in. So the
cards step down one size first, to `CardScale.crowded()`, and one size only: a hand that resized
by a few percent every time a penalty card landed would be a table that never looks the same
twice.

The step is **the tap floor**, 44dp, and that is not an arbitrary choice of a smaller number.
`CardFace` reserves 44dp of footprint whatever size it draws the picture — `TouchTargetTest`
measures it — so shrinking the art below that buys no room at all; it just draws a small card in
a box that did not shrink. That is also the whole reason wrapping is the answer rather than more
shrinking.

The three seats opposite keep overlapping. Their cards are counted rather than read, and the
felt's *width* is the scarcest thing on a phone, so a second column costs more than it buys.
`CrowdedTableTest` holds both: nine cards a seat, every card on screen and tappable, and your own
hand with no card lying over another.

**The rail is a fixed share of the screen** — `railHeight(screen)`, a third of it, clamped
between 240 and 300 dp — and the felt is exactly what remains. Not a floor the contents can
push past, which is what it was: a King's fourteen rank chips arrived and the table shrank,
they went and it grew back. Animating that made it a slide rather than a jump, which is a nicer
way to move something that should not move at all.

What adapts now is the panel's contents rather than its height: the box of recent moves stands
aside when a rank grid needs the room, and the column scrolls as a last resort so a large
system font cannot push a button off the bottom. Measured on the device across five panel
states — a toss-in prompt, a two-line prompt with a two-line log, bots playing, a turn, a drawn
card — the felt's bottom edge stayed at the same pixel in every one.

**And the rail's own boxes are fixed too, since 2026-09-02.** The scrolling column above was
the design's last resort and had become its first: on a phone taller than the one the rail was
drawn on, a prompt, a four-line log and two stacked buttons overran a 270 dp rail by forty, and
"Leave them" sat half under the edge of the screen in three screenshots running. The rail is
two columns over a foot now (`ControlPanel.kt`): the choices pinned to the foot, in **one row**
rather than a stack — a phone has width to spare where it has no height; and above them one
block that fills the rest, with **the card being decided about on the left, as tall as the
block** — the web table showed the drawn card large in its panel, and the felt's in-play slot
says where a card is rather than what — and beside it the prompt, keeping two lines' room
whether or not the rule under it is still being said, with the box of recent moves under it
taking whatever the prompt leaves. Nothing is a fixed depth that could leave a strip of rail
empty above the buttons, and nothing moves between one move and the next on the same phone,
which is what a fixed box is for. Nothing outside the foot scrolls, so a button cannot be
pushed off the rail; the prompt scrolls within its own room and a King's fourteen chips within
theirs, and the foot may never take the prompt's first line. `RailFitsTest` measures both
choices *clipped* on the test phone, on a 20:9 one, and at a doubled font, which is the
failure exactly, and that the card is drawn beside the prompt.

Four more, from a play session on the phone the same day. **The foot keeps one row's room
with nothing to press**, so the log is the same size on a bot's turn as on yours; **Call
Vinto shares the row** in its own tone rather than sitting under an "or", which was a second
row on exactly the turns that also had a rule to show. **The card in the rail is the card the
prompt is about**, whoever's it is — a bot's Queen being aimed, or the action card on offer
from the pile — where it used to be the viewer's own and nothing else, which read as the rail
sometimes showing a card and sometimes not. And **the log folds one actor's run of moves onto
one line**, joined by an arrow, so a turn reads as one thing that happened and the last line
grows in place rather than pushing the others up. A tap on a card on the felt — the one drawn,
or the top of the pile — opens the help sheet on that card alone; the "?" still opens all of it.

And four from the session after that. **The card's column is there for the whole of your
turn and not for a bot's.** It first came and went with the card, which moved the prompt and
the log sideways on every move; then it was kept always, with an empty slot, which put a
large empty rectangle beside "Look at two of your cards" and a bot's Jack beside "Mikey is
playing" — a rail that changed shape with every seat's every move. Now the column exists
exactly while the turn is yours, a toss-in window included, and always holds a card: the
card in play, else the top of the pile (which is what the turn is about before the draw,
after a swap and in the window), else the deck's back. On a bot's turn the words take the
width. The one change of shape left is at the turn boundary, which is a change of *whose*
turn it is and reads as one. **Any card the rail shows is explained under the prompt**,
name, points and action. **The log keeps two turns** — this one and the one it is answering
— where it kept the whole round, which was a transcript to scroll rather than a table to
read. And **a draw is narrated with its rank to every seat**, as the felt already shows it:
the rules reveal a drawn card publicly, and a log that named it to the drawer alone was
hiding a Joker the whole table had just watched come off the deck.

### While a Jack or a Queen is being aimed, the column holds what it is aimed at

The column above always holds the card in play, and for one action that was the wrong card.
Playing a Queen filled it with the Queen — which the player had chosen to play a moment
earlier and could read off the discard where the felt draws it — while the two cards being
swapped, the actual decision, were one line of small text under the prompt: "Chosen: You,
card 3 and Don, card 5". A player who had used both this table and the web one it replaced
said so in as many words: on the old screen you could see more about the cards being changed.

So a two-card action carries its aim (`Table.aim`), and the column draws it: two slots side
by side with the mark that says they exchange places, each holding a card once one has been
chosen and a card-shaped outline until then. **The face is whatever the projection handed
over** — a Queen looks before it swaps, so its targets arrive face-up; a Jack does not, so
they arrive as backs — which is what keeps the rail from becoming a second place the
redaction rule has to be right in. Each slot is named underneath, "You, card 3", and a chosen
card wears the same gold ring it wears on the felt.

Three departures from the web screen this restores, because it was not right either. **Both
slots are drawn from the moment the action starts**, empty, rather than appearing as they
fill — the column is one shape for the whole action, and two empty slots say "two cards are
wanted" better than a counter does, which is why there is no "1/2". **The pair takes the same
share of the width one card took**, so the prompt's column does not narrow when an action
starts. And the face comes from the view rather than from a rule written twice: the web
component hard-coded "a Jack shows backs", which was true and was still a second opinion
about redaction.

What the card in play used to say by being there, the words say now: `WhatTheCardDoes` under
the prompt, which fades on the usual terms (`worthSaying`) for somebody who has met it — and
that line names and prices the card, the same sentence `rail_card_action` writes beside a card
that *is* drawn, so it does not change shape when the picture comes and goes.

### When the table asks which *player*, the players are on the rail

Two moves name a person rather than a card — an Ace, which makes somebody draw, and the
coalition choosing who plays its hand — and for both, the only way to answer was to tap that
seat's plate out on the felt. Nothing said so. The rail asked "Who draws a card?" and offered
one button, "Put it down", so the screen read as a question whose only answer was to give up;
the plate did take a gold ring, but gold is also what a plate wears when it is simply
somebody's turn. Every other question the table asks is answered on the rail, so this one was
the odd one out, and the report was the plain one: *now I have to understand that I need to
click on a person icon*.

`Table.seats` carries them and `SeatGrid` draws them in the foot, where the King's ranks go and
in the same amber, because it is the same verb: name one of these. Never more than three to a
row — the table is four seats and one is always excluded, the player themselves for an Ace and
the caller for the coalition — and a narrow side rail wraps them the way it wraps ranks.

**Each button wears that seat's portrait, above the name and at the size the web table drew
it** (56dp; the web's `Avatar size="md"` was 64 CSS px). That number is the whole point of the
control: at the 26dp it started at, beside the label rather than above it, the artwork was a
coloured dot and the row read as three words again. Stacking it also gives the name a whole
line, which is what stops a long one being cut to three letters. `GameButton` grew a `stacked`
slot for this rather than a second button being written, because everything else about it — the
bevel, the tone, the press, the spoken label — is what makes it that button.

**And the whole hand is the target, not the chip beside it.** An Ace lands on a *hand* — the
victim draws a card nobody chooses — so every card of every opponent sends the same move, which
makes the target five cards wide instead of one plate and lights the hand up, since a tappable
card already wears the ring that says so. Aiming at a seat plate alone was a small target
carrying a gold ring that also means "it is their turn".

The portrait needs a mapping that can say **no**. `portraitFor` cannot: it falls back to
Leonardo, which is right for the round hole in a felt plate and wrong here, because a stranger
wearing Leonardo's face is worse than a stranger wearing none — and online a seat is whoever
typed their name in, with no portrait to carry. So `portraitOrNull` is the honest half (the
four the offline game deals, plus the "You" seat the felt already draws Leonardo on) and
`portraitFor` is the same thing with the fallback, for the felt.

**And the card asking gives its column up**, as it does for an aim. The player drew the Ace,
read it and chose to play it — three screens with the card on them — and the rail then spent
its one card of room on it for a fourth. What is undecided is who, so the room goes to the log
and the answer goes on the foot.

The felt still answers: the plates are still tappable, and tapping one is the better gesture
once you know it is there. What is new is that the rail says the choice exists.

## 6j. Finding a table, getting somebody to it, and saying so while you wait

Three things that the online client had no answer for, and one bug found while giving it one.

### Every wait now says where it is

The app had no progress indicator of any kind — a `grep` for one found nothing — so a tap
that crossed a network looked exactly like a tap that missed. `theme/Progress.kt` draws one
the way the rest of the table is drawn: a track at low opacity with a brighter round-capped
sweep riding it, in the ink of whatever it sits on. Material's own ring on felt reads as a
form submitting.

**Reduced motion is honoured properly**, which means "no movement, same information": the
still version is a *complete* ring at the sweep's weight, visibly a busy indicator rather
than a frozen animation, and the animated branch is a separate composable so the frame clock
is never started for somebody who asked for stillness.

Local before global, wherever the wait belongs to one thing. Every call that crosses the wire,
and what it draws:

| what is waiting | what it draws |
| --- | --- |
| `POST /rooms` — opening a room | the button, busy: label swapped for a spinner, taps swallowed |
| `GET /rooms` — the public list, first load | the middle of the screen |
| `GET /rooms` — a refresh | a small one beside the title; **the list stays** |
| the socket opening or re-opening | the connection badge spins instead of showing a settled dot |
| the lobby before its first broadcast | the space the seats will fill, held open, with a spinner in it |
| adding or removing a bot | on *that seat*, and the add button goes busy |
| a move over the socket | one line under the prompt |
| agreeing to the next round | the strip under the felt, saying what it is waiting for |

`GameButton` grew `busy`, which swaps the label and swallows its own taps. That is not
politeness: "open a room" pressed twice is two rooms, "add a bot" pressed twice is a table
with a bot nobody asked for — and the second press is the fault of the first having looked
like nothing happened.

### The bug that turned up while drawing the waits

`GameHolder.act` had no in-flight state. Locally that never mattered, because the reducer
answers in the same frame. Over a socket it was wrong: `RemoteGameSession` holds a **single**
waiter for the answer it expects, so a second move sent while the first was in flight replaced
that waiter and the first hung until it timed out. A player who hurried made their own move
stall. One move at a time now, with the second dropped rather than queued.

### A browser for public rooms

`GET /rooms` already existed and nothing used it. `DiscoverScreen` is the four states it
needs — asking, unable to ask, asked-and-empty, and a list — and `OnlineScreen` asks
public-or-private *before* the room exists.

**The default was private and is now listed** — reversed on the product owner's decision. The
original reasoning was that a listing cannot be taken back once a stranger has read it, so the
safe answer is the one already chosen. Sound as far as it went, and it left out the thing that
decides it: a room has to be *found*. With nobody listed by default the public browser is an
empty screen, and an online mode that exists so two strangers can meet gives them nowhere to
do it. The cost is real and worth naming — somebody opening a room for two friends publishes
it unless they notice the control — and what makes that acceptable is that the choice is on
the screen that creates the room, one tap away, before the room exists, and that a listing
carries a nickname and a seat count and nothing else. `RoomVisibilityDefaultTest` was rewritten
rather than deleted: whichever way the default points, something has to say so out loud,
because the control is carried entirely by which end of a groove a thumb is sitting on.

The rows are a pure function of the service's answer (`shared/client/Discovery.kt`, eight
tests) and they **keep the service's order**. A client that re-sorts makes two people looking
at one lobby see two different lists, and one of them taps the row the other was reading.

Four security decisions on that path, three of them fixes:

- **The listing is an allow-list, not the record minus a field.** It used to answer with the
  registry's own row minus `sourceId`, which already published `roomId` — the Durable
  Object's name — and would have published whatever anybody added next. `PublicRoom` names
  what is public; a new field on `RegisteredRoom` stays private until somebody adds it there
  on purpose.
- **`hostNickname` is cleaned by the registry**, not by the field that types it. The UI caps
  it at sixteen characters, which stops the honest caller and nobody else; a direct POST put
  whatever it liked in front of every stranger browsing.
- **`ROOM_OPEN` closes the door as well as the table.** It used to guard only the socket, one
  layer down, so a closed deployment still minted codes and still named its public rooms.
  `/health` and `/replay` stay open above the gate — one is a liveness answer, the other a
  pure function of its own argument.
- **A code that could never have been issued is refused in the Worker**, by
  `looksLikeRoomCode`, before the one Durable Object that knows every live room is asked
  anything. Not the security boundary — `resolveRoomCode` is — but a scan should have to send
  plausible codes to cost the registry a round trip. The refusal is the same 404 an unknown
  code gets, so it is not an oracle either.

Also: the listing sends `no-store` (occupancy a second ago, cached, sends people to a table
that filled while the answer sat in a proxy), a countdown travels as a *duration* resolved
against the service's clock rather than a deadline rendered through a phone's, and the
response is capped at 50 rooms.

### An invitation worth sending

The lobby shows the code monospaced and letterspaced — it is the one string in this app
somebody reads aloud down a telephone — with Share and Copy under it, and the line that says
it can simply be read out.

`shareText` is platform code, because a share *sheet* genuinely is a platform thing: Android's
chooser, the browser's Web Share. Where a platform has none it returns false and the button
falls through to the clipboard, rather than doing nothing the player can see. The clipboard
itself is **not** a second `expect`: Compose carries one on all four targets, and four
hand-written platform implementations would be four APIs to get right for a job the framework
has already done.

## 6n. The endgame, which was being skipped

Calling Vinto took the player straight from the button to "Round over". The bots' final turns
happened — they are in the log — and none of them was drawn.

`AnimationQueue` drops a whole batch that costs more than its budget, which is the right rule for
a client catching up after a reconnect and was the wrong number for this. A Vinto call submits
the call **and all three bots' entire last turns** as one batch: measured at **14** moves in an
ordinary deal, against a budget of 8. So the queue cleared it and the table landed on the final
state, exactly as designed, having skipped the endgame the whole hand was played for.

The budget is 24 now, and the doc says what it is measuring: *how far behind the client is*, not
how much happened. Those were the same number until the final round proved they are not. It does
not weaken the reconnect guard — `RemoteGameSession` already collapses a sync to a single frame
before the queue sees it (design C4).

`FinalRoundIsWatchedTest` plays a real local game to a Vinto call and asserts the batch reaches
the queue whole. It fails on the old budget, which is what makes it worth having.

### And the final round now says who is playing whom

Two things taken from the web client, which does this better, in this app's idiom rather than
its own:

- **The strip above the felt draws from the call onwards.** It used to `return` when
  `coalitionLeaderId` was null, so the window between the call and the coalition choosing showed
  no banner, no turn counter and nothing else — silence at the single most surprising moment in
  the game.
- **A roster of faces**, coalition on the left, caller on the right, the leader ringed. The web
  draws two named columns, which is a panel's worth of height; this is one line of portraits,
  which is the same information in a tenth of the room and reads faster besides — three of the
  four players are bots the person knows by face long before they know by name. It carries one
  spoken description for the whole row, because four portraits read out one at a time are four
  names with no relationship between them.

### And the score sheet answers the question first

It opened with "Round 3" and a table, leaving the player to derive the winner from a column of
+3 and −1 at the exact moment they wanted an answer. It now opens with the verdict — the call
held, level, the others beat it, or nobody called — and the two totals it turned on. The row that
**decided** the round is ringed and named, which is the number both the +3 and the −1 were worked
out from and which nothing used to identify. Portraits went into the rows for the same reason
they went into the roster.

No confetti and no exclamation mark, unlike the web's "🎉 Coalition Victory!": the same screen
has to carry a loss, and a player who has just lost a round does not need it celebrated at them.

`RoundOutcome` and `bestCoalitionHands` are pure and live in `shared/client` beside `roundPoints`,
tested by `RoundOutcomeTest`; the words are tested by `ScoreSheetTest` in composeApp. Same split
as `CardHelpTest` and `LessonCopyTest` — the model says *which* verdict, the resources say it in
a language.
