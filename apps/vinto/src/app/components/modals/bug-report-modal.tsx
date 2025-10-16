// components/bug-report-modal.tsx
'use client';

import React, { useEffect } from 'react';
import { observer } from 'mobx-react-lite';
import { useBugReportStore } from '../di-provider';
import { useGameClient } from '@/client';

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

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault();
    const debugData = gameClient.exportDebugData();
    void store.submit(debugData);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className="bg-surface-primary border-2 border-primary rounded-lg shadow-theme-lg w-full max-w-md mx-4 p-6">
        {/* Header */}
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-bold text-primary">Report a Bug</h2>
          <button
            onClick={onClose}
            className="text-secondary hover:text-primary transition-colors"
            type="button"
          >
            ✕
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Email */}
          <div>
            <label
              htmlFor="email"
              className="block text-sm font-medium text-primary mb-1"
            >
              Email (optional)
            </label>
            <input
              id="email"
              type="email"
              value={store.email}
              onChange={(e) => {
                store.setEmail(e.target.value);
              }}
              disabled={store.isSubmitting}
              className="w-full px-3 py-2 bg-surface-secondary border border-primary rounded focus:outline-none focus:ring-2 focus:ring-accent-primary text-primary disabled:opacity-50"
              placeholder="your.email@example.com"
            />
          </div>

          {/* Description */}
          <div>
            <label
              htmlFor="description"
              className="block text-sm font-medium text-primary mb-1"
            >
              What went wrong? <span className="text-error">*</span>
            </label>
            <textarea
              id="description"
              value={store.description}
              onChange={(e) => {
                store.setDescription(e.target.value);
              }}
              disabled={store.isSubmitting}
              required
              rows={4}
              className="w-full px-3 py-2 bg-surface-secondary border border-primary rounded focus:outline-none focus:ring-2 focus:ring-accent-primary text-primary resize-none disabled:opacity-50"
              placeholder="Describe the bug you encountered..."
            />
          </div>

          {/* Info */}
          <div className="text-xs text-secondary bg-surface-secondary border border-primary rounded p-3">
            <p className="font-medium text-primary mb-1">
              📋 Debug Information
            </p>
            <p>
              Game state and action history will be automatically attached to
              help us diagnose the issue.
            </p>
          </div>

          {/* Status Messages */}
          {store.showSuccessMessage && (
            <div className="text-sm text-success bg-success/10 border border-success rounded p-3">
              ✓ Bug report submitted successfully! Thank you for your feedback.
            </div>
          )}
          {store.showErrorMessage && (
            <div className="text-sm text-error bg-error/10 border border-error rounded p-3">
              ✗ Failed to submit bug report. Please try again or email us
              directly.
            </div>
          )}

          {/* Buttons */}
          <div className="flex gap-3">
            <button
              type="button"
              onClick={onClose}
              disabled={store.isSubmitting}
              className="flex-1 px-4 py-2 bg-surface-secondary text-secondary border border-primary rounded hover:bg-surface-primary transition-colors disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!store.canSubmit}
              className="flex-1 px-4 py-2 bg-accent-primary text-white rounded hover:bg-accent-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
            >
              {store.isSubmitting ? 'Sending...' : 'Submit Report'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
});
