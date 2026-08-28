// components/VintoConfirmationModal.tsx
'use client';

import React, { useEffect, useRef } from 'react';
import { observer } from 'mobx-react-lite';
import { CallVintoButton, CancelButton } from '../buttons';
import { useUIStore } from '../di-provider';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';
import { initDialog } from './dialog';

export const VintoConfirmationModal = observer(() => {
  const uiStore = useUIStore();
  const gameClient = useGameClient();
  const dialogRef = useRef<HTMLDialogElement>(null);

  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);

  // Initialize dialog with custom events and utilities
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    void initDialog(dialog);

    // Listen to custom events
    const handleClosed = () => {
      uiStore.setShowVintoConfirmation(false);
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

    if (uiStore.showVintoConfirmation && !dialog.open) {
      dialog.showModal();
    } else if (!uiStore.showVintoConfirmation && dialog.open) {
      dialog.close('close');
    }
  }, [uiStore.showVintoConfirmation]);

  if (!humanPlayer) return null;

  return (
    <dialog
      ref={dialogRef}
      id="VintoConfirmationDialog"
      {...({ 'modal-mode': 'mini' } as React.HTMLAttributes<HTMLDialogElement>)}
      className="dialog-mini vinto-confirmation-dialog"
      {...({ loading: 'true' } as React.HTMLAttributes<HTMLDialogElement>)}
    >
      <form method="dialog">
        <article>
          <div className="dialog-warning-header">
            <span className="dialog-warning-icon">⚠️</span>
            <h3>Call Vinto?</h3>
          </div>
          <p className="dialog-text-center">
            This ends the round immediately. All other players get one final
            turn.
          </p>
        </article>

        <footer>
          <menu>
            <CallVintoButton
              onClick={() => {
                gameClient.dispatch(GameActions.callVinto(humanPlayer.id));
                dialogRef.current?.close('confirm');
              }}
              fullWidth
              className="py-3 px-4 text-base font-bold min-h-[48px]"
              data-testid="confirm-vinto"
            >
              Yes, Call Vinto
            </CallVintoButton>
            <CancelButton
              onClick={() => dialogRef.current?.close('cancel')}
              fullWidth
              className="py-3 px-4 text-base font-bold min-h-[48px]"
              autoFocus
              data-testid="cancel-vinto"
            />
          </menu>
        </footer>
      </form>
    </dialog>
  );
});

VintoConfirmationModal.displayName = 'VintoConfirmationModal';
