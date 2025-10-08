// components/error-boundary.tsx
'use client';

import React from 'react';
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary';
import * as Sentry from '@sentry/react';
import { AlertCircle, RefreshCw } from 'lucide-react';
import { TryAgainButton } from './buttons';

interface ErrorFallbackProps {
  error: Error;
  resetErrorBoundary: () => void;
}

function ErrorFallback({ error, resetErrorBoundary }: ErrorFallbackProps) {
  return (
    <div className="min-h-screen bg-page-gradient flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-surface-primary rounded-lg shadow-lg p-6 space-y-4">
        <div className="flex items-center gap-3 text-error">
          <AlertCircle className="w-8 h-8 flex-shrink-0" />
          <h1 className="text-xl font-semibold">Something went wrong</h1>
        </div>

        <div className="bg-error-light border border-error rounded p-3">
          <p className="text-sm text-error-dark font-mono break-words">
            {error.message || 'An unexpected error occurred'}
          </p>
        </div>

        <div className="text-sm text-secondary">
          <p>
            We&apos;ve been notified and are looking into it. Please try
            refreshing the page.
          </p>
        </div>

        <TryAgainButton onClick={resetErrorBoundary}>
          <RefreshCw className="w-4 h-4" />
          Try Again
        </TryAgainButton>

        {process.env.NODE_ENV === 'development' && error.stack && (
          <details className="mt-4">
            <summary className="cursor-pointer text-sm text-secondary hover:text-primary">
              View stack trace
            </summary>
            <pre className="mt-2 text-xs bg-surface-tertiary text-on-surface p-3 rounded overflow-x-auto">
              {error.stack}
            </pre>
          </details>
        )}
      </div>
    </div>
  );
}

interface ErrorBoundaryProps {
  children: React.ReactNode;
}

export function ErrorBoundary({ children }: ErrorBoundaryProps) {
  const handleError = (error: Error, errorInfo: React.ErrorInfo) => {
    // Log to Sentry with additional context
    Sentry.captureException(error, {
      contexts: {
        react: {
          componentStack: errorInfo.componentStack,
        },
      },
    });
  };

  const handleReset = () => {
    // Optionally clear any error state or reload the page
    window.location.reload();
  };

  return (
    <ReactErrorBoundary
      FallbackComponent={ErrorFallback}
      onError={handleError}
      onReset={handleReset}
    >
      {children}
    </ReactErrorBoundary>
  );
}
