// lib/oracle-client.ts
import toast from 'react-hot-toast';
import { GameState, Difficulty, AIMove } from '../shapes';
import { GameToastService } from './toast-service';

export class OracleVintoClient {
  private gameId: string | null = null;
  private playerId: string | null = null;

  async createGameSession(humanPlayerId: string): Promise<string> {
    const loadingToast = GameToastService.loading('🌩️ Connecting to Cloud...');
    
    try {
      // Simulate Oracle connection delay
      const gameId = `vinto-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
      this.gameId = gameId;
      this.playerId = humanPlayerId;
      
      toast.dismiss(loadingToast);
      
      return gameId;
      
    } catch {
      toast.dismiss(loadingToast);
      GameToastService.cloudError('Failed to create session');
      throw new Error('Failed to create session');
    }
  }

  async requestAIMove(
    gameState: GameState, 
    aiPlayerId: string, 
    difficulty: Difficulty
  ): Promise<AIMove> {
    const aiPlayer = gameState.players.find(p => p.id === aiPlayerId);
    if (!aiPlayer || aiPlayer.isHuman) {
      GameToastService.error('Invalid AI player request');
      throw new Error('Invalid AI player');
    }

    const thinkingToast = GameToastService.aiThinking(aiPlayer.name);
    
    try {
      const startTime = Date.now();
      
      // Difficulty affects thinking time and decision quality
      const baseTimeByDiff: Record<Difficulty, number> = {
        basic: 500,
        moderate: 1000,
        hard: 1500,
        ultimate: 2000,
      };
      const baseTime = baseTimeByDiff[difficulty];
      const thinkTime = baseTime + Math.random() * 800;
      
      // Simulate Oracle AI computation
      await new Promise(resolve => setTimeout(resolve, thinkTime));
      
      // Difficulty affects AI confidence
      const baseConfidenceByDiff: Record<Difficulty, number> = {
        basic: 0.4,
        moderate: 0.65,
        hard: 0.85,
        ultimate: 0.95,
      };
      const confidenceRaw = baseConfidenceByDiff[difficulty] + Math.random() * 0.15;
      const confidence = Math.min(0.99, confidenceRaw);
      
      const result: AIMove = {
        type: 'draw',
        confidence,
        expectedValue: -1.5 + Math.random() * 2,
        reasoning: `${difficulty.toUpperCase()} Cloud: ${thinkTime > 1200 ? 'Deep MCTS analysis suggests' : 'Quick analysis shows'} drawing is optimal`,
        thinkingTime: Date.now() - startTime,
        networkTime: Date.now() - startTime
      };
      
      toast.dismiss(thinkingToast);
      
      GameToastService.aiMove(
        aiPlayer.name, 
        result.type, 
        Math.round(result.confidence * 100)
      );

      return result;
      
    } catch {
      toast.dismiss(thinkingToast);
      GameToastService.cloudError(`${aiPlayer.name} computation failed`);
      
      // Return fallback move to keep game playable
      return {
        type: 'draw',
        confidence: 0.25,
        expectedValue: -3,
        reasoning: 'Fallback move due to cloud error',
        thinkingTime: 0,
        error: true
      };
    }
  }

  async endGameSession(): Promise<void> {
    try {
      if (!this.gameId || !this.playerId) return;

      // Simulate cleanup call
      await new Promise(resolve => setTimeout(resolve, 200));

      GameToastService.info('👋 Game session ended');
      this.gameId = null;
      this.playerId = null;
      
    } catch {
      GameToastService.warning('Session cleanup incomplete - this is usually fine');
    }
  }

  getSessionInfo() {
    return {
      gameId: this.gameId,
      playerId: this.playerId,
      hasActiveSession: !!(this.gameId && this.playerId)
    };
  }
}