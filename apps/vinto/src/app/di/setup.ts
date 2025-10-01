// di/setup.ts
/**
 * DI Container Setup
 * Registers all services and stores with the DI container
 */

import 'reflect-metadata';
import { container } from 'tsyringe';

// Stores
import {
  PlayerStore,
  DeckStore,
  GamePhaseStore,
  ActionStore,
  TossInStore,
  ReplayStore,
  CardAnimationStore,
  GameStore,
} from '../stores';

// Command system
import { CommandFactory } from '../commands/command-factory';
import { CommandHistory } from '../commands/command-history';
import { GameStateManager } from '../commands/game-state-manager';

// Services
import { BotDecisionServiceFactory } from '../services/bot-decision';
import { ActionCoordinator } from '../stores/action-coordinator';

/**
 * Configure the DI container with all dependencies
 */
export function setupDIContainer(
  difficulty: 'easy' | 'moderate' | 'hard' = 'moderate'
) {
  // Register stores as singletons
  container.registerSingleton(PlayerStore);
  container.registerSingleton(DeckStore);
  container.registerSingleton(GamePhaseStore);
  container.registerSingleton(ActionStore);
  container.registerSingleton(TossInStore);
  container.registerSingleton(ReplayStore);
  container.registerSingleton(CardAnimationStore);

  // Register command system as singletons
  container.registerSingleton(CommandHistory);
  container.registerSingleton(CommandFactory);

  // Register BotDecisionService
  container.register('BotDecisionService', {
    useFactory: () => BotDecisionServiceFactory.create(difficulty),
  });

  // Register ActionCoordinator
  container.registerSingleton(ActionCoordinator);

  // Register GameStore last (depends on everything)
  container.registerSingleton(GameStore);

  // Register GameStateManager
  container.registerSingleton(GameStateManager);
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
