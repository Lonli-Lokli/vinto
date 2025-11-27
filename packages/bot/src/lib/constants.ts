export const SWAP_KNOWLEDGE_WEIGHT = 100; // Highest priority: maximize knowledge
export const SWAP_HAND_SIZE_WEIGHT = 50; // Second priority: minimize hand size
export const SWAP_SCORE_WEIGHT = 15; // Increased from 1 to prevent catastrophic Joker swaps

// Strategic card protection multipliers
export const JOKER_PROTECTION_MULTIPLIER = 3.0; // Joker is the best card (-1 point)
export const KING_PROTECTION_MULTIPLIER = 2.5; // King is valuable (0 points + action)
