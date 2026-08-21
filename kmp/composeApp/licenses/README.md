# Bundled fonts

Two families are bundled in `src/commonMain/composeResources/font`, both under the SIL Open
Font License 1.1, whose full text sits beside this file:

* **Cinzel** (Bold) — the wordmark, and nothing else. An engraved Roman face: it is the
  brass plaque the game's name is set on. Instanced from the variable original at weight 700
  with `fontTools`, so the weight is drawn rather than synthesised, and subset to Latin —
  which is all it needs, because the name of the game is the same in every language.
* **Fira Sans Condensed** (Medium, SemiBold, Bold) — everything a player reads. Condensed,
  so a caps button label fits across a third of a phone; and it carries full Cyrillic, which
  is what makes the Belarusian and Ukrainian builds set in the same type as the English one
  rather than falling back to the system face halfway down the screen.

Both are subset to Latin, Latin Extended-A, Cyrillic and the punctuation the app uses,
which is what keeps four files under 280 KB.
