// commands/command-factory.ts
/**
 * Factory for creating game commands
 * Centralizes command creation and makes it easier to use
 */

import { injectable, inject } from 'tsyringe';
import { ICommand, CommandData } from './command';
import {
  InitializeGameCommand,
  DrawCardCommand,
  SwapCardsCommand,
  PeekCardCommand,
  PlayActionCardCommand,
  DiscardCardCommand,
  ReplaceCardCommand,
  AdvanceTurnCommand,
  DeclareKingActionCommand,
  TossInCardCommand,
  AddPenaltyCardCommand,
} from './game-commands';
import {
  PlayerStore,
  DeckStore,
  ActionStore,
  GamePhaseStore,
  TossInStore,
  CardAnimationStore,
} from '../stores';
import { Card, Rank, Difficulty, NeverError } from '../shapes';

@injectable()
export class CommandFactory {
  constructor(
    @inject(PlayerStore) private playerStore: PlayerStore,
    @inject(DeckStore) private deckStore: DeckStore,
    @inject(ActionStore) private actionStore: ActionStore,
    @inject(GamePhaseStore) private gamePhaseStore: GamePhaseStore,
    @inject(TossInStore) private tossInStore: TossInStore,
    @inject(CardAnimationStore) private cardAnimationStore: CardAnimationStore
  ) {}

  initializeGame(difficulty: Difficulty): ICommand {
    return new InitializeGameCommand(
      this.playerStore,
      this.deckStore,
      this.gamePhaseStore,
      this.actionStore,
      this.tossInStore,
      difficulty
    );
  }

  drawCard(playerId: string): ICommand {
    return new DrawCardCommand(
      this.playerStore,
      this.deckStore,
      this.cardAnimationStore,
      playerId
    );
  }

  swapCards(
    player1Id: string,
    position1: number,
    player2Id: string,
    position2: number,
    revealed = false
  ): ICommand {
    return new SwapCardsCommand(
      this.playerStore,
      this.cardAnimationStore,
      player1Id,
      position1,
      player2Id,
      position2,
      revealed
    );
  }

  peekCard(playerId: string, position: number, isPermanent = false): ICommand {
    return new PeekCardCommand(
      this.playerStore,
      playerId,
      position,
      isPermanent
    );
  }

  playActionCard(
    card: Card,
    playerId: string,
    actionDetails?: { type: string; targets?: any[] }
  ): ICommand {
    const player = this.playerStore.getPlayer(playerId);
    const playerName = player?.name || 'Unknown';

    return new PlayActionCardCommand(
      this.deckStore,
      this.actionStore,
      this.cardAnimationStore,
      card,
      playerId,
      playerName,
      actionDetails
    );
  }

  discardCard(card: Card, playerId?: string, skipAnimation = false): ICommand {
    return new DiscardCardCommand(
      this.deckStore,
      this.cardAnimationStore,
      card,
      playerId,
      skipAnimation
    );
  }

  replaceCard(playerId: string, position: number, newCard: Card): ICommand {
    return new ReplaceCardCommand(
      this.playerStore,
      this.cardAnimationStore,
      playerId,
      position,
      newCard
    );
  }

  advanceTurn(fromPlayerId: string): ICommand {
    return new AdvanceTurnCommand(this.playerStore, fromPlayerId);
  }

  declareKingAction(rank: Rank): ICommand {
    return new DeclareKingActionCommand(this.actionStore, rank);
  }

  tossInCard(playerId: string, position: number, matchingRank: Rank): ICommand {
    return new TossInCardCommand(
      this.playerStore,
      this.cardAnimationStore,
      playerId,
      position,
      matchingRank
    );
  }

  addPenaltyCard(playerId: string): ICommand {
    return new AddPenaltyCardCommand(
      this.playerStore,
      this.deckStore,
      this.cardAnimationStore,
      playerId
    );
  }

  /**
   * Reconstruct a command from serialized command data
   */
  fromCommandData(data: CommandData): ICommand {
    const { type, payload } = data;

    switch (type) {
      case 'INITIALIZE_GAME':
        return this.initializeGame(payload.difficulty as Difficulty);

      case 'DRAW_CARD':
        return this.drawCard(payload.playerId as string);

      case 'SWAP_CARDS':
        return this.swapCards(
          payload.player1Id as string,
          payload.position1 as number,
          payload.player2Id as string,
          payload.position2 as number
        );

      case 'PEEK_CARD':
        return this.peekCard(
          payload.playerId as string,
          payload.position as number,
          payload.isPermanent as boolean
        );

      case 'PLAY_ACTION_CARD':
        return this.playActionCard(
          payload.card as Card,
          payload.playerId as string
        );

      case 'DISCARD_CARD':
        return this.discardCard(payload.card as Card);

      case 'REPLACE_CARD':
        return this.replaceCard(
          payload.playerId as string,
          payload.position as number,
          payload.newCard as Card
        );

      case 'ADVANCE_TURN':
        return this.advanceTurn(payload.fromPlayerId as string);

      case 'DECLARE_KING_ACTION':
        return this.declareKingAction(payload.rank as Rank);

      case 'TOSS_IN':
        return this.tossInCard(
          payload.playerId as string,
          payload.position as number,
          payload.matchingRank as Rank
        );

      case 'ADD_PENALTY_CARD':
        return this.addPenaltyCard(payload.playerId as string);

      default:
        throw new NeverError(type);
    }
  }
}
