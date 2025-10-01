import { FC } from 'react';
import { Image_Donatello, Image_Michelangelo, Image_Raphael, Image_You } from './image';
import { Player } from '../shapes';

interface AvatarProps {
  player: Player;
  size?: 'sm' | 'md' | 'lg';
}

export const Avatar: FC<AvatarProps> = ({ player, size = 'md' }) => {
  const imageSizeClasses = {
    sm: 'w-8 h-8',
    md: 'w-16 h-16',
    lg: 'w-full h-full',
  };

  const renderImg = () => {
    const className = imageSizeClasses[size];
    switch (player.id) {
      case 'human':
        return <Image_You className={className} />;
    case 'bot1':
        return <Image_Michelangelo className={className} />;
    case 'bot2':
        return <Image_Donatello className={className} />;
    case 'bot3':
        return <Image_Raphael className={className} />;
      default:
        return '🤖';
    }
  };
  return <div>{renderImg()}</div>;
};
