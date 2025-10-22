// components/VintoConfirmationModal.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { CallVintoButton, CancelButton } from '../buttons';
import { useUIStore } from '../di-provider';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';

export const VintoConfirmationModal = observer(() => {
  const uiStore = useUIStore();
  const gameClient = useGameClient();

  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);
  const dialogRef = React.useRef<HTMLDialogElement>(null);

  // Open/close dialog imperatively
  React.useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (uiStore.showVintoConfirmation && !dialog.open) {
      dialog.showModal();
    } else if (!uiStore.showVintoConfirmation && dialog.open) {
      dialog.close('close');
    }
  }, [uiStore.showVintoConfirmation]);

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
      uiStore.setShowVintoConfirmation(false);
    };
    dialog.addEventListener('close', handleClose);
    return () => dialog.removeEventListener('close', handleClose);
  }, [uiStore]);

  if (!humanPlayer) return null;

  return (
    <dialog
      ref={dialogRef}
      id="VintoConfirmationDialog"
      modal-mode="mega"
      className="z-50 bg-surface-primary border-warning border-2 rounded-xl shadow-theme-lg max-w-sm w-full animate-fade-in"
    >
      <form
        method="dialog"
        className="grid grid-rows-[auto_1fr_auto] max-h-[80vh]"
      >
        <header className="flex items-center justify-center gap-2 mb-0 bg-surface-secondary px-6 py-4 rounded-t-xl">
          <span className="text-3xl">⚠️</span>
          <h2 className="text-xl md:text-2xl font-bold text-primary">
            Call Vinto?
          </h2>
          <button
            type="button"
            onClick={() => dialogRef.current?.close('close')}
            className="text-muted-foreground hover:text-accent transition-colors rounded-full focus:outline-none focus:ring-2 focus:ring-accent p-2 aspect-square ml-auto"
            aria-label="Close confirmation dialog"
          >
            <span aria-hidden="true">✕</span>
          </button>
        </header>
        <article className="overflow-y-auto max-h-full px-6 py-4 bg-surface-primary flex flex-col gap-5">
          <p className="text-base md:text-lg text-secondary text-center leading-relaxed mb-2">
            This ends the round immediately. All other players get one final
            turn.
          </p>
        </article>
        <footer className="bg-surface-secondary flex flex-wrap gap-3 justify-between items-center px-6 py-4 rounded-b-xl">
          <menu className="flex flex-col gap-3 p-0 m-0 w-full">
            <CallVintoButton
              onClick={() => {
                gameClient.dispatch(GameActions.callVinto(humanPlayer.id));
                dialogRef.current?.close('confirm');
              }}
              fullWidth
              className="py-3 px-4 text-base font-bold min-h-[48px]"
            >
              Yes, Call Vinto
            </CallVintoButton>
            <CancelButton
              onClick={() => dialogRef.current?.close('cancel')}
              fullWidth
              className="py-3 px-4 text-base font-bold min-h-[48px]"
              autoFocus
            />
          </menu>
        </footer>
      </form>
    </dialog>
  );
});

VintoConfirmationModal.displayName = 'VintoConfirmationModal';
