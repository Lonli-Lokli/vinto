// components/VintoConfirmationModal.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { CallVintoButton, CancelButton } from '../buttons';

interface VintoConfirmationModalProps {
  isOpen: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export const VintoConfirmationModal = observer(
  ({ isOpen, onConfirm, onCancel }: VintoConfirmationModalProps) => {
    if (!isOpen) return null;

    return (
      <>
        {/* Backdrop */}
        <div
          className="fixed inset-0 bg-black/60 backdrop-blur-sm z-[200] animate-in fade-in duration-200"
          onClick={onCancel}
        />

        {/* Modal */}
        <div className="fixed inset-0 z-[201] flex items-center justify-center p-4 pointer-events-none">
          <div
            className="bg-white rounded-lg shadow-2xl border-2 border-orange-400 max-w-sm w-full p-6 pointer-events-auto animate-in zoom-in-95 duration-200"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Title */}
            <div className="flex items-center justify-center gap-2 mb-4">
              <span className="text-3xl">⚠️</span>
              <h2 className="text-xl md:text-2xl font-bold text-gray-900">
                Call Vinto?
              </h2>
            </div>

            {/* Message */}
            <p className="text-base md:text-lg text-gray-700 text-center leading-relaxed mb-6">
              This ends the round immediately. All other players get one final
              turn.
            </p>

            {/* Buttons - Vertical stack */}
            <div className="space-y-3">
              {/* Confirm button - Orange, top */}
              <CallVintoButton
                onClick={onConfirm}
                fullWidth
                className="py-3 px-4 text-base font-bold min-h-[48px]"
              >
                Yes, Call Vinto
              </CallVintoButton>

              {/* Cancel button - Gray, bottom */}
              <CancelButton
                onClick={onCancel}
                fullWidth
                className="py-3 px-4 text-base font-bold min-h-[48px]"
              />
            </div>
          </div>
        </div>
      </>
    );
  }
);

VintoConfirmationModal.displayName = 'VintoConfirmationModal';
