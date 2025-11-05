import { BotVersion, Difficulty, NeverError } from '@vinto/shapes';
import { StrategicBotDecisionService } from './strategic-bot-decision';
import { BotDecisionService } from './shapes';
import { MCTSBotDecisionService } from './mcts-bot-decision';

// Factory for creating bot decision services
export class BotDecisionServiceFactory {
  static create(
    difficulty: Difficulty,
    botVersion: BotVersion
  ): BotDecisionService {
    switch (botVersion) {
      case 'v1':
        // Difficulty controls memory accuracy and MCTS iterations, not decision quality
        return new MCTSBotDecisionService(difficulty);
      case 'v2':
        return new StrategicBotDecisionService(difficulty);
      default:
        throw new NeverError(botVersion);
    }
  }
}
