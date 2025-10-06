import { Rank } from '../shapes';
import * as gameSetup from '../constants/game-setup';

// Helper function to get action explanation
export const getActionExplanation = (rank: Rank): string => {
  const { getCardLongDescription } = gameSetup;
  return getCardLongDescription(rank) || 'Special card action';
};

// Helper function to get card value
export const getCardValue = (rank: Rank): number => {
  const { getCardValue } = gameSetup;
  return getCardValue(rank);
};

export const getCardName = (rank: Rank): string => {
  const { getCardName } = gameSetup;
  return getCardName(rank);
};
