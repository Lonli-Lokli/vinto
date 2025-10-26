/**
 * Vitest setup file
 * Runs before all tests to configure mocks
 */

import { vi } from 'vitest';

// Create mock functions that can be accessed and inspected in tests
export const mockLogger = {
  log: vi.fn(),
  warn: vi.fn(),
  error: vi.fn(),
  setContext: vi.fn(),
  setUser: vi.fn(),
  addBreadcrumb: vi.fn(),
};

// Mock the logger to suppress console output and Sentry reports during tests
vi.mock('@vinto/shapes', async () => {
  const actual = await vi.importActual<typeof import('@vinto/shapes')>(
    '@vinto/shapes'
  );
  return {
    ...actual,
    logger: mockLogger,
  };
});
