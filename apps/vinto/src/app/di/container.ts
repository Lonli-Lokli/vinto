// di/container.ts
/**
 * Dependency Injection Container Configuration
 *
 * Uses tsyringe for IoC (Inversion of Control) and DI
 */
import { container } from 'tsyringe';

// Store tokens for type-safe injection
export const TOKENS = {
  // Services
  BotDecisionService: Symbol('BotDecisionService'),
  TimerService: Symbol('TimerService'),
  ToastService: Symbol('ToastService'),
} as const;

/**
 * Global DI container instance
 */
export const appContainer = container;

/**
 * Reset the container (useful for testing)
 */
export function resetContainer(): void {
  container.clearInstances();
}

/**
 * Check if container is configured
 */
export function isContainerConfigured(): boolean {
  try {
    return container.isRegistered(TOKENS.ToastService);
  } catch {
    return false;
  }
}
