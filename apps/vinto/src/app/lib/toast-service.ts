// lib/toast-service.ts
import toast from 'react-hot-toast';
import { CheckCircle, AlertCircle, XCircle, Info, Loader2 } from 'lucide-react';
import React from 'react';

export class GameToastService {
  static success(message: string, duration = 4000) {
    return toast.custom(
      (t) =>
        React.createElement(
          'div',
          {
            className: `${
              t.visible ? 'animate-enter' : 'animate-leave'
            } max-w-md w-full bg-white shadow-lg rounded-xl pointer-events-auto flex ring-1 ring-black ring-opacity-5 border-l-4 border-green-500`,
          },
          [
            React.createElement(
              'div',
              {
                key: 'content',
                className: 'flex-1 w-0 p-4',
              },
              [
                React.createElement(
                  'div',
                  {
                    key: 'inner',
                    className: 'flex items-start',
                  },
                  [
                    React.createElement(
                      'div',
                      {
                        key: 'icon',
                        className: 'flex-shrink-0',
                      },
                      [
                        React.createElement(CheckCircle, {
                          key: 'check',
                          className: 'h-6 w-6 text-green-500',
                        }),
                      ]
                    ),
                    React.createElement(
                      'div',
                      {
                        key: 'text',
                        className: 'ml-3 flex-1',
                      },
                      [
                        React.createElement(
                          'p',
                          {
                            key: 'msg',
                            className: 'text-sm font-medium text-gray-900',
                          },
                          message
                        ),
                      ]
                    ),
                  ]
                ),
              ]
            ),
            React.createElement(
              'div',
              {
                key: 'dismiss',
                className: 'flex border-l border-gray-200',
              },
              [
                React.createElement(
                  'button',
                  {
                    key: 'btn',
                    onClick: () => toast.dismiss(t.id),
                    className:
                      'w-full border border-transparent rounded-none rounded-r-xl p-4 flex items-center justify-center text-sm font-medium text-green-600 hover:text-green-500',
                  },
                  '✕'
                ),
              ]
            ),
          ]
        ),
      { duration, position: 'top-center' }
    );
  }

  static error(message: string, details?: string, duration = 6000) {
    return toast.custom(
      (t) =>
        React.createElement(
          'div',
          {
            className: `${
              t.visible ? 'animate-enter' : 'animate-leave'
            } max-w-md w-full bg-white shadow-lg rounded-xl pointer-events-auto flex ring-1 ring-black ring-opacity-5 border-l-4 border-red-500`,
          },
          [
            React.createElement(
              'div',
              {
                key: 'content',
                className: 'flex-1 w-0 p-4',
              },
              [
                React.createElement(
                  'div',
                  {
                    key: 'inner',
                    className: 'flex items-start',
                  },
                  [
                    React.createElement(
                      'div',
                      {
                        key: 'icon',
                        className: 'flex-shrink-0',
                      },
                      [
                        React.createElement(XCircle, {
                          key: 'x',
                          className: 'h-6 w-6 text-red-500',
                        }),
                      ]
                    ),
                    React.createElement(
                      'div',
                      {
                        key: 'text',
                        className: 'ml-3 flex-1',
                      },
                      [
                        React.createElement(
                          'p',
                          {
                            key: 'msg',
                            className: 'text-sm font-medium text-gray-900',
                          },
                          message
                        ),
                        details
                          ? React.createElement(
                              'p',
                              {
                                key: 'details',
                                className: 'mt-1 text-sm text-gray-500',
                              },
                              details
                            )
                          : null,
                      ]
                    ),
                  ]
                ),
              ]
            ),
            React.createElement(
              'div',
              {
                key: 'dismiss',
                className: 'flex border-l border-gray-200',
              },
              [
                React.createElement(
                  'button',
                  {
                    key: 'btn',
                    onClick: () => toast.dismiss(t.id),
                    className:
                      'w-full border border-transparent rounded-none rounded-r-xl p-4 flex items-center justify-center text-sm font-medium text-red-600 hover:text-red-500',
                  },
                  '✕'
                ),
              ]
            ),
          ]
        ),
      { duration, position: 'top-center' }
    );
  }

  static warning(message: string, duration = 5000) {
    return toast.custom(
      (t) =>
        React.createElement(
          'div',
          {
            className: `${
              t.visible ? 'animate-enter' : 'animate-leave'
            } max-w-md w-full bg-white shadow-lg rounded-xl pointer-events-auto flex ring-1 ring-black ring-opacity-5 border-l-4 border-yellow-500`,
          },
          [
            React.createElement(
              'div',
              {
                key: 'content',
                className: 'flex-1 w-0 p-4',
              },
              [
                React.createElement(
                  'div',
                  {
                    key: 'inner',
                    className: 'flex items-start',
                  },
                  [
                    React.createElement(
                      'div',
                      {
                        key: 'icon',
                        className: 'flex-shrink-0',
                      },
                      [
                        React.createElement(AlertCircle, {
                          key: 'alert',
                          className: 'h-6 w-6 text-yellow-500',
                        }),
                      ]
                    ),
                    React.createElement(
                      'div',
                      {
                        key: 'text',
                        className: 'ml-3 flex-1',
                      },
                      [
                        React.createElement(
                          'p',
                          {
                            key: 'msg',
                            className: 'text-sm font-medium text-gray-900',
                          },
                          message
                        ),
                      ]
                    ),
                  ]
                ),
              ]
            ),
          ]
        ),
      { duration, position: 'top-center' }
    );
  }

  static info(message: string, duration = 4000) {
    return toast.custom(
      (t) =>
        React.createElement(
          'div',
          {
            className: `${
              t.visible ? 'animate-enter' : 'animate-leave'
            } max-w-md w-full bg-white shadow-lg rounded-xl pointer-events-auto flex ring-1 ring-black ring-opacity-5 border-l-4 border-blue-500`,
          },
          [
            React.createElement(
              'div',
              {
                key: 'content',
                className: 'flex-1 w-0 p-4',
              },
              [
                React.createElement(
                  'div',
                  {
                    key: 'inner',
                    className: 'flex items-start',
                  },
                  [
                    React.createElement(
                      'div',
                      {
                        key: 'icon',
                        className: 'flex-shrink-0',
                      },
                      [
                        React.createElement(Info, {
                          key: 'info',
                          className: 'h-6 w-6 text-blue-500',
                        }),
                      ]
                    ),
                    React.createElement(
                      'div',
                      {
                        key: 'text',
                        className: 'ml-3 flex-1',
                      },
                      [
                        React.createElement(
                          'p',
                          {
                            key: 'msg',
                            className: 'text-sm font-medium text-gray-900',
                          },
                          message
                        ),
                      ]
                    ),
                  ]
                ),
              ]
            ),
          ]
        ),
      { duration, position: 'top-center' }
    );
  }

  static loading(message: string) {
    return toast.custom(
      (t) =>
        React.createElement(
          'div',
          {
            className: `${
              t.visible ? 'animate-enter' : 'animate-leave'
            } max-w-md w-full bg-white shadow-lg rounded-xl pointer-events-auto flex ring-1 ring-black ring-opacity-5`,
          },
          [
            React.createElement(
              'div',
              {
                key: 'content',
                className: 'flex-1 w-0 p-4',
              },
              [
                React.createElement(
                  'div',
                  {
                    key: 'inner',
                    className: 'flex items-start',
                  },
                  [
                    React.createElement(
                      'div',
                      {
                        key: 'icon',
                        className: 'flex-shrink-0',
                      },
                      [
                        React.createElement(Loader2, {
                          key: 'loader',
                          className: 'h-6 w-6 text-blue-500 animate-spin',
                        }),
                      ]
                    ),
                    React.createElement(
                      'div',
                      {
                        key: 'text',
                        className: 'ml-3 flex-1',
                      },
                      [
                        React.createElement(
                          'p',
                          {
                            key: 'msg',
                            className: 'text-sm font-medium text-gray-900',
                          },
                          message
                        ),
                      ]
                    ),
                  ]
                ),
              ]
            ),
          ]
        ),
      { duration: Infinity, position: 'top-center' }
    );
  }

  static aiThinking(playerName: string) {
    return this.loading(`🧠 ${playerName} analyzing...`);
  }

  static aiMove(playerName: string, move: string, confidence: number) {
    this.info(
      `${playerName}: ${move.toUpperCase()} (${confidence}% confident)`,
      4000
    );
  }

  static cloudError(error: string) {
    this.error('Cloud Error', error, 6000);
  }

  static cardPeeked(position: number) {
    this.info(`👁️ Peeked at card position ${position + 1}`, 2000);
  }

  static difficultyChanged(difficulty: string) {
    this.info(`🎯 Difficulty changed to ${difficulty.toUpperCase()}`, 2000);
  }
}
