# Card imagery — a deck where every face says what the card does

The idea: generate the card faces with an image LLM (svgmaker.io) so that each face *depicts
its meaning in this game*. A player who has seen the face once should be able to answer, from
the picture alone, the three questions every action card poses: **whose card does it touch,
what happens to it, and do I get to look?** The art becomes a second copy of `CARD_CONFIGS`,
drawn instead of written.

The first version of this sheet failed that test and is replaced. It described *props* — a
telescope for the 9, a keyhole for the 10 — and a prop answers none of the three questions: a
hand holding a telescope could be any game's card. The meanings come from
`shared/shapes/.../CardConfig.kt` and `docs/game-engine/VINTO_RULES.md`; what was missing was
a visual grammar that encodes actor and target, not just theme.

## The grammar: every action card is a miniature of the table

Vinto's one universal image is the thing every player stares at all game: **rows of face-down
cards in front of each seat**. So the emblem on every action card is a tiny top-down table:

| Element | Means |
| --- | --- |
| A row of small face-down cards along the **bottom** edge, marked with a gold seat-chevron | **your own hand** |
| A row of small face-down cards along the **top** edge, in cool ink, no chevron | **another player's hand** |
| A card tilted up out of its row, face glowing cream-white | a card being **revealed** |
| A large open **eye** with a dotted sight-line to a card | **you get to look** at that card |
| An eye firmly **closed** | **nobody looks** (a blind action) |
| Bold solid gold **crossing arrows** between two cards | a **swap that happens** |
| The same arrows **dashed** | a swap **you may choose** to make |
| A neat **stack** of face-down cards | the **draw pile** |

Positions do the work a prop cannot: bottom = mine, top = theirs, eye = information, arrow =
movement. Once a player has read any one action card, every other one is legible for free —
and the grammar is honest to the rules: the Jack's eye is closed because the swap is blind
(`VINTO_RULES.md`: swap two face-down cards, no peek), the Queen's is open and her arrows
dashed because she looks first and swaps *optionally*.

No people, no hands, no telescopes, no keyholes, no scrolls. Only cards, eyes, arrows, and
one crown.

## Where the art lives, and the pipeline

Current faces: `composeApp/src/commonMain/composeResources/drawable/card_*.png`,
**825 × 1125 px**. The destination format is **vector** — XML vector drawables render on all
four targets and stay crisp at any size, which raster PNGs demonstrably do not on desktop and
web (see the options rundown that settled this). Raw `.svg` is not accepted by
`commonMain` composeResources; convert with Android Studio's Vector Asset importer or
`avocado`, and keep the art paths-and-solid-fills only so conversion survives.

**Generate the emblem, not the card.** The first generation run produced two failures that
were never going to stop: a text banner nobody asked for ("THE CARD READER"), and a
bottom-right index drawn as a "6" instead of a rotated 9. Neither is worth fighting in a
prompt when the frame is deterministic anyway:

- The **card template** — border, background, both corner indices (bottom-right rotated
  180°, with the underline stroke on 6 and 9 that every real deck uses to disambiguate
  them) — is **one hand-written vector**, built once, correct forever.
- svgmaker.io generates only the **square central emblem** per rank, dropped into the
  template's slot. Fourteen generations can no longer disagree about the frame, miscount an
  index, or invent a label.

Legibility constraints, from how the table actually draws: cards render down to the 44 dp tap
floor (`CardScale.crowded()`), where only the corner index carries the card — the emblem is
what a player studies during peeks, the help gallery and the lesson. Check every emblem at
~120 px; if the mini-table turns to noise, reduce the row to three cards instead of five.

## The shared style block

Paste this in front of every emblem prompt below.

> Flat vector emblem on a plain cream background #F7F5EF, square composition with generous
> margins. Limited palette: dark ink #14181B, felt green #1B5E43, muted gold #C9A227, warm
> orange #E8791E used sparingly. Small face-down playing cards are drawn as rounded
> felt-green rectangles with a thin gold border; a revealed card is cream-white with a gold
> glow. Consistent medium line weight, paths and solid fills only — no gradients, no
> shadows, no masks, no filters, no texture. Absolutely no words, letters, numbers, labels,
> banners or scrolls anywhere. No people, no hands, no faces, no real-world objects unless
> the prompt names one.

## The faces

### 2–6 — the weight you are holding (value = rank, no action)

Not a pip deck. In Vinto a 2–6 *does* nothing — its entire meaning is that it sits in your
row counting against you, a 2 barely and a 6 badly. So the emblem is your own hand with the
burden drawn on it:

> A single row of [two/three/four/five/six] small face-down playing cards along the lower
> half, marked beneath the center with a small gold seat-chevron pointing up at them: the
> viewer's own hand. Hanging beneath the row, one round gold weight on a short cord, like a
> scale weight. For the lowest rank the cards are outlined and airy and the weight is tiny;
> for each higher rank the cards are drawn more solid and darker green and the weight grows
> visibly larger and heavier, so the highest rank reads as a hand dragged down by what it
> holds.

Generate all five in one session so the progression actually progresses. **Count the cards
in each result by hand** — generators miscount, and a "4" showing five cards teaches a lie.
The count *is* the rank: the card's number told as the number of cards you're stuck with.

### 7 and 8 — peek at one of your own cards

> A row of five small face-down playing cards along the bottom edge with a gold
> seat-chevron beneath it: the viewer's own hand. One card of the row is tilted up out of
> line, glowing cream-white. Above the row, one large calm open eye in dark ink with a gold
> iris, a fine dotted sight-line running from the eye down to the tilted card. Nothing else.

Only *your* row appears on the card — that absence is what says "your own, not theirs".
7 and 8 share the composition deliberately, because they do the same thing; vary only which
card of the row is tilted (7: second from left; 8: fourth), so the siblings are tellable
apart without pretending they differ.

### 9 and 10 — peek at one card of another player

> A row of five small face-down playing cards along the TOP edge, in cooler darker green
> with no chevron: another player's hand. One of their cards is tilted down out of line,
> glowing cream-white. At the bottom edge, a gold seat-chevron with one large open eye
> above it, dark ink with a gold iris, and a long fine dotted sight-line running from the
> eye up across the emblem to the tilted card. Nothing else.

The mirror of the 7/8: their row, top of frame; your eye, bottom. Same sibling rule — 9 and
10 vary only which of the opponent's cards is tilted.

### Jack — swap two face-down cards from two different players, blind

> Two rows of small face-down playing cards face each other: one along the bottom edge with
> a gold seat-chevron, one along the top edge without. One card from each row has slid out
> toward the middle, both still face-down, joined by two bold solid gold arrows crossing in
> an X. At the center of the X, one firmly closed eye in dark ink — a single curved lash
> line, unmistakably shut. Rotationally symmetric apart from the chevron and the eye.

The closed eye is the Jack's whole personality: the swap happens and **nobody looks**
(`VINTO_RULES.md`: swap two face-down cards belonging to two different players). The two
rows say "two different players" without a word.

### Queen — peek at two cards from two different players, then swap if you want

> The same composition as a trade between two rows: bottom row with a gold seat-chevron,
> top row without, one card from each slid toward the middle. Both slid cards are tilted
> and glowing cream-white, each with a fine dotted sight-line running to one large open eye
> at the center, dark ink with a gold iris. The two crossing arrows between the cards are
> drawn DASHED, not solid — a trade being considered, not yet made.

Deliberately the Jack's sister image with exactly two changes — the eye is open, the arrows
are dashed — because that *is* the rules difference: the Queen looks first and the swap is
optional. A player who compares the two faces has learned both cards.

### King — declare any card's action and play it (value 0)

> A gold crown drawn as an open outline, hollow and weightless, floating at the top. Below
> it, a fanned arc of four small cream-white cards, each bearing one tiny glyph from the
> deck's own grammar: an open eye over a single card; an open eye with a dotted line to a
> distant card; two solid crossing arrows; a card with a bold arrow pushing it away. A
> single gold beam drops from the crown to one card of the fan, picking it.

The King *names a rank and plays its action* — so his emblem is a menu of the other emblems,
with the crown choosing. The glyphs are the 7/8, 9/10, Jack and Ace emblems in miniature,
which only works because the grammar is consistent. The hollow outline crown carries the
other fact worth teaching: the mightiest card weighs nothing (value 0).

### Ace — force an opponent to draw a penalty card (value 1)

> A neat stack of face-down playing cards at the center-left: the draw pile. From its top,
> one face-down card slides along a single bold gold arrow up toward a row of five
> face-down cards at the top edge, where a gap has opened to receive it — the row visibly
> becoming six. At the bottom edge, a small gold seat-chevron pointing up: the viewer
> commanding it. No eye anywhere — nobody looks at anything.

Deck → their row, one more card, their problem. The growing row is the punchline: in this
game a bigger hand is a worse hand, and the emblem shows the burden arriving.

### Joker — value −1, no action

> A row of five small face-down playing cards along the bottom edge with a gold
> seat-chevron. One card of the row is drawn in warm orange with a tiny jester-cap glyph
> on its back, and it is lifting gently off the row, floating slightly above the line with
> two short motion lines beneath it — lighter than the hand it sits in. Above it, a small
> gold minus sign inside a thin gold circle.

The one card that pulls your total *down*: shown doing exactly that, in its own seat, in the
row grammar every other card uses. Corner index on the template is the jester-cap glyph
rather than a letter.

### The card back

Unchanged from the first sheet — the back is brand, not rules:

> Playing-card back, portrait 3:4. Felt green #1B5E43 field with a thin gold double border
> inset. Centered: the letter V drawn as a bold geometric mark in warm orange #E8791E with
> a gold outline, inside a diamond of four small card-pip shapes. Around it a subtle
> tessellated diamond lattice in slightly darker green #0E3428, perfectly rotationally
> symmetric with no top or bottom. No text besides the V mark.

## Acceptance checklist, per emblem

Learned from the first generation run; check every result against all six before it goes
near the repo:

1. **The three questions**: can someone who knows the rules but not this deck answer *whose
   card, what happens, do I look* from the emblem alone? If they answer wrong, the emblem is
   wrong, however pretty.
2. **No text leaked in.** The generator will try; the style block forbids it; check anyway.
3. **Counts are exact** — cards in a 2–6 row, cards in a hand row (five), arrows (two).
4. **Grammar consistency**: chevron only on your row, your row only at the bottom, closed
   eye only on the Jack, dashed arrows only on the Queen.
5. **Thumbnail test** at ~120 px: rows still read as rows, the eye still reads as an eye.
6. **Paths and solid fills only**, or the vector-drawable conversion will mangle it.

## Working notes

- **The art is presentation, never data.** `CardConfig.shortDescription` reaches the hashed
  game state (`CardCopyIsDataTest`); new faces are new drawables and nothing else — no
  engine, no corpus, no test churn beyond any goldens a maintainer refreshes.
- **Sibling pairs (7/8, 9/10) and sister pairs (J/Q) generate in one session each**, or the
  palette and line weight drift and the deliberate near-identity stops being deliberate.
- **If this ships as a premium deck** rather than a replacement, files take a suffixed name
  (`card_7_meaning.xml`), the default deck stays, and the deck choice is a cosmetic id per
  `MONETIZATION.md`.

## Postscript: the deck is code now

Two generation rounds proved the prediction in the acceptance checklist right in the worst
way: the model drew the 2–6 *progression* as one scene, turned "the viewer's own hand" into
a human hand, and tattooed the seat-chevron onto it. A diffusion model cannot hold a
fourteen-face design system; code holds it for free.

`tools/make-card-faces.py` now generates the whole deck — the grammar above implemented as
SVG primitives (rows, chevron, eyes, arrows, weight), the standard corner indices on every
face exactly like a normal deck (rotated bottom-right, underlined 6 and 9), and a
`preview.html` for judging the result at full size and at thumbnail. The generated SVGs are
committed under `tools/card-faces/`, same convention as the launcher icons. The prompt sheet
above stays as the *specification* the code implements — and as the record of why prompts
alone were not enough.

## Revision 3 — big heraldic emblems, decided on the product owner's feedback

The mini-table scenes were legible but read as diagrams, and two of their symbols failed the
owner's review: the seat-chevron ("what does this sign mean?") and the 2–6 scale weights
("balloons"). Both are gone. The deck now works like a poker deck's court cards: **one big
emblem per action card**, a single bold symbol of the function, drawn large.

| Card | Emblem |
| --- | --- |
| 2–6 | Clean pip cards — but the pips are card shapes, the game's own object; count = rank |
| 7, 8 | Your card (green, gold border) with a large open eye on it — you know your own card |
| 9, 10 | An opponent's card (dark, ink border) under a golden magnifying lens |
| J | Two crossed cards — one yours, one theirs — wearing a knotted blindfold, circled by solid swap arrows |
| Q | The same two cards with the eye open between them and the swap arrows dashed — look first, trade optionally |
| K | A hollow crown (value 0) whose beam falls on a white card bearing a star: any card, by naming it |
| A | A potion flask pouring onto an opponent's card — the poison of one more card (the owner's own image) |
| Joker | A three-horned jester's cap, bells out, over a −1 badge |

What survives from the grammar is the one code that needs no legend: **green with a gold
border is yours, dark with an ink border is theirs**. The J/Q pair is still deliberately the
same image with the Jack blindfolded and the Queen's arrows dashed, because that is the
rules difference between them.

One drawing lesson worth keeping: the first large-scale jester cap rendered as a crown —
three upright spikes over a band are a crown whatever you call them. A jester cap needs
drooping outer horns and hanging bells to not be royalty.

## Postscript 2: the pipeline is complete, and the mark is ours

The deck ships as **vector drawables**. `tools/make-card-faces.py` emits, on every run,
both the SVGs (for the preview page) and Android vector-drawable XML directly into
`composeApp/src/commonMain/composeResources/drawable/` under the old PNG names, so no
call site changed while the art became resolution-independent — the fix for the desktop
and web quality complaint that started this work. Two vector-drawable gaps were removed
at the source rather than converted around: the corner indices are monoline stroke-drawn
glyphs (no `<text>`), and every dashed stroke is emitted as real segments (no
`stroke-dasharray`). The emitter refuses any SVG feature it does not know, so drift
fails the generator instead of rendering wrong in the app. A WCAG gate runs before
anything is written: indices at 4.5:1 on their grounds, grounds at 3:1 against the felt.

The brand mark was also replaced: the old `tools/brand/vinto-mark.png` came from the
retired web client and could not be legally reused. The mark is now an original
letterform authored here (`tools/brand/vinto-mark.svg`, the same V the card back
carries), and every launcher icon, favicon, manifest icon and the share card is
regenerated from it — the share card fanning five faces of this deck.
