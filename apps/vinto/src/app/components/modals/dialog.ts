// dialog.ts - Dialog utility with custom events and proper animation handling
// Based on web.dev best practices: https://web.dev/articles/building/a-dialog-component

// Check if we're in a browser environment
const isBrowser = typeof window !== 'undefined';

// Custom events to be added to <dialog>
const dialogClosingEvent = isBrowser ? new Event('closing') : null;
const dialogClosedEvent = isBrowser ? new Event('closed') : null;
const dialogOpeningEvent = isBrowser ? new Event('opening') : null;
const dialogOpenedEvent = isBrowser ? new Event('opened') : null;
const dialogRemovedEvent = isBrowser ? new Event('removed') : null;

// Track opening
const dialogAttrObserver = isBrowser
  ? new MutationObserver((mutations) => {
      // eslint-disable-next-line @typescript-eslint/no-misused-promises
      mutations.forEach(async (mutation) => {
        if (mutation.attributeName === 'open') {
          const dialog = mutation.target as HTMLDialogElement;

          const isOpen = dialog.hasAttribute('open');
          if (!isOpen) return;

          dialog.removeAttribute('inert');

          // Set focus
          const focusTarget = dialog.querySelector(
            '[autofocus]'
          ) as HTMLElement;
          focusTarget
            ? focusTarget.focus()
            : (dialog.querySelector('button') as HTMLElement)?.focus();

          if (dialogOpeningEvent) dialog.dispatchEvent(dialogOpeningEvent);
          await animationsComplete(dialog);
          if (dialogOpenedEvent) dialog.dispatchEvent(dialogOpenedEvent);
        }
      });
    })
  : null;

// Track deletion
const dialogDeleteObserver = isBrowser
  ? new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        mutation.removedNodes.forEach((removedNode) => {
          if (removedNode.nodeName === 'DIALOG') {
            const dialog = removedNode as HTMLDialogElement;
            // eslint-disable-next-line @typescript-eslint/no-misused-promises
            dialog.removeEventListener('close', dialogClose);
            if (dialogRemovedEvent) dialog.dispatchEvent(dialogRemovedEvent);
          }
        });
      });
    })
  : null;

// Wait for all dialog animations to complete their promises
const animationsComplete = (element: HTMLElement) =>
  Promise.allSettled(
    element.getAnimations().map((animation) => animation.finished)
  );

const dialogClose = async (event: Event) => {
  const dialog = event.target as HTMLDialogElement;
  dialog.setAttribute('inert', '');
  if (dialogClosingEvent) dialog.dispatchEvent(dialogClosingEvent);

  await animationsComplete(dialog);

  if (dialogClosedEvent) dialog.dispatchEvent(dialogClosedEvent);
};

// Page load dialogs setup
export async function initDialog(dialog: HTMLDialogElement) {
  if (!isBrowser) return;

  // eslint-disable-next-line @typescript-eslint/no-misused-promises
  dialog.addEventListener('close', dialogClose);

  if (dialogAttrObserver) {
    dialogAttrObserver.observe(dialog, {
      attributes: true,
    });
  }

  if (dialogDeleteObserver) {
    dialogDeleteObserver.observe(document.body, {
      attributes: false,
      subtree: false,
      childList: true,
    });
  }

  // Remove loading attribute to prevent page load @keyframes playing
  await animationsComplete(dialog);
  dialog.removeAttribute('loading');
}
