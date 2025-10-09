// app/utils/featureFlags.ts

/**
 * Feature Flags for Gradual GameClient Migration
 *
 * Enable/disable new architecture per component for safe rollout.
 *
 * Usage:
 * ```tsx
 * import { isFeatureEnabled } from '../utils/featureFlags';
 * import { GameControls as OldGameControls } from './game-controls';
 * import { GameControlsNew } from './game-controls-new';
 *
 * const GameControls = isFeatureEnabled('gameControls')
 *   ? GameControlsNew
 *   : OldGameControls;
 * ```
 */

export const FEATURE_FLAGS = {
  // UI Components
  gameControls: false, // ❌ Disabled - needs state sync with old stores
  playerArea: false, // Enable new PlayerArea component
  deckArea: false, // Enable new DeckArea component
  gameInfo: false, // Enable new GameInfo component

  // Bot AI
  botAI: false, // Enable new BotAI adapter

  // Animation
  animations: false, // Enable new animation system

  // Advanced Features
  stateSync: false, // Enable GameClient-to-Store synchronization (temporary)
  debugMode: true, // Enable debug logging and dev tools
} as const;

export type FeatureFlag = keyof typeof FEATURE_FLAGS;

/**
 * Check if a feature is enabled
 */
export function isFeatureEnabled(feature: FeatureFlag): boolean {
  return FEATURE_FLAGS[feature];
}

/**
 * Enable a feature at runtime (useful for testing)
 */
export function enableFeature(feature: FeatureFlag): void {
  // @ts-expect-error - We're intentionally mutating the const for runtime flags
  FEATURE_FLAGS[feature] = true;
  console.log(`[FeatureFlags] Enabled: ${feature}`);
}

/**
 * Disable a feature at runtime
 */
export function disableFeature(feature: FeatureFlag): void {
  // @ts-expect-error - We're intentionally mutating the const for runtime flags
  FEATURE_FLAGS[feature] = false;
  console.log(`[FeatureFlags] Disabled: ${feature}`);
}

/**
 * Get all enabled features
 */
export function getEnabledFeatures(): FeatureFlag[] {
  return (Object.keys(FEATURE_FLAGS) as FeatureFlag[]).filter(
    (key) => FEATURE_FLAGS[key]
  );
}

/**
 * Debug: Log all feature flags
 */
export function logFeatureFlags(): void {
  console.group('[FeatureFlags] Current Configuration');
  Object.entries(FEATURE_FLAGS).forEach(([key, value]) => {
    console.log(`  ${key}: ${value ? '✅' : '❌'}`);
  });
  console.groupEnd();
}
