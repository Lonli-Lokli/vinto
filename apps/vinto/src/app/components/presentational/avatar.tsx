import { FC } from 'react';
import { Image_Donatello, Image_Michelangelo, Image_Raphael, Image_You } from './image';

interface AvatarProps {
  playerName: string;
  size?: 'xs' | 'sm' | 'md' | 'lg';
}

/**
 * Derive avatar image from player name
 * Maps specific bot names to their avatar images
 */
function getAvatarFromName(name: string) {
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

export const Avatar: FC<AvatarProps> = ({ playerName, size = 'md' }) => {
  const imageSizeClasses = {
    xs: 'w-6 h-6',
    sm: 'w-8 h-8',
    md: 'w-16 h-16',
    lg: 'w-full h-full',
  };

  const renderImg = () => {
    const className = imageSizeClasses[size];
    const avatar = getAvatarFromName(playerName);

    switch (avatar) {
      case 'you':
        return <Image_You className={className} />;
      case 'michelangelo':
        return <Image_Michelangelo className={className} />;
      case 'donatello':
        return <Image_Donatello className={className} />;
      case 'raphael':
        return <Image_Raphael className={className} />;
      default:
        return '🤖';
    }
  };

  return <div>{renderImg()}</div>;
};
