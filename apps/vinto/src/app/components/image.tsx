
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

type ImgProps = React.ImgHTMLAttributes<HTMLImageElement>;
const wrap = (src: string, altFallback: string) =>
	function WrappedImg(props: ImgProps) {
		const { alt, className, ...rest } = props;
		return (
			<img
				src={src as unknown as string}
				alt={alt ?? altFallback}
				className={className ?? 'w-10 h-10 object-contain'}
				{...rest}
			/>
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


// Named exports above provide React components that render <img src=...>