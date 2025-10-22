// components/CoalitionLeaderModal.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Crown } from 'lucide-react';
import { useUIStore } from '../di-provider';
import { ContinueButton, OpponentSelectButton } from '../buttons';
import { GameActions } from '@vinto/engine';
import { useGameClient } from '@vinto/local-client';

export const CoalitionLeaderModal = observer(() => {
  const gameClient = useGameClient();
  const uiStore = useUIStore();

  const dialogRef = React.useRef<HTMLDialogElement>(null);

  // Open/close dialog imperatively
  React.useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (uiStore.showCoalitionLeaderSelection && !dialog.open) {
      dialog.showModal();
    } else if (!uiStore.showCoalitionLeaderSelection && dialog.open) {
      dialog.close('close');
    }
  }, [uiStore.showCoalitionLeaderSelection]);

  // Light dismiss (click backdrop)
  React.useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const handleLightDismiss = (e: MouseEvent) => {
      if (e.target === dialog) {
        dialog.close('dismiss');
      }
    };
    dialog.addEventListener('click', handleLightDismiss);
    return () => dialog.removeEventListener('click', handleLightDismiss);
  }, []);

  // Handle close event
  React.useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const handleClose = (_e: Event) => {
      uiStore.closeCoalitionLeaderSelection();
    };
    dialog.addEventListener('close', handleClose);
    return () => dialog.removeEventListener('close', handleClose);
  }, [uiStore]);

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
      modal-mode="mega"
      className="z-50 bg-surface-primary border-primary border-2 rounded-2xl shadow-theme-lg max-w-2xl w-full animate-fade-in"
    >
      <form
        method="dialog"
        className="grid grid-rows-[auto_1fr_auto] max-h-[80vh]"
      >
        <header className="text-center mb-0 bg-surface-secondary px-8 py-6 rounded-t-2xl">
          <div className="flex items-center justify-center gap-2 mb-2">
            <Crown className="text-warning" size={32} />
            <h2 className="text-2xl font-bold text-primary">
              Select Coalition Leader
            </h2>
            <button
              type="button"
              onClick={() => dialogRef.current?.close('close')}
              className="text-muted-foreground hover:text-accent transition-colors rounded-full focus:outline-none focus:ring-2 focus:ring-accent p-2 aspect-square ml-auto"
              aria-label="Close coalition leader dialog"
            >
              <span aria-hidden="true">✕</span>
            </button>
          </div>
          <p className="text-secondary">
            {vintoCaller?.name} called Vinto! Choose who will lead the
            coalition.
          </p>
          <p className="text-sm text-secondary mt-2">
            The leader will see all coalition cards and play for the team.
          </p>
        </header>
        <article className="overflow-y-auto max-h-full px-8 py-6 bg-surface-primary flex flex-col gap-5">
          <div className="grid grid-cols-2 gap-4 mb-2">
            {coalitionMembers.map((player) => {
              const isCoalitionLeader = coalitionLeaderId === player.id;
              return (
                <div
                  key={player.id}
                  className={`
                    relative p-2 rounded-xl border-2 transition-all
                    ${
                      isCoalitionLeader
                        ? 'border-warning bg-warning-light'
                        : 'border-primary bg-surface-primary'
                    }
                  `}
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
                    <div className="absolute top-0 right-0 flex items-center gap-1 bg-warning text-white text-xs font-semibold px-2 py-1 rounded-bl-lg rounded-tr-lg">
                      <Crown size={12} />
                      <span>Leader</span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </article>
        <footer className="bg-surface-secondary flex flex-wrap gap-3 justify-center items-center px-8 py-6 rounded-b-2xl">
          <menu className="flex flex-row gap-3 p-0 m-0 w-full justify-center">
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
