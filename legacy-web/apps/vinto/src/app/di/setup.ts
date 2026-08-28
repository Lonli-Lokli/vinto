// di/setup.ts
/**
 * DI Container Setup
 * Registers all services and stores with the DI container
 */

import 'reflect-metadata';
import { container } from 'tsyringe';

// Stores
import { CardAnimationStore } from '../stores';
import { UIStore } from '../stores';
import { BugReportStore } from '../stores/bug-report-store';

// Services
import { AnimationService } from '../services/animation-service';
import { AnimationPositionCapture } from '../services/animation-position-capture';
import { HeadlessService } from '../services/headless-service';

/**
 * Configure the DI container with all dependencies
 */
export function setupDIContainer() {
  // Register UI stores
  container.registerSingleton(CardAnimationStore);
  container.registerSingleton(UIStore);
  container.registerSingleton(BugReportStore);

  // Register services
  container.registerSingleton(AnimationPositionCapture);
  container.registerSingleton(AnimationService);
  container.registerSingleton(HeadlessService);
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
