// di/setup.ts
/**
 * DI Container Setup
 * Registers all services and stores with the DI container
 */

import 'reflect-metadata';
import { container } from 'tsyringe';

// Stores
import { CardAnimationStore } from '../stores';

// Services
import { UIStore } from '../stores';
import { AnimationService } from '../services/animation-service';
import { AnimationPositionCapture } from '../services/animation-position-capture';

/**
 * Configure the DI container with all dependencies
 */
export function setupDIContainer() {
  // Register UI stores
  container.registerSingleton(CardAnimationStore);
  container.registerSingleton(UIStore);

  // Register services
  container.registerSingleton(AnimationPositionCapture);
  container.registerSingleton(AnimationService);
}

/**
 * Get a singleton instance from the container
 */
export function getInstance<T>(token: any): T {
  return container.resolve<T>(token);
}

/**
 * Clear and reset the container
 */
export function resetDIContainer() {
  container.clearInstances();
}

/**
 * Check if DI is configured
 */
export function isDIConfigured(): boolean {
  return container.isRegistered(UIStore);
}
