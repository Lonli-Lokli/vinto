# Bundled fonts, and which languages they cover

Two families live in `src/commonMain/composeResources/font`, both under the SIL Open Font
License 1.1, whose full text sits beside this file.

## What is bundled

**Fira Sans Condensed** (Medium, SemiBold, Bold) — everything a player reads. Condensed earns
its place twice over: a caps label fits across a third of a phone, and the same word in German
or Belarusian still fits, which a wide face would not.

**Cinzel** (Bold) — the name of the game, and nothing else. An engraved Roman: the brass plaque
the game is played on. Instanced from the variable original at weight 700 with `fontTools`, so
the weight is drawn rather than synthesised, and subset to Latin — which is all a proper noun
needs, since VINTO is VINTO in every language. `FontCoverageTest` holds it to exactly that one
string; the moment anything translated is set in it, the case fails.

## Which languages set in the bundled type

Every language written in **Latin** or **Cyrillic**, because the subset keeps all of both:
Latin and its extensions — Vietnamese included — and the whole Cyrillic block, so Polish,
Czech, Romanian, Turkish, Vietnamese, Russian, Ukrainian, Belarusian, Serbian and Kazakh all
set in the same face, at the same weights, with the same colour on the page.

The first subset here did *not* keep all of that. It was cut to Latin-1 plus Latin Extended-A
plus the common Cyrillic range, which quietly dropped Romanian's ș and ț, every Vietnamese
tone mark, and Kazakh — none of which would have failed a build, and all of which would have
arrived in a different typeface mid-word. Coverage is worth more than the eighty kilobytes it
costs, and `FontCoverageTest` now measures the promise rather than trusting it.

## Which do not, and what happens then

**CJK, Arabic, Hebrew, Devanagari and Thai are a deliberate gap.** No font covering them fits
in a phone game's download — Noto Sans CJK alone is larger than this entire app — so the
platform's own face is the right fallback, and on Android and iOS it is a good one.

What must not happen is discovering that in a screenshot. `FontCoverageTest` reads every
`values-*/strings.xml` against the real `cmap` and fails the build with the locale and the
exact characters, so adding such a locale is a decision with two honest answers: bundle a face
for that script alongside this one, or accept the fallback and say so here.

## Sizes

| File | Bytes | Covers |
| --- | --- | --- |
| `fira_medium.ttf` | ~302 KB | Latin, Latin ext, Vietnamese, Cyrillic |
| `fira_semibold.ttf` | ~327 KB | as above |
| `fira_bold.ttf` | ~328 KB | as above |
| `cinzel_bold.ttf` | ~31 KB | Latin, for one word |

Dropping Greek from the original files is what pays for keeping everything else; no locale in
the plan is written in it, and it is the one script here nothing else depends on.
