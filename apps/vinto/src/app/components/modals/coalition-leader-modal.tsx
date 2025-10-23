// components/CoalitionLeaderModal.tsx
'use client';

import React, { useEffect, useRef } from 'react';
import { observer } from 'mobx-react-lite';
import { Crown } from 'lucide-react';
import { useUIStore } from '../di-provider';
import { ContinueButton, OpponentSelectButton } from '../buttons';
import { GameActions } from '@vinto/engine';
import { useGameClient } from '@vinto/local-client';
import { initDialog } from './dialog';

export const CoalitionLeaderModal = observer(() => {
  const gameClient = useGameClient();
  const uiStore = useUIStore();
  const dialogRef = useRef<HTMLDialogElement>(null);

  // Initialize dialog with custom events and utilities
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    void initDialog(dialog);

    // Listen to custom events
    const handleClosed = () => {
      uiStore.closeCoalitionLeaderSelection();
    };

    dialog.addEventListener('closed', handleClosed);

    return () => {
      dialog.removeEventListener('closed', handleClosed);
    };
  }, [uiStore]);

  // Open/close dialog imperatively
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (uiStore.showCoalitionLeaderSelection && !dialog.open) {
      dialog.showModal();
    } else if (!uiStore.showCoalitionLeaderSelection && dialog.open) {
      dialog.close('close');
    }
  }, [uiStore.showCoalitionLeaderSelection]);

  // Get coalition members (everyone except Vinto caller)
  const players = gameClient.visualState.players;
  const coalitionMembers = players.filter((p) => !p.isVintoCaller);
  const vintoCaller = players.find((p) => p.isVintoCaller);
  const coalitionLeaderId = gameClient.visualState.coalitionLeaderId;

  const handleSelectLeader = (playerId: string) => {
    gameClient.dispatch(GameActions.setCoalitionLeader(playerId));
  };

  return (
    <dialog
      ref={dialogRef}
      id="CoalitionLeaderDialog"
      {...({ 'modal-mode': 'mega' } as React.HTMLAttributes<HTMLDialogElement>)}
      className="dialog-mega coalition-leader-dialog"
      {...({ loading: 'true' } as React.HTMLAttributes<HTMLDialogElement>)}
    >
      <form method="dialog">
        <header>
          <div className="dialog-header-content">
            <Crown className="dialog-icon" size={32} />
            <h3>Select Coalition Leader</h3>
            <button
              type="button"
              onClick={() => dialogRef.current?.close('close')}
              className="dialog-close-btn"
              aria-label="Close coalition leader dialog"
            >
              <span aria-hidden="true">✕</span>
            </button>
          </div>
          <p className="dialog-subtitle">
            {vintoCaller?.name} called Vinto! Choose who will lead the
            coalition.
          </p>
          <p className="dialog-description">
            The leader will see all coalition cards and play for the team.
          </p>
        </header>

        <article>
          <div className="coalition-grid">
            {coalitionMembers.map((player) => {
              const isCoalitionLeader = coalitionLeaderId === player.id;
              return (
                <div
                  key={player.id}
                  className={`coalition-player-card ${
                    isCoalitionLeader ? 'is-leader' : ''
                  }`}
                >
                  <OpponentSelectButton
                    opponentName={`${player.name}${
                      player.isHuman ? ' (You)' : ''
                    }`}
                    onClick={() => handleSelectLeader(player.id)}
                    showAvatar={true}
                    player={player}
                    isSelected={isCoalitionLeader}
                    className="w-full"
                  />
                  {isCoalitionLeader && (
                    <div className="leader-badge">
                      <Crown size={12} />
                      <span>Leader</span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </article>

        <footer>
          <menu>
            <ContinueButton
              onClick={() => dialogRef.current?.close('confirm')}
              disabled={!coalitionLeaderId}
            >
              Confirm Leader
            </ContinueButton>
          </menu>
        </footer>
      </form>
    </dialog>
  );
});
