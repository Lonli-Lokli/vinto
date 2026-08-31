# Card imagery — a deck where every face says what the card does

The idea: generate the card faces with an image LLM (svgmaker.io) so that each face *depicts
its meaning*. A 7 is a peek at your own hand, so the 7 shows an eye under a lifted card. A
player who has seen the face once should remember what the rank does without opening the "?"
sheet — the art becomes a second copy of `CARD_CONFIGS`, drawn instead of written.

This file is the prompt sheet: one shared style block, then one prompt per face, all
copy-paste ready. The meanings come from `shared/shapes/.../CardConfig.kt` and are rules, not
guesses.

## Where the art lives, and the pipeline

Current faces: `composeApp/src/commonMain/composeResources/drawable/card_*.png`,
**825 × 1125 px** RGBA (3:4.09, effectively 2.75" × 3.75" poker at 300 dpi). 14 faces +
`card_back.png`.

Two routes from an SVG to the app, pick per asset:

1. **Rasterize to PNG at 825 × 1125** and drop it in under the existing name (or a new
   `card_7_meaning.png` name if this ships as a second deck — see `MONETIZATION.md`).
   Zero build changes; this is the safe default.
2. **Convert SVG → Android vector drawable XML** and put the `.xml` beside the PNGs.
   Compose resources render XML vectors on every target; raw `.svg` files are **not**
   supported in `commonMain` composeResources, so an SVG never goes in as-is. Conversion is
   Android Studio's Vector Asset tool or `avocado`; complex LLM output (gradients, filters,
   masks) often doesn't survive conversion — if the XML looks wrong, fall back to route 1.

Legibility constraints, from how the table actually draws:

- Cards render down to the 44 dp tap floor (`CardScale.crowded()`), where the face is a
  thumbnail. **The rank index must carry the card at that size**; the motif is what you see
  when a card is held up (peeks, the help gallery, the lesson).
- One motif, centered, symmetric enough to read at any of the four seats' orientations.
  No text besides the rank index — words on faces would re-open the translation problem
  §6h just closed.

## The shared style block

Paste this in front of every prompt below. It is what makes 14 generations one deck.

> Flat vector playing-card face, portrait 3:4 ratio, clean white-cream background #F7F5EF
> with a thin rounded border inset in dark ink #14181B. Limited palette: dark ink #14181B,
> felt green #1B5E43, muted gold #C9A227, one warm orange accent #E8791E used sparingly.
> Consistent medium line weight throughout, no gradients, no shadows, no photorealism, no
> texture, no text or letters anywhere except the corner indices described. Large rank index
> in the top-left and bottom-right corners (bottom-right rotated 180°), bold geometric serif.
> One central emblem, symmetric composition, generous margins, in the style of a modern
> minimalist board-game card.

## The faces

### 2–6 — the quiet numbers (value = rank, no action)

One family, five variants. The idea worth encoding: **low is light, high is heavy** — the
whole game is about holding less, so the art should make a 2 feel like a card you keep and a
6 like a card you want rid of.

> Corner indices "2" [3/4/5/6]. Central emblem: [two/three/four/five/six] small felt-green
> diamond pips arranged in a balanced vertical pattern. For rank 2 the pips are outlined and
> airy; each higher rank's pips grow slightly larger and more solid, so that 6 reads visibly
> heavier and darker than 2. Nothing else on the card.

(Generate five times, adjusting the bracketed count and the weight note: 2 = outlined,
3 = thin fill, 4 = solid, 5 = solid and larger, 6 = solid, largest, with a heavier border.)

### 7 — peek at one of your own cards

> Corner indices "7". Central emblem: a single face-down card seen from above, its near
> corner lifted by a thumb, and beneath the lifted corner one calm open eye looking out at
> the viewer. Card back in felt green, eye in dark ink with a gold iris. The gesture reads
> as someone privately checking their own card.

### 8 — peek at one of your own cards (the 7's sibling)

Same act, different prop — siblings, not twins, so a player still tells them apart at a
glance while learning they do the same thing.

> Corner indices "8". Central emblem: a small round hand-mirror with a gold rim, tilted to
> reflect the face of a card lying below it; in the mirror's glass, a single card face is
> visible as a simple felt-green rectangle with an ink pip. The gesture reads as glimpsing
> your own card in a mirror.

### 9 — peek at one opponent's card

> Corner indices "9". Central emblem: a brass spyglass in gold and ink, extended and aimed
> upward-outward toward a face-down felt-green card in the top third of the emblem, a thin
> dotted sight-line connecting lens to card. The gesture reads as looking at someone else's
> card from a distance.

### 10 — peek at one opponent's card (the 9's sibling)

> Corner indices "10". Central emblem: an old-fashioned keyhole shape in dark ink, and
> visible through the keyhole a single face-down felt-green card. A small gold key lies at
> the foot of the emblem. The gesture reads as peeking at a card behind someone else's door.

### Jack — swap two face-down cards from two different players (blind)

The idea that must survive: the swap is **blind** — nobody looks.

> Corner indices "J". Central emblem: two hands reaching from opposite sides, each sliding a
> face-down felt-green card toward the other along two crossing arrows in gold; between them
> a small blindfold ribbon motif in dark ink. Perfectly rotationally symmetric, so it reads
> the same upside down. The gesture reads as a trade made without looking.

### Queen — peek at two cards, then swap them if you want

The Queen is the Jack with eyes: look first, then choose.

> Corner indices "Q". Central emblem: the same two crossing gold arrows between two
> face-down felt-green cards as a trade motif, but above the crossing point a single open
> eye in dark ink with a gold iris, and the arrows drawn dashed rather than solid — a trade
> considered, not yet made. Rotationally symmetric apart from the eye. The gesture reads as
> looking at two cards before deciding to trade them.

### King — declare any card and play its action (value 0)

Two ideas, both needed: the King *speaks a rank into being*, and the King *costs nothing*.

> Corner indices "K". Central emblem: a gold crown above an unrolled scroll; on the scroll,
> instead of writing, a single empty card-shaped outline with a gold question-mark-free
> blank center — a decree with a slot where any rank can be named. The crown is drawn as an
> open outline, hollow and weightless. The gesture reads as royalty commanding any card's
> power by naming it.

### Ace — force an opponent to draw a penalty card (value 1)

> Corner indices "A". Central emblem: a pointing hand in dark ink extending a single
> face-down felt-green card toward the upper edge, with a short bold gold arrow pushing it
> away from the giver. The card being pushed has a small weight-like pip on its back. The
> gesture reads as handing someone a card they did not want.

### Joker — value −1, no action

The only card worth less than nothing: it should look like the one thing on the table that
is lighter than air.

> Corner indices: a small jester-cap glyph instead of a letter. Central emblem: a jester's
> cap in orange #E8791E and felt green with gold bells, floating above a small "−1" drawn as
> an outlined gold token beneath it; two tiny motion lines suggest the cap is drifting
> upward. The gesture reads as the one card that lifts your total instead of adding to it.

### The card back

The back is the face everyone sees most. It must carry the brand (the orange V,
`tools/brand/vinto-mark.png`) and stay non-directional — a back that reads upside down at
the far seat looks broken.

> Playing-card back, portrait 3:4. Felt green #1B5E43 field with a thin gold double border
> inset. Centered: the letter V drawn as a bold geometric mark in warm orange #E8791E with a
> gold outline, inside a diamond of four small card-pip shapes. Around it a subtle
> tessellated diamond lattice in a slightly darker green #0E3428, perfectly rotationally
> symmetric with no top or bottom. No text.

## Working notes for the generation session

- **Generate the siblings together.** 7 and 8 (and 9 and 10) should come from the same
  session so the shared palette and line weight actually match; regenerating one later
  usually drifts.
- **Check every face at thumbnail size** before accepting it — scale to ~120 px tall. If the
  motif turns to noise, simplify the prompt (fewer elements, thicker lines), don't shrink
  expectations of the index.
- **Check both themes.** Faces sit on light paper in light theme and on dark felt in dark
  theme; the cream background and ink border above are what keep the card edges visible on
  both. Don't let a generation talk you into a white-on-transparent face.
- **The art is presentation, never data.** `CardConfig.shortDescription` reaches the hashed
  game state (`CardCopyIsDataTest`); nothing about new art touches that. New faces are new
  drawables and nothing else — no engine, no corpus, no test churn beyond any golden images
  a maintainer chooses to refresh.
- **If this ships as a premium deck** rather than a replacement, the files take a suffixed
  name (`card_7_meaning.png`), the default deck stays as-is, and the deck choice is a
  cosmetic id per `MONETIZATION.md`.
