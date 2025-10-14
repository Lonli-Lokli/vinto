import { reaction } from 'mobx';
import { GameClient } from '../../client/game-client';
import { UIStore } from '../stores/ui-store';

export class HeadlessService {
  private disposeReaction?: () => void;
  constructor(private gameClient: GameClient, private uiStore: UIStore) {
    this.setupReaction();
  }

  private setupReaction() {
    this.disposeReaction = reaction(
      // Watch for bot turn state - EXPLICIT state watching only
      () => ({
        isTossInActivated: this.gameClient.isTossInActivated,
      }),

      ({ isTossInActivated }) => {
        if (isTossInActivated) {
          this.uiStore.clearTemporaryCardVisibility();
        }
      }
    );
  }

  /**
   * Cleanup reactions when service is destroyed
   */
  dispose(): void {
    this.disposeReaction?.();
  }
}
