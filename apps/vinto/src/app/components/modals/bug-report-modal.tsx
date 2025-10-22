// components/bug-report-modal.tsx
'use client';

import React, { useEffect } from 'react';
import { observer } from 'mobx-react-lite';
import { useBugReportStore } from '../di-provider';
import { useGameClient } from '@vinto/local-client';

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

  // Reset store when modal closes
  useEffect(() => {
    if (!isOpen) {
      store.reset();
    }
  }, [isOpen, store]);

  useEffect(() => {
    store.registerGameClient(gameClient);
  }, [gameClient, store]);
  // Auto-close on success
  useEffect(() => {
    if (store.showSuccessMessage) {
      const timer = setTimeout(() => {
        onClose();
      }, 2000);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [store.showSuccessMessage, onClose]);

  const dialogRef = React.useRef<HTMLDialogElement>(null);

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

  // Light dismiss (click backdrop)
  useEffect(() => {
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
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const handleClose = () => {
      onClose();
    };
    dialog.addEventListener('close', handleClose);
    return () => dialog.removeEventListener('close', handleClose);
  }, [onClose]);

  // Form submit handler
  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const debugData = gameClient.exportDebugData();
    void store.submit(debugData);
  };

  return (
    <dialog
      ref={dialogRef}
      id="MegaDialog"
      modal-mode="mega"
      className="z-50 bg-surface-primary border-primary border rounded-xl shadow-theme-lg max-w-md w-full animate-fade-in"
    >
      <form
        method="dialog"
        onSubmit={handleSubmit}
        className="grid grid-rows-[auto_1fr_auto] max-h-[80vh]"
      >
        <header className="flex justify-between items-center mb-0 bg-surface-secondary px-6 py-4 rounded-t-xl">
          <h2 className="text-2xl font-bold text-primary tracking-tight">
            Report a Bug
          </h2>
          <button
            type="button"
            onClick={() => dialogRef.current?.close('close')}
            className="text-muted-foreground hover:text-accent transition-colors rounded-full focus:outline-none focus:ring-2 focus:ring-accent p-2 aspect-square"
            aria-label="Close bug report dialog"
          >
            <span aria-hidden="true">✕</span>
          </button>
        </header>
        <article className="overflow-y-auto max-h-full px-6 py-4 bg-surface-primary flex flex-col gap-5">
          {/* Email */}
          <div>
            <label
              htmlFor="email"
              className="block text-sm font-medium text-primary mb-2"
            >
              Email (optional)
            </label>
            <input
              id="email"
              type="email"
              value={store.email}
              onChange={(e) => store.setEmail(e.target.value)}
              disabled={store.isSubmitting}
              className="w-full px-3 py-2 bg-surface-secondary border border-primary rounded-lg focus:outline-none focus:ring-2 focus:ring-accent text-primary placeholder:text-muted-foreground disabled:opacity-50 transition-shadow shadow-theme-sm"
              placeholder="your.email@example.com"
              autoComplete="email"
            />
          </div>
          {/* Description */}
          <div>
            <label
              htmlFor="description"
              className="block text-sm font-medium text-primary mb-2"
            >
              What went wrong?{' '}
              <span className="text-error" aria-label="required">
                *
              </span>
            </label>
            <textarea
              id="description"
              autoFocus
              value={store.description}
              onChange={(e) => store.setDescription(e.target.value)}
              disabled={store.isSubmitting}
              required
              rows={4}
              className="w-full px-3 py-2 bg-surface-secondary border border-primary rounded-lg focus:outline-none focus:ring-2 focus:ring-accent text-primary resize-none placeholder:text-muted-foreground disabled:opacity-50 transition-shadow shadow-theme-sm"
              placeholder="Describe the bug you encountered..."
            />
          </div>
          {/* Info */}
          <div className="text-xs text-muted bg-muted border border-primary rounded-lg p-3">
            <p className="font-semibold text-primary mb-1">
              📋 Debug Information (
              {process.env.NEXT_PUBLIC_VERCEL_GIT_REPO_ID ?? 'unknown'})
            </p>
            <p className="text-muted-foreground">
              Game state and action history will be automatically attached to
              help us diagnose the issue.
            </p>
          </div>
          {/* Status Messages */}
          {store.showSuccessMessage && (
            <div className="text-sm text-success bg-success-light border border-success rounded-lg p-3 flex items-center gap-2 animate-gentle-pulse">
              <span aria-hidden="true">✓</span> Bug report submitted
              successfully! Thank you for your feedback.
            </div>
          )}
          {store.showErrorMessage && (
            <div className="text-sm text-error bg-error-light border border-error rounded-lg p-3 flex items-center gap-2 animate-gentle-pulse">
              <span aria-hidden="true">✗</span> Failed to submit bug report.
              Please try again or email us directly.
            </div>
          )}
        </article>
        <footer className="bg-surface-secondary flex flex-wrap gap-3 justify-between items-center px-6 py-4 rounded-b-xl">
          <menu className="flex flex-wrap gap-3 p-0 m-0 w-full">
            <button
              type="reset"
              autoFocus
              onClick={() => dialogRef.current?.close('cancel')}
              disabled={store.isSubmitting}
              className="flex-1 px-4 py-2 bg-muted text-muted-foreground border border-primary rounded-lg hover:bg-surface-secondary transition-colors disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-accent"
            >
              Cancel
            </button>
            <button
              type="submit"
              value="confirm"
              disabled={!store.canSubmit}
              className="flex-1 px-4 py-2 bg-accent text-on-primary rounded-lg hover:bg-accent-dark transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-semibold focus:outline-none focus:ring-2 focus:ring-accent"
            >
              {store.isSubmitting ? 'Sending...' : 'Submit Report'}
            </button>
          </menu>
        </footer>
      </form>
    </dialog>
  );
});
