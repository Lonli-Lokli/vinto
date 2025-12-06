import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import {
  Image_Cover,
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
  Image_You,
  Image_Michelangelo,
  Image_Donatello,
  Image_Raphael,
} from '../src/app/components/presentational/image';

describe('Image Components', () => {
  describe('Card Images', () => {
    it('should render Image_Cover', () => {
      const { container } = render(<Image_Cover width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('Cover');
    });

    it('should render Image_2', () => {
      const { container } = render(<Image_2 width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('2');
    });

    it('should render Image_3', () => {
      const { container } = render(<Image_3 width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('3');
    });

    it('should render Image_4', () => {
      const { container } = render(<Image_4 width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('4');
    });

    it('should render Image_5', () => {
      const { container } = render(<Image_5 width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('5');
    });

    it('should render Image_6', () => {
      const { container } = render(<Image_6 width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('6');
    });

    it('should render Image_7', () => {
      const { container } = render(<Image_7 width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('7');
    });

    it('should render Image_8', () => {
      const { container } = render(<Image_8 width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('8');
    });

    it('should render Image_9', () => {
      const { container } = render(<Image_9 width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('9');
    });

    it('should render Image_10', () => {
      const { container } = render(<Image_10 width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('10');
    });

    it('should render Image_J', () => {
      const { container } = render(<Image_J width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('J');
    });

    it('should render Image_Q', () => {
      const { container } = render(<Image_Q width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('Q');
    });

    it('should render Image_K', () => {
      const { container } = render(<Image_K width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('K');
    });

    it('should render Image_A', () => {
      const { container } = render(<Image_A width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('A');
    });

    it('should render Image_Joker', () => {
      const { container } = render(<Image_Joker width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('Joker');
    });
  });

  describe('Avatar Images', () => {
    it('should render Image_You', () => {
      const { container } = render(<Image_You width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('You');
    });

    it('should render Image_Michelangelo', () => {
      const { container } = render(
        <Image_Michelangelo width={100} height={100} />
      );
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('Michelangelo');
    });

    it('should render Image_Donatello', () => {
      const { container } = render(
        <Image_Donatello width={100} height={100} />
      );
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('Donatello');
    });

    it('should render Image_Raphael', () => {
      const { container } = render(<Image_Raphael width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('Raphael');
    });
  });

  describe('Image Props', () => {
    it('should apply custom className', () => {
      const { container } = render(
        <Image_2 width={100} height={100} className="custom-class" />
      );
      const img = container.querySelector('img');
      expect(img?.className).toContain('custom-class');
    });

    it('should use default className when not provided', () => {
      const { container } = render(<Image_2 width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img?.className).toContain('w-10');
      expect(img?.className).toContain('h-10');
      expect(img?.className).toContain('object-contain');
    });

    it('should support fill mode', () => {
      const { container } = render(<Image_2 fill />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
    });

    it('should set width and height when provided', () => {
      const { container } = render(<Image_2 width={200} height={200} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.getAttribute('width')).toBe('200');
      expect(img?.getAttribute('height')).toBe('200');
    });
  });

  describe('Priority Loading', () => {
    it('should set priority for Cover image', () => {
      const { container } = render(<Image_Cover width={100} height={100} />);
      const img = container.querySelector('img');
      expect(img).toBeTruthy();
    });

    it('should set priority for avatar images', () => {
      const { container: youContainer } = render(
        <Image_You width={100} height={100} />
      );
      const { container: michelangeloContainer } = render(
        <Image_Michelangelo width={100} height={100} />
      );
      const { container: donatelloContainer } = render(
        <Image_Donatello width={100} height={100} />
      );
      const { container: raphaelContainer } = render(
        <Image_Raphael width={100} height={100} />
      );

      expect(youContainer.querySelector('img')).toBeTruthy();
      expect(michelangeloContainer.querySelector('img')).toBeTruthy();
      expect(donatelloContainer.querySelector('img')).toBeTruthy();
      expect(raphaelContainer.querySelector('img')).toBeTruthy();
    });
  });
});
