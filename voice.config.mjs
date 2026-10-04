// Vinto's exceptions to the portfolio voice gate (../gulnya/tools/voice-lint.mjs). Each one names the
// rule it relaxes, the text it applies to, and why. Anything not listed here is checked as usual.
export default {
  // Vinto is free with an optional tip (portfolio contract: "Vinto is the exception, and stays free"),
  // so the rules that stop a PAID app from claiming "free" do not apply.
  rules: {
    'de-free-claim': 'off',
    'fr-free-claim': 'off',
  },
  allow: [
    { rule: 'es-inverted', match: 'Vinto!', why: 'the "!" belongs to the name "Vinto!", so it takes no opening ¡' },
    { rule: 'fr-spacing', match: 'Vinto!', why: 'the "!" is part of the name "Vinto!", so French spacing does not apply to it' },
    { rule: 'exclamation', match: 'Vinto！', why: 'the same name with the full-width mark Chinese and Japanese text uses' },
    { rule: 'exclamation', match: 'Vinto!', why: '"Vinto!" is the name of the card game and the call a player makes; the "!" is part of the name' },
  ],
};
