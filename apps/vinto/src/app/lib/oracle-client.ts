// lib/oracle-client.ts
import toast from 'react-hot-toast';
import { GameState, Difficulty, AIMove } from '../shapes';
import { GameToastService } from './toast-service';

export class OracleVintoClient {
  private gameId: string | null = null;
  private playerId: string | null = null;

  async createGameSession(humanPlayerId: string): Promise<string> {
    const loadingToast = GameToastService.loading('🌩️ Connecting to Oracle Cloud...');
    
    try {
      // Simulate Oracle connection delay
      await new Promise(resolve => setTimeout(resolve, 1500));
      
      const gameId = `vinto-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
      this.gameId = gameId;
      this.playerId = humanPlayerId;
      
      toast.dismiss(loadingToast);
      GameToastService.oracleConnected();
      GameToastService.gameStarted();
      
      return gameId;
      
    } catch (error) {
      toast.dismiss(loadingToast);
      GameToastService.oracleError('Failed to create session');
      throw error;
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
      const baseTime = difficulty === 'easy' ? 500 : difficulty === 'medium' ? 1000 : 1500;
      const thinkTime = baseTime + Math.random() * 800;
      
      // Simulate Oracle AI computation
      await new Promise(resolve => setTimeout(resolve, thinkTime));
      
      // Difficulty affects AI confidence
      const baseConfidence = difficulty === 'easy' ? 0.4 : difficulty === 'medium' ? 0.65 : 0.85;
      const confidence = baseConfidence + Math.random() * 0.15;
      
      const result: AIMove = {
        type: 'draw',
        confidence,
        expectedValue: -1.5 + Math.random() * 2,
        reasoning: `${difficulty.toUpperCase()} Oracle: ${thinkTime > 1200 ? 'Deep MCTS analysis suggests' : 'Quick analysis shows'} drawing is optimal`,
        thinkingTime: Date.now() - startTime,
        networkTime: Date.now() - startTime
      };
      
      toast.dismiss(thinkingToast);
      
      GameToastService.aiMove(
        aiPlayer.name, 
        result.type, 
        Math.round(result.confidence * 100)
      );

      // Show reasoning for higher difficulties with good confidence
      if (difficulty !== 'easy' && result.confidence > 0.75) {
        setTimeout(() => {
          GameToastService.info(`💭 ${result.reasoning}`, 3000);
        }, 800);
      }

      return result;
      
    } catch (error) {
      toast.dismiss(thinkingToast);
      GameToastService.oracleError(`${aiPlayer.name} computation failed`);
      
      // Return fallback move to keep game playable
      return {
        type: 'draw',
        confidence: 0.25,
        expectedValue: -3,
        reasoning: 'Fallback move due to Oracle error',
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
      
    } catch (error) {
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