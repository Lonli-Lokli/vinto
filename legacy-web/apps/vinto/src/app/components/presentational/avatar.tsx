import { FC } from 'react';
import {
  Image_Donatello,
  Image_Michelangelo,
  Image_Raphael,
  Image_You,
} from './image';

interface AvatarProps {
  playerName: string;
  size?: 'xs' | 'sm' | 'md' | 'lg';
  priority?: boolean; // For avatars visible on initial render
}

const AVATAR_SIZE_CONFIG: Record<
  string,
  {
    className: string;
    width?: number;
    height?: number;
    sizes?: string;
    fill?: boolean;
  }
> = {
  xs: {
    className: 'w-6 h-6 rounded-full object-cover',
    width: 24,
    height: 24,
    sizes: '24px',
  },
  sm: {
    className: 'w-8 h-8 rounded-full object-cover',
    width: 32,
    height: 32,
    sizes: '32px',
  },
  md: {
    className: 'w-16 h-16 rounded-full object-cover',
    width: 64,
    height: 64,
    sizes: '64px',
  },
  lg: {
    className: 'w-full h-full rounded-full object-cover',
    fill: true,
    sizes: '(max-width: 768px) 100vw, 200px',
  },
} as const;

const AVATAR_COMPONENTS = {
  you: Image_You,
  michelangelo: Image_Michelangelo,
  donatello: Image_Donatello,
  raphael: Image_Raphael,
} as const;

/**
 * Derive avatar image from player name
 * Maps specific bot names to their avatar images
 */
function getAvatarFromName(
  name: string
): keyof typeof AVATAR_COMPONENTS | 'default' {
  // Check for human player names (case-insensitive)
  if (name.toLowerCase() === 'you') {
    return 'you';
  }

  // Map bot names to avatars (case-insensitive)
  const nameLower = name.toLowerCase();
  if (nameLower.includes('michelangelo') || nameLower === 'mikey') {
    return 'michelangelo';
  }
  if (nameLower.includes('donatello') || nameLower === 'donnie') {
    return 'donatello';
  }
  if (nameLower.includes('raphael') || nameLower === 'raph') {
    return 'raphael';
  }

  // Default fallback
  return 'default';
}

export const Avatar: FC<AvatarProps> = ({
  playerName,
  size = 'md',
  priority = true,
}) => {
  const config = AVATAR_SIZE_CONFIG[size];
  const avatarKey = getAvatarFromName(playerName);
  const AvatarImage =
    avatarKey !== 'default' ? AVATAR_COMPONENTS[avatarKey] : null;

  return (
    <div className={`relative ${config.fill ? 'w-full h-full' : 'inline-block'}`}>
      {AvatarImage ? (
        <AvatarImage
          className={config.className}
          sizes={config.sizes}
          width={config.width}
          height={config.height}
          fill={config.fill}
          priority={priority}
        />
      ) : (
        <span
          className={`${config.className} flex items-center justify-center bg-gray-200 text-gray-600`}
        >
          🤖
        </span>
      )}
    </div>
  );
};
