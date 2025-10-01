/* eslint-disable @next/next/no-img-element */
import React from 'react';
import twoSrc from '../images/2.svg';
import threeSrc from '../images/3.svg';
import fourSrc from '../images/4.svg';
import fiveSrc from '../images/5.svg';
import sixSrc from '../images/6.svg';
import sevenSrc from '../images/7.svg';
import eightSrc from '../images/8.svg';
import nineSrc from '../images/9.svg';
import tenSrc from '../images/10.svg';
import jSrc from '../images/J.svg';
import qSrc from '../images/Q.svg';
import kSrc from '../images/K.svg';
import aSrc from '../images/A.svg';
import jokerSrc from '../images/Joker.svg';
import youSrc from '../images/You.png';
import michelangeloSrc from '../images/Michelangelo.png';
import donatelloSrc from '../images/Donatello.png';
import raphaelSrc from '../images/Raphael.png';

type ImgProps = Omit<React.ImgHTMLAttributes<HTMLImageElement>, 'width' | 'height' | 'src'>;

type StaticImportLike = { src?: string } | string | undefined | null;

function resolveSrc(input: StaticImportLike): string {
	if (!input) return '';
	if (typeof input === 'string') return input;
	// Next.js may export an object with a 'src' property for static assets
	return typeof input.src === 'string' ? input.src : '';
}

const wrap = (srcLike: StaticImportLike, altFallback?: string) =>
	function WrappedImg(props: ImgProps) {
		const { alt, className, ...rest } = props;
		const src = resolveSrc(srcLike);
		return (
			<div className="relative flex items-center justify-center">
				<img
					src={src}
					alt={alt ?? altFallback}
					className={className ?? 'w-10 h-10 object-contain'}
					{...rest}
				/>
				{altFallback && (
					<span className="absolute bottom-0 text-xs font-bold text-gray-900 bg-white/90 px-1 py-0.5 rounded shadow-sm border border-gray-200">
						{altFallback}
					</span>
				)}
			</div>
		);
	};

export const Image_2 = wrap(twoSrc as unknown as string, '2');
export const Image_3 = wrap(threeSrc as unknown as string, '3');
export const Image_4 = wrap(fourSrc as unknown as string, '4');
export const Image_5 = wrap(fiveSrc as unknown as string, '5');
export const Image_6 = wrap(sixSrc as unknown as string, '6');
export const Image_7 = wrap(sevenSrc as unknown as string, '7');
export const Image_8 = wrap(eightSrc as unknown as string, '8');
export const Image_9 = wrap(nineSrc as unknown as string, '9');
export const Image_10 = wrap(tenSrc as unknown as string, '10');
export const Image_J = wrap(jSrc as unknown as string, 'J');
export const Image_Q = wrap(qSrc as unknown as string, 'Q');
export const Image_K = wrap(kSrc as unknown as string, 'K');
export const Image_A = wrap(aSrc as unknown as string, 'A');
export const Image_Joker = wrap(jokerSrc as unknown as string, 'Joker');

export const Image_You = wrap(youSrc as unknown as string);
export const Image_Michelangelo = wrap(michelangeloSrc as unknown as string);
export const Image_Donatello = wrap(donatelloSrc as unknown as string);
export const Image_Raphael = wrap(raphaelSrc as unknown as string);
// Named exports above provide React components that render <img src=...>