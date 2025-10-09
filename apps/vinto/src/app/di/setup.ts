// di/setup.ts
/**
 * DI Container Setup
 * Registers all services and stores with the DI container
 */

import 'reflect-metadata';
import { container } from 'tsyringe';

// Stores (only active stores - old stores removed in migration)
import {
  CardAnimationStore,
  GameStore,
} from '../stores';

// Services
import { ActionCoordinator } from '../stores/action-coordinator';
import { BotDecisionServiceFactory } from '../services/mcts-bot-decision';
import { UIStore } from '../stores/ui-store';

/**
 * Configure the DI container with all dependencies
 */
export function setupDIContainer(
  difficulty: 'easy' | 'moderate' | 'hard' = 'moderate'
) {
  // Register active stores as singletons
  container.registerSingleton(CardAnimationStore);
  container.registerSingleton(UIStore);

  // Register BotDecisionService
  container.register('BotDecisionService', {
    useFactory: () => BotDecisionServiceFactory.create(difficulty),
  });

  // Register ActionCoordinator
  container.registerSingleton(ActionCoordinator);

  // Register GameStore last (depends on everything)
  container.registerSingleton(GameStore);
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
  return container.isRegistered(GameStore);
}
