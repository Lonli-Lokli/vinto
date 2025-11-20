// SHARED coalition plans across all bot instances for the same game

import { CoalitionMemberPlan } from './coalition-round-solver';

// Key: gameId, Value: Map of playerId -> plan
const sharedCoalitionPlans = new Map<
  string,
  Map<string, CoalitionMemberPlan>
>();

export function getCoalitionPlanForPlayer(
  gameId: string,
  playerId: string
): CoalitionMemberPlan | null {
  const plan = sharedCoalitionPlans.get(gameId);
  if (plan) {
    return plan.get(playerId) ?? null;
  }

  return null;
}

export function hasCoalitionPlan(gameId: string): boolean {
  return sharedCoalitionPlans.has(gameId);
}

export function createCoalitionPlan(gameId: string): void {
  if (!sharedCoalitionPlans.has(gameId)) {
    sharedCoalitionPlans.set(gameId, new Map());
  }
}

export function setCoalitionPlanForPlayer(
  gameId: string,
  playerId: string,
  memberPlan: CoalitionMemberPlan
): void {
  let gamePlans = sharedCoalitionPlans.get(gameId);
  if (!gamePlans) {
    gamePlans = new Map<string, CoalitionMemberPlan>();
    sharedCoalitionPlans.set(gameId, gamePlans);
  }
  gamePlans.set(playerId, memberPlan);
}
