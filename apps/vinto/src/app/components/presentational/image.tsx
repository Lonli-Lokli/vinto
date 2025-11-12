/* eslint-disable @next/next/no-img-element */
import React from 'react';
import coverSrc from '../../images/cover.png';
import twoSrc from '../../images/2.png';
import threeSrc from '../../images/3.png';
import fourSrc from '../../images/4.png';
import fiveSrc from '../../images/5.png';
import sixSrc from '../../images/6.png';
import sevenSrc from '../../images/7.png';
import eightSrc from '../../images/8.png';
import nineSrc from '../../images/9.png';
import tenSrc from '../../images/10.png';
import jSrc from '../../images/J.png';
import qSrc from '../../images/Q.png';
import kSrc from '../../images/K.png';
import aSrc from '../../images/A.png';
import jokerSrc from '../../images/Joker.png';
import youSrc from '../../images/You.png';
import michelangeloSrc from '../../images/Michelangelo.png';
import donatelloSrc from '../../images/Donatello.png';
import raphaelSrc from '../../images/Raphael.png';
import Image from 'next/image';

type ImgProps = Omit<React.ComponentProps<typeof Image>, 'src' | 'alt'>;

type StaticImportLike = { src?: string } | string | undefined | null;

function resolveSrc(input: StaticImportLike): string {
  if (!input) return '';
  if (typeof input === 'string') return input;
  // Next.js may export an object with a 'src' property for static assets
  return typeof input.src === 'string' ? input.src : '';
}

const wrap = (
  srcLike: StaticImportLike,
  altFallback: string,
  priority = false
) =>
  function WrappedImg(props: ImgProps) {
    const { className, width, height, fill, ...rest } = props;
    const src = resolveSrc(srcLike);

    if (fill) {
      return (
        <Image
          src={src}
          alt={altFallback}
          fill
          priority={priority}
          className={className ?? 'w-10 h-10 object-contain'}
          {...rest}
        />
      );
    }
    return (
      <Image
        src={src}
        alt={altFallback}
        priority={priority}
        className={className ?? 'w-10 h-10 object-contain'}
        width={width}
        height={height}
        {...rest}
      />
    );
  };

export const Image_Cover = wrap(coverSrc, 'Cover', true);
export const Image_2 = wrap(twoSrc, '2');
export const Image_3 = wrap(threeSrc, '3');
export const Image_4 = wrap(fourSrc, '4');
export const Image_5 = wrap(fiveSrc, '5');
export const Image_6 = wrap(sixSrc, '6');
export const Image_7 = wrap(sevenSrc, '7');
export const Image_8 = wrap(eightSrc, '8');
export const Image_9 = wrap(nineSrc, '9');
export const Image_10 = wrap(tenSrc, '10');
export const Image_J = wrap(jSrc, 'J');
export const Image_Q = wrap(qSrc, 'Q');
export const Image_K = wrap(kSrc, 'K');
export const Image_A = wrap(aSrc, 'A');
export const Image_Joker = wrap(jokerSrc, 'Joker');

export const Image_You = wrap(youSrc, 'You', true);
export const Image_Michelangelo = wrap(michelangeloSrc, 'Michelangelo', true);
export const Image_Donatello = wrap(donatelloSrc, 'Donatello', true);
export const Image_Raphael = wrap(raphaelSrc, 'Raphael', true);
