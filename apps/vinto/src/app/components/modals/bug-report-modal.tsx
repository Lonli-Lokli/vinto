// components/bug-report-modal.tsx
'use client';

import React, { useEffect, useRef } from 'react';
import { observer } from 'mobx-react-lite';
import { useBugReportStore } from '../di-provider';
import { useGameClient } from '@vinto/local-client';
import { initDialog } from './dialog';

interface BugReportModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const BugReportModal = observer(function BugReportModal({
  isOpen,
  onClose,
}: BugReportModalProps) {
  const store = useBugReportStore();
  const gameClient = useGameClient();
  const dialogRef = useRef<HTMLDialogElement>(null);

  // Initialize dialog with custom events and utilities
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    void initDialog(dialog);

    // Listen to custom events
    const handleClosed = (e: Event) => {
      const dialog = e.target as HTMLDialogElement;
      onClose();

      // Handle form submission based on return value
      if (dialog.returnValue === 'confirm') {
        console.info('Bug report confirmed');
      } else {
        // Reset form on cancel/dismiss
        dialog.querySelector('form')?.reset();
        store.reset();
      }
    };

    const handleOpened = () => {
      console.info('Dialog opened');
    };

    dialog.addEventListener('closed', handleClosed);
    dialog.addEventListener('opened', handleOpened);

    return () => {
      dialog.removeEventListener('closed', handleClosed);
      dialog.removeEventListener('opened', handleOpened);
    };
  }, [onClose, store]);

  // Register game client
  useEffect(() => {
    store.registerGameClient(gameClient);
  }, [gameClient, store]);

  // Open/close dialog imperatively
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (isOpen && !dialog.open) {
      dialog.showModal();
    } else if (!isOpen && dialog.open) {
      dialog.close('close');
    }
  }, [isOpen]);

  // Auto-close on success
  useEffect(() => {
    if (store.showSuccessMessage) {
      const timer = setTimeout(() => {
        dialogRef.current?.close('success');
      }, 2000);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [store.showSuccessMessage]);

  // Form submit handler
  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const debugData = gameClient.exportDebugData();
    void store.submit(debugData);
  };

  return (
    <dialog
      ref={dialogRef}
      id="BugReportDialog"
      {...({ 'modal-mode': 'mega' } as React.HTMLAttributes<HTMLDialogElement>)}
      className="dialog-mega"
      {...({ loading: 'true' } as React.HTMLAttributes<HTMLDialogElement>)}
    >
      <form method="dialog" onSubmit={handleSubmit}>
        <header>
          <h3>Report a Bug</h3>
          <button
            type="button"
            onClick={() => dialogRef.current?.close('close')}
            className="dialog-close-btn"
            aria-label="Close bug report dialog"
          >
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <article>
          {/* Email */}
          <div className="form-field">
            <label htmlFor="email">Email (optional)</label>
            <input
              id="email"
              type="email"
              value={store.email}
              onChange={(e) => store.setEmail(e.target.value)}
              disabled={store.isSubmitting}
              placeholder="your.email@example.com"
              autoComplete="email"
            />
          </div>

          {/* Description */}
          <div className="form-field">
            <label htmlFor="description">
              What went wrong? <span className="required">*</span>
            </label>
            <textarea
              id="description"
              value={store.description}
              onChange={(e) => store.setDescription(e.target.value)}
              disabled={store.isSubmitting}
              required
              rows={4}
              placeholder="Describe the bug you encountered..."
            />
          </div>

          {/* Info */}
          <div className="dialog-info">
            <p className="info-title">
              📋 Debug Information (
              {process.env.NEXT_PUBLIC_VERCEL_GIT_REPO_ID ?? 'unknown'})
            </p>
            <p className="info-description">
              Game state and action history will be automatically attached to
              help us diagnose the issue.
            </p>
          </div>

          {/* Status Messages */}
          {store.showSuccessMessage && (
            <div className="dialog-message success">
              <span aria-hidden="true">✓</span> Bug report submitted
              successfully! Thank you for your feedback.
            </div>
          )}
          {store.showErrorMessage && (
            <div className="dialog-message error">
              <span aria-hidden="true">✗</span> Failed to submit bug report.
              Please try again or email us directly.
            </div>
          )}
        </article>

        <footer>
          <menu>
            <button
              type="reset"
              autoFocus
              onClick={() => dialogRef.current?.close('cancel')}
              disabled={store.isSubmitting}
            >
              Cancel
            </button>
            <button type="submit" value="confirm" disabled={!store.canSubmit}>
              {store.isSubmitting ? 'Sending...' : 'Submit Report'}
            </button>
          </menu>
        </footer>
      </form>
    </dialog>
  );
});
