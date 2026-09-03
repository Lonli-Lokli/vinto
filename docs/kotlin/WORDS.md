# Words, and where they live

Every string a player sees, moved out of the code and into `strings.xml` — in eight slices,
seven of which turned up a defect that had nothing to do with language.

Split out of [`README.md`](README.md) — this workspace's index, state and setup — when that
file grew past the size a tool will read in one go. The section numbers are unchanged, so an
older reference of the form `docs/kotlin/README.md §6c` still names the paragraph it meant.

---
## 6h. Words, and where they live

Every string the **UI module** says is now in
`composeApp/src/commonMain/composeResources/values/strings.xml`. A translation is a file
beside it — `values-be/strings.xml`, `values-uk/strings.xml` — and nothing else changes, which
is what the `translate-game` skill expects to find.

Two rules for that file, written at the top of it as well: name a string for what it *says*
rather than where it sits, and never build a sentence out of two of them. Word order is not
universal, and a translator handed half a sentence cannot fix the half they were not given.

Accessibility descriptions went in too. A screen reader announcing "a face-down card" in a
Belarusian game is the same failure as an untranslated button.

The choice labels moved off the enums. `Difficulty.serialName` is a **wire value** — it is in
every saved game and every recording — and capitalising it to put on a button worked exactly as
long as the app was English. `Labels.kt` maps each enum to a resource instead.

### What was still English, and why it was harder — now done

Roughly two hundred strings remained in `shared/client` when this was written, and they could
not simply move: that module has no Compose, and its copy was not written as sentences to
translate but *assembled* from grammar. All of it is converted now (slice 8 above); the
original reasoning is kept because it is why the answer was a typed message rather than a
string table.

- `Narration.kt` conjugates verbs — `youForm`/`theyForm`, "You draw" against "Raph draws". In
  Belarusian or Ukrainian that is not a suffix swap; it is a different sentence.
- `TableModel.kt` builds prompts and button labels with interpolation ("A 7 went down — toss in
  a match?"), and the pointer keys off those labels.
- `TeachScript.kt` is the lesson, which is mostly prose.
- `CardConfig.kt` in `shared/shapes` carries the card names and descriptions, ported verbatim
  from TypeScript so both clients teach the same game.

The fix is the same in each case and it is **not** a string table in a non-UI module: those
functions should return a *typed message* — the id and its arguments — and the UI should render
it from resources. `Table.prompt: String` becomes something like `Table.prompt: Say`, and the
tests get better rather than worse, because asserting `Say.YouDrew(SEVEN)` says what is meant
where asserting an English sentence says what it currently reads.

That is what landed. The app is no longer half-translated: menus, settings, the score sheet,
the help sheet, the spoken descriptions, the table's prompts, the move log **and the lesson**
all follow the phone's language.

### And then the languages arrived

The sentence that used to end this section — "adding `values-be/` and `values-uk/` is now a file
each and no code at all" — was the whole point of the exercise, and it has now been cashed in.
There is a `values-<loc>/strings.xml` for **every one of the twenty entries in `Language.kt`**,
and the selector in Settings that had nothing to choose between now has nineteen alternatives.

`node tools/check-translations.mjs` holds them: same key set as the source, same placeholders per
string, no Android-style `\'` (compose-resources draws the backslash — `StringEscapeTest` bans it
too), no double-escaped entities, no bare `&`. It does **not** check that anything was actually
translated, because "Vinto" legitimately stays "Vinto" and a tool that guessed would cry wolf.

### The one that was translated and still came out English

The "?" sheet was the exception nobody had spotted, and it was reported by a player rather than
found by a test: *"some card explanations are not translated"*.

The sentence around each card was a resource and did move with the language. What it was built
FROM did not — `CardConfig.name`, `longDescription` and `helpText` are Kotlin constants in
`shared/shapes`, so a Russian reader got a Russian frame with "Queen" and an English paragraph
dropped into it. A translated template around untranslated nouns is the most convincing way to
look translated and not be.

They live in `strings.xml` now, read through `CardWords` (`card_name_*`, `card_long_*`,
`card_help_*`). Nothing in `shared/shapes` changed: `CardConfig` still says a King is worth 0.
**`shortDescription` did not move and must not** — it becomes `Card.actionText`, which is inside
the canonical hash all 50 fixtures pin against TypeScript, and `CardCopyIsDataTest` fails loudly
on anybody who tries. `CardWords` deliberately has no `cardShort()`: the absence is the guard.

### How big it actually is, measured

Counted rather than estimated, because "roughly two hundred strings" is the kind of figure
that turns out to be either 40 or 900 and the difference decides whether this is one sitting
or several:

| | |
| --- | --- |
| Human-facing string literals | **238** — `Narration.kt` 26, `TableModel.kt` 68, `TeachScript.kt` 144 |
| Functions returning a `String` a person reads | 36 |
| Call sites in `composeApp` that render one | 17 |
| Test assertions written against English text | ~24 |
| Entries already in `strings.xml`, for comparison | 183 |

So the description was accurate — this is about as big as it says, not secretly ten times
bigger. The work is real all the same: a `Say` hierarchy with something over a hundred cases,
238 new resource entries, and 36 functions plus their call sites and tests rewritten.

**Do it as vertical slices, not as one commit.** Each leaves the app compiling and the tests
green, and the tests get *better* as it goes: `assertEquals(Say.DrewKnown(You, SEVEN), …)` says
what is meant where an English sentence only says what it currently reads.

### Slice 1 — `Narration.kt`, done

The move log. `narrate` returns a `Say` instead of a sentence, `GameSession.log` is
`StateFlow<List<Say>>`, and `composeApp`'s `said()` renders one from `strings.xml`. 26
resource entries, and the pattern works.

Three things the slice settled, recorded here so the next two do not re-decide them:

- **A person's name is a `Speaker`, not a `String`.** `Speaker.You`, `Speaker.Named(nickname)`
  and `Speaker.Nobody`. It has to be a type because the difference is *grammatical* rather than
  cosmetic — it picks the verb — and a renderer that had to compare a string against the word
  "You" would be broken by the first translation. `Nobody` exists for the things that happen
  to the table rather than to a player, like the deal ending.
- **Conjugated lines are written out twice, in full.** `log_draw_you` and `log_draw_they`, not
  a stem plus a suffix. English makes the suffix trick look reasonable; Belarusian and
  Ukrainian want two different sentences, and a translator handed the fragment `"draws"` cannot
  fix a half they were never given. Only the four verbs that actually conjugate need the pair
  — the past-tense lines ("took", "tossed in", "called Vinto") take one string with a name in
  it.
- **A rank is not translated.** It travels as `Rank` and renders as its symbol. A `7` is a `7`
  in every language this will ever ship in, and a card's *name* is a different string that the
  help sheet already owns.

### Slice 2 — `Choice.label`, done, and it was carrying a bug

The buttons. `Choice.label` is a `Label` — a closed type — rather than a String, and
`Target.Button` names one the same way.

**This slice was not really about translation.** Two things read that label: the UI, to draw a
button, and `TeachScript`, to decide which button the lesson should point at. Identifying a
control by the English it happens to display is a coupling no test sees and no compiler checks,
and it had already failed twice:

- `label.startsWith("Take the")` — the model produces `"Use Queen"`. So the beat that teaches
  the *second way to start a turn* never fired, on a lesson whose director goes to deliberate
  trouble to leave an unused action card on the pile for it (UI.md §6g). Silent since the code was
  written.
- `label.contains("Pass")` — no button has said "Pass" for some time. Dead clause in
  `tossWindow`, harmless only because two other disjuncts did the work.

Both are now type checks. A translation cannot break them and neither can a rewording, which
is the property that matters more than the 13 strings this moved.

Two halves of one lookup to keep in step: `ChoiceButton` marks a button with `keyOf(label)` and
`Pointer` looks it up with the same function. When those disagree the arrow points at nothing
and says nothing — which is exactly how the missing beat stayed missing.

The tests converted with it, and got better: `it.label == Label.CallVinto` instead of
`it.label == "Call Vinto"`, and `send(Label.DrawCard)` instead of `send("Draw")`.

### Slice 3 — `Table.prompt`, done

The line above the buttons. `Ask`, 30 cases, rendered by `asked()` from 30 resource entries.

A third piece of English assembly went with it: the toss-in prompt joined its ranks with
`" or "`, hard-coded in a module with no way to translate it. `Ask.TossIn` carries the ranks
and the renderer joins them with `ask_or`.

And the thing slice 1 had to leave in the UI came back to the model, better than it left —
and has since gone altogether. Dropping a log line that only repeats the prompt used to
compare two *rendered strings*, which worked by coincidence, `Ask.YouDrew` and `Say.DrewKnown`
being different types that happen to produce the same English; `Ask.echoedBy(Say)` said the
relationship instead. It is deleted now, because the fold was wrong rather than
untranslatable: with "You drew the Joker" folded out, the newest line in the box was the
*previous* seat's move, and the log read as though the draw had not happened (product owner,
from a real round). The heading and the log say the same thing once each, on purpose.

### The boundary: `shortDescription` is data, and cannot be translated

Found while deciding what to do about `CardConfig`, and it is the non-obvious constraint on
this whole piece of work.

`CardConfig.shortDescription` is copied into **`Card.actionText`** when a toss-in resolves
(`TossInHandlers`). `Card`s live in `GameState`. The canonical hash excludes exactly three
fields — `turnActions`, `roundActions`, `botMemory` — so `actionText` is **inside the hash**
that all 50 fixtures pin against the value TypeScript computed. Translate those four strings
and every recording diverges.

So the line is drawn there:

| Field | | |
| --- | --- | --- |
| `shortDescription` | **data** | Reaches `Card.actionText`, hashed. Must stay exactly what TypeScript wrote |
| `name`, `longDescription`, `helpText` | presentation | Translatable; they never enter a state |

`CardCopyIsDataTest` in `shared/shapes` pins the four strings, the shape of the rule (an
action card has text, a plain one does not), and the exclusion list itself — so if `actionText`
ever *does* leave the hash, one test says the constraint is lifted rather than fifty saying
something is wrong.

The right fix is that `actionText` should never have been a string in the state: it is derivable
from the rank at the point of display. That is a rules-shaped change needing the corpus
regenerated, which CI.md §1d says is on its way to being impossible — so it is recorded here
as a deviation rather than queued as a task.

### Slice 5 — `Table.detail`, done

Nine cases, and the King's borrowed-action line was built from
`getCardShortDescription` — the hashed field. It now takes `longDescription`, which is the
same information said at greater length and is presentation rather than data.

One improvement came free. `worthSaying` counts how often a player has seen a hint, to fade it
after the second or third time, and it keyed that count on the hint's **words**. It keys on the
message now: the count survives a translation, and two hints that merely read alike in English
no longer share a tally.

### Slice 6 — `Table.help`, done. `TableModel` is finished

The "?" text. `Explains`, six cases, and the card paragraph's *order* — name, value, what it
does, how to do it — is now the resource's business rather than the model's. A language that
wants the value first can have it.

Worth noting how the test moved rather than went away. `everyStateExplainsItself` asserted on
the assembled paragraph ("starts with Queen", "contains swap", "contains 10"), which was the
right claim in the wrong module once the model stopped assembling anything. It split: the
model's half asserts *which* explanation and about which card, and `CardHelpTest` in composeApp
asserts the words. Converting an assertion into something weaker and calling the tests green is
the easy mistake when a refactor moves a responsibility, and it is worth being deliberate about.

**`TableModel.kt` is now fully converted** — labels, prompts, details and help.

### Slice 7 — `Chapter`, and nine strings that were never drawn

The first cut into `TeachScript`, and it turned up a third thing that was not a translation
problem.

`Chapter` carried a `label: String` — "The table", "Your two peeks", nine of them — and
**nothing rendered it**. Meanwhile the progress dots those words were written for had no
accessible name at all: nine unlabelled circles conveying how far somebody had got by *colour
alone*, which is precisely the information a screen reader cannot get.

So the words moved to `Labels.kt` (where `Difficulty`, `Pace` and `Theme` already keep theirs)
and the dots now use them: "Your two peeks — covered", "Calling Vinto — still to come".
`ChapterDotsTest` keeps them connected, so a tenth chapter cannot be added as a silent dot.

Three for three: every slice into this area has found something that had nothing to do with
language — two dead English matches, a hashed field about to be translated, and now display
text that was never displayed. Assembling sentences in a module that cannot render them turns
out to be a reliable marker for code nobody has looked at.

### Slice 8 — the lesson itself, and §6h is finished

**Done.** 28 beats, ~135 literals, one commit, exactly as the design said it had to be — `Lesson`
holds `title: String?` and `body: String` as *fields*, so changing their type moves every call
site at once and only the resource entries can be added incrementally.

What is left in `shared/client` after it: **five string literals, none of them words a player
sees** — two storage keys, an internal animation id, and two `require`/`error` messages for
whoever is debugging. The module says what happened and the UI says it in words, everywhere.

Four things worth keeping from doing it.

**The name `Beat` was taken, and that is not a triviality.** `Choreography.kt` has had a `Beat`
since the animation layer was built and it means something completely different — a step in a
card's movement. The design in this section named the new type `Beat` and it would have
collided on the first compile. It is `Teaches`, which reads correctly beside the five types it
joins (`Say`, `Label`, `Ask`, `Detail`, `Explains`), all named for what the module is *doing*.

**The design's fourth step was wrong, and the section it was written in says why.** It proposed
turning `Taught.talked: Set<String>` into a `Set<Teaches>` "while you are there". That breaks
for exactly the two beats this section had already identified as irregular: `TossIn` and
`VintoCalled` carry arguments, so two instances of the same beat are *unequal* — a toss-in
window with one thrower and the same window with two would be two entries in the set, and the
lesson would say itself twice. The id is the beat's identity; the arguments are what varies
within it. `Teaches.id` stays, and the eighteen existing `talkId` strings stay with it, so a
lesson somebody is halfway through still means what it meant.

**Two resource conventions caught it, and one of them has its own test already.**
compose-resources does **not** process `\'` — the backslash is drawn on the screen — which
`StringEscapeTest` exists to catch and did. And its format arguments are positional (`%1$s`),
not bare `%s`; a bare one renders literally. Both were caught by tests rather than by review,
which is the argument for the split below.

**The tests split, and the split is the careful part.** `TeachScriptTest` asserted things like
"the body contains +3" — the right claim in the wrong module once the script stops assembling
words. The easy, wrong move is to let those claims go and call the suite green. So the script's
half now asserts *which* beat and in *what order* (a fact about the script), and every content
claim moved intact to `LessonCopyTest` in composeApp, which renders the resource and asserts on
it — the same split `CardHelpTest` got in slice 6. That includes the one that exists because
the copy got a rule **wrong** once: a caller who finishes lower takes +3 *and the others each
lose one*, where nothing is what a tie costs them.

`TeachingRoundTest` now collects prompts and details separately (`asked` and `said`), which is
the shape the remaining slices want anyway.

`Say` lives in `shared/client` rather than `shared/protocol` because it is not wire — nothing
sends one anywhere, and putting it in the protocol module would imply a compatibility promise
that does not exist.
