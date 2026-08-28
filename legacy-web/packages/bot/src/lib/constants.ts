export const SWAP_KNOWLEDGE_WEIGHT = 100; // Highest priority: maximize knowledge
export const SWAP_HAND_SIZE_WEIGHT = 50; // Second priority: minimize hand size
export const SWAP_SCORE_WEIGHT = 15; // Increased from 1 to 15 (15x) to prevent catastrophic Joker swaps

// Strategic card protection multipliers (applied to score delta)
// These determine HOW MUCH to protect valuable cards
export const JOKER_PROTECTION_MULTIPLIER = 3.0; // Joker is the best card (-1 point)
export const KING_PROTECTION_MULTIPLIER = 2.5; // King is valuable (0 points + powerful action)

// Penalty amplifiers for bad swaps (applied after protection multipliers)
// These control the MAGNITUDE of the penalty to ensure bot NEVER makes catastrophic swaps
// Example: Swapping Joker for 6: scoreDelta (7) × SWAP_SCORE_WEIGHT (15) × JOKER_PROTECTION (3.0) × AMPLIFIER (100) = 31,500 penalty
export const JOKER_PENALTY_AMPLIFIER = 100; // Massive penalty for swapping out Joker
export const KING_PENALTY_AMPLIFIER = 100; // Large penalty for swapping out King
export const GENERAL_SWAP_PENALTY_MULTIPLIER = 10; // General penalty for swapping better card for worse
