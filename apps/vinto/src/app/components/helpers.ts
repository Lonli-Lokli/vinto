import {
  Image_2,
  Image_3,
  Image_4,
  Image_5,
  Image_6,
  Image_7,
  Image_8,
  Image_9,
  Image_10,
  Image_J,
  Image_Q,
  Image_K,
  Image_A,
  Image_Joker,
} from './presentational/image';

export type CardSize = 'sm' | 'md' | 'lg' | 'xl' | 'auto';
type CardSizeConfig = { className: string; width?: number ; height?: number; sizes?: string; fill?: boolean };

export const CARD_SIZE_CONFIG: Record<CardSize, CardSizeConfig> = {
  sm: {
    className: 'w-6 h-9 text-2xs',
    width: 24,
    height: 36,
    sizes: '24px',
  },
  md: {
    className: 'w-8 h-12 text-2xs',
    width: 32,
    height: 48,
    sizes: '32px',
  },
  lg: {
    className: 'w-10 h-14 text-xs',
    width: 40,
    height: 56,
    sizes: '40px',
  },
  xl: {
    className: 'w-12 h-16 text-sm',
    width: 48,
    height: 64,
    sizes: '48px',
  },
  auto: {
    className: 'w-full h-full text-xs',
    fill: true,
    sizes: '(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 200px',
  },
} as const;

export const RANK_IMAGE_MAP = {
  '2': Image_2,
  '3': Image_3,
  '4': Image_4,
  '5': Image_5,
  '6': Image_6,
  '7': Image_7,
  '8': Image_8,
  '9': Image_9,
  '10': Image_10,
  J: Image_J,
  Q: Image_Q,
  K: Image_K,
  A: Image_A,
  Joker: Image_Joker,
} as const;

/**
 * Calculate image sizes attribute accounting for rotation
 * When rotated 90°, width and height are effectively swapped
 */
export const getImageSizes = (size: CardSize, rotated: boolean): string  | undefined => {
  const config = CARD_SIZE_CONFIG[size];

  if (config.fill) return undefined;

  // When rotated, the effective width is the original height
  if (rotated && config.height) {
    return `${config.height}px`;
  }

  return config.sizes;
};
