// shared/logger.ts
// Logger utility that reports warnings to Sentry

import * as Sentry from '@sentry/nextjs';

/**
 * Logger utility with Sentry integration
 *
 * Use this instead of console.warn/error to automatically report issues to Sentry
 * while still logging to console in development.
 */
export const logger = {
  /**
   * Log an informational message (console only, not sent to Sentry)
   */
  log: (message: string, data?: Record<string, unknown>): void => {
    if (data) {
      console.log(message, data);
    } else {
      console.log(message);
    }
  },

  /**
   * Log a warning and report to Sentry
   * Use for unexpected states that don't break functionality but indicate potential issues
   */
  warn: (message: string, data?: Record<string, unknown>): void => {
    // Log to console
    if (data) {
      console.warn(message, data);
    } else {
      console.warn(message);
    }

    // Report to Sentry as a warning-level message
    Sentry.captureMessage(message, {
      level: 'warning',
      extra: data,
    });
  },

  /**
   * Log an error and report to Sentry
   * Use for errors that impact functionality
   */
  error: (
    message: string,
    error?: unknown,
    data?: Record<string, unknown>
  ): void => {
    // Log to console
    if (error && data) {
      console.error(message, error, data);
    } else if (error) {
      console.error(message, error);
    } else if (data) {
      console.error(message, data);
    } else {
      console.error(message);
    }

    // Report to Sentry
    if (error instanceof Error) {
      Sentry.captureException(error, {
        extra: {
          message,
          ...data,
        },
      });
    } else {
      Sentry.captureMessage(message, {
        level: 'error',
        extra: {
          error,
          ...data,
        },
      });
    }
  },

  /**
   * Set additional context for Sentry reports
   */
  setContext: (name: string, context: Record<string, unknown>): void => {
    Sentry.setContext(name, context);
  },

  /**
   * Set user context for Sentry reports
   */
  setUser: (
    user: { id?: string; username?: string; email?: string } | null
  ): void => {
    Sentry.setUser(user);
  },

  /**
   * Add breadcrumb for debugging
   */
  addBreadcrumb: (
    message: string,
    category: string,
    data?: Record<string, unknown>
  ): void => {
    Sentry.addBreadcrumb({
      message,
      category,
      data,
      level: 'info',
    });
  },
};
