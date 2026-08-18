import { Difficulty } from '@vinto/shapes';
import { BotDecisionService } from './shapes';
import { MCTSBotDecisionService } from './mcts-bot-decision';

/**
 * Creates the bot decision service.
 *
 * There is exactly one bot engine. The former `v2` (`StrategicBotDecisionService`) was
 * removed because it read opponents' hidden hands; see
 * `docs/bot/BOT-ENGINE-DECISION.md`.
 */
export class BotDecisionServiceFactory {
  static create(difficulty: Difficulty): BotDecisionService {
    // Difficulty controls memory accuracy and MCTS iterations, not decision quality
    return new MCTSBotDecisionService(difficulty);
  }
}
