import { Card } from './domain-types';

export const shuffleCards = (deck: Card[]): Card[] => {
  const shuffled = [...deck];

  // Use crypto.getRandomValues for better entropy
  const randomBytes = new Uint32Array(shuffled.length);
  crypto.getRandomValues(randomBytes);

  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor((randomBytes[i] / 0x100000000) * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  return shuffled;
};

export const getEnvironment = () => {
  switch (process.env.VERCEL_ENV) {
    case 'production':
      return 'production';
    default:
      return 'development';
  }
};
