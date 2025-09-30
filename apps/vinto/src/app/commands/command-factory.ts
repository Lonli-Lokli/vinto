// commands/command-factory.ts
/**
 * Factory for creating game commands
 * Centralizes command creation and makes it easier to use
 */

import { ICommand } from './command';
import {
  DrawCardCommand,
  SwapCardsCommand,
  PeekCardCommand,
  DiscardCardCommand,
  ReplaceCardCommand,
  AdvanceTurnCommand,
  DeclareKingActionCommand,
  TossInCardCommand,
  AddPenaltyCardCommand,
} from './game-commands';
import { PlayerStore } from '../stores/player-store';
import { DeckStore } from '../stores/deck-store';
import { ActionStore } from '../stores/action-store';
import { Card, Rank } from '../shapes';

export class CommandFactory {
  constructor(
    private playerStore: PlayerStore,
    private deckStore: DeckStore,
    private actionStore: ActionStore
  ) {}

  drawCard(playerId: string): ICommand {
    return new DrawCardCommand(
      this.playerStore,
      this.deckStore,
      playerId
    );
  }

  swapCards(
    player1Id: string,
    position1: number,
    player2Id: string,
    position2: number
  ): ICommand {
    return new SwapCardsCommand(
      this.playerStore,
      player1Id,
      position1,
      player2Id,
      position2
    );
  }

  peekCard(
    playerId: string,
    position: number,
    isPermanent: boolean = false
  ): ICommand {
    return new PeekCardCommand(
      this.playerStore,
      playerId,
      position,
      isPermanent
    );
  }

  discardCard(card: Card): ICommand {
    return new DiscardCardCommand(
      this.deckStore,
      card
    );
  }

  replaceCard(
    playerId: string,
    position: number,
    newCard: Card
  ): ICommand {
    return new ReplaceCardCommand(
      this.playerStore,
      playerId,
      position,
      newCard
    );
  }

  advanceTurn(fromPlayerId: string): ICommand {
    return new AdvanceTurnCommand(
      this.playerStore,
      fromPlayerId
    );
  }

  declareKingAction(rank: Rank): ICommand {
    return new DeclareKingActionCommand(
      this.actionStore,
      rank
    );
  }

  tossInCard(
    playerId: string,
    position: number,
    matchingRank: Rank
  ): ICommand {
    return new TossInCardCommand(
      this.playerStore,
      playerId,
      position,
      matchingRank
    );
  }

  addPenaltyCard(playerId: string): ICommand {
    return new AddPenaltyCardCommand(
      this.playerStore,
      this.deckStore,
      playerId
    );
  }
}
