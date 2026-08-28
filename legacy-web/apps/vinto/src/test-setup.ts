import { vi } from 'vitest';
import { createElement } from 'react';

// Mock Next.js Image component
vi.mock('next/image', () => ({
  __esModule: true,
  default: (props: any) => {
     
    const { width, height, priority, sizes, ...imgProps } = props;
    return createElement('img', {
      ...imgProps,
      width: width?.toString(),
      height: height?.toString(),
    });
  },
}));
