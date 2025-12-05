import { test, expect } from '@playwright/test';

/**
 * Happy Path E2E Test for Vinto Card Game
 *
 * This test simulates a complete game flow from start to finish:
 * 1. Game initialization and loading
 * 2. Memory phase (peeking at cards)
 * 3. Gameplay turns (drawing and discarding)
 * 4. Calling Vinto to end the game
 * 5. Verifying game completion
 */

test.describe('Vinto Game - Happy Path', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to the game
    await page.goto('/');
  });

  test('should complete a full game from start to calling Vinto', async ({
    page,
  }) => {
    // Increase test timeout to handle multiple turns and bot thinking time
    test.setTimeout(60000); // 60 seconds
    await test.step('Load the game', async () => {
      // Wait for the page to load
      await expect(page).toHaveTitle(/Vinto/i);

      // Look for game initialization elements
      // This may vary based on your actual UI - adjust selectors as needed
      const gameBoard = page
        .locator('[data-testid="middle-area"]')
        .or(page.getByRole('main'));
      await expect(gameBoard).toBeVisible({ timeout: 15000 });
    });

    await test.step('Complete setup phase and start game', async () => {
      // Wait for Memory Phase indicator to appear using test ID
      const setupPhaseIndicator = page.locator('[data-testid="game-phase-setup"]');
      await expect(setupPhaseIndicator).toBeVisible({ timeout: 10000 });

      // Click on two player cards to complete the memory phase
      // The game requires users to peek at 2 cards before starting
      const firstCard = page
        .locator('[data-testid="player-card-0"]')
        .or(page.locator('.player-card').first());
      await expect(firstCard).toBeVisible();
      await firstCard.click();

      // Wait a moment for the first card flip animation
      await page.waitForTimeout(500);

      const secondCard = page
        .locator('[data-testid="player-card-1"]')
        .or(page.locator('.player-card').nth(1));
      await expect(secondCard).toBeVisible();
      await secondCard.click();

      // Wait a moment for the second card flip animation
      await page.waitForTimeout(500);

      // Now the "Start Game" button should be enabled
      const startButton = page
        .getByRole('button', { name: /start game|new game|play/i })
        .or(page.locator('[data-testid="start-game"]'));

      // Wait for button to be enabled after clicking both cards
      await expect(startButton).toBeEnabled({ timeout: 5000 });
      await startButton.click();

      // Verify the game board is visible
      await expect(
        page
          .locator('[data-testid="player-hand"]')
          .or(page.locator('.player-cards'))
      ).toBeVisible({ timeout: 10000 });
    });

    await test.step('Play through several turns', async () => {
      // Play at least 3 player turns (not just any 3 turns)
      let playerTurnsPlayed = 0;
      const maxAttempts = 20; // Reasonable limit to prevent infinite loops
      let attempts = 0;

      while (playerTurnsPlayed < 3 && attempts < maxAttempts) {
        attempts++;

        // Check if it's player's turn by looking for active player indicator
        const activePlayerIndicator = page.locator('[data-testid="active-player-indicator"]');
        const isPlayerTurn = await activePlayerIndicator
          .isVisible({ timeout: 3000 })
          .catch(() => false);

        if (isPlayerTurn) {
          playerTurnsPlayed++;

          // Wait for draw pile to be available
          const drawPile = page.locator('[data-testid="draw-pile"]');

          // Draw a card from the deck
          await expect(drawPile).toBeVisible();
          // Use force: true to bypass actionability checks
          // The parent GameTable container can intercept clicks, but the element is actually clickable
          await drawPile.click({ force: true });

          // Wait for the choosing phase indicator to appear
          // This is more reliable than waiting for individual buttons
          const choosingPhase = page.locator('[data-testid="game-phase-choosing"]');
          await expect(choosingPhase).toBeVisible({ timeout: 15000 });

          // Now get the action buttons
          const useActionButton = page.getByRole('button', {
            name: /use action/i,
          });
          const swapButton = page.getByRole('button', {
            name: /swap cards/i,
          });
          const discardButton = page.getByRole('button', {
            name: /discard/i,
          });

          // Handle the drawn card - either use action, swap, or discard

          const hasUseAction = await useActionButton.count();
          const hasSwap = await swapButton.count();
          const hasDiscard = await discardButton.count();

          if (hasUseAction > 0) {
            await useActionButton.click();

            // If it's a peek action, wait for selectable cards to appear and select one
            const selectableCard = page
              .locator('[data-testid*="card"]')
              .first();
            const cardClickable = await selectableCard.count();
            if (cardClickable > 0) {
              await expect(selectableCard).toBeVisible();
              await selectableCard.click();
            }
          } else if (hasSwap > 0) {
            await swapButton.click();

            // Wait for card selection state to be ready, then select a position to swap
            const cardSlot = page
              .locator('[data-testid="player-card-0"]')
              .first();
            const slotClickable = await cardSlot.count();
            if (slotClickable > 0) {
              await expect(cardSlot).toBeVisible();
              await cardSlot.click();
            }
          } else if (hasDiscard > 0) {
            await discardButton.click();
          }

          // Wait for toss-in phase to appear using test ID
          // After a card is discarded, the toss-in indicator should appear
          const tossInPhase = page.locator('[data-testid="game-phase-toss-in"]');
          const continueButton = page.getByRole('button', {
            name: /continue/i,
          });

          // Wait for either toss-in or next turn (sometimes toss-in is skipped)
          const tossInAppeared = await tossInPhase
            .isVisible({ timeout: 3000 })
            .catch(() => false);

          if (tossInAppeared) {
            // If toss-in phase appeared, click Continue to proceed
            await expect(continueButton).toBeVisible();
            await continueButton.click();
            // Wait a moment for the turn transition after clicking Continue
            await page.waitForTimeout(500);
          }

          // CRITICAL: Ensure we've fully exited our turn before continuing
          // Wait for the active player indicator to disappear (indicating turn has ended)
          await activePlayerIndicator.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {
            // If it doesn't disappear, it might mean we're still in our turn somehow
            // This is okay - we'll check again in the next loop iteration
          });

          // Wait for turn to complete and transition to next player
          await page.waitForTimeout(1000);
        } else {
          // Not player's turn yet, wait for bots to play
          await page.waitForTimeout(1000);
        }
      }

      // CRITICAL FIX: After completing 3 turns, ensure we're not stuck in a pending state
      // Check if there's a Continue button visible (toss-in phase) and click it
      const continueButton = page.getByRole('button', { name: /continue/i });
      const continueVisible = await continueButton.isVisible({ timeout: 1000 }).catch(() => false);
      if (continueVisible) {
        await continueButton.click();
        await page.waitForTimeout(1000);
      }
    });

    await test.step('Call Vinto to end the game', async () => {
      // The "Call Vinto" button appears during the toss-in phase of the player's own turn
      // Strategy: Keep playing turns until we see the Call Vinto button appear during toss-in

      let vintoButtonClicked = false;
      const startTime = Date.now();
      const maxTime = 45000; // 45 seconds max to find and click Call Vinto
      let consecutiveNotPlayerTurnCount = 0;
      const maxConsecutiveNotPlayerTurns = 30; // If we wait 30 seconds without seeing player's turn, something is wrong

      while (!vintoButtonClicked && Date.now() - startTime < maxTime) {
        // First, check if there's any pending action we need to complete
        const continueButton = page.getByRole('button', { name: /continue/i });
        const continueVisible = await continueButton.isVisible({ timeout: 500 }).catch(() => false);
        if (continueVisible) {
          // We're stuck in toss-in from a previous turn - click Continue
          await continueButton.click();
          await page.waitForTimeout(1000);
          consecutiveNotPlayerTurnCount = 0; // Reset since we took an action
          continue; // Go back to the start of the loop
        }

        // Check if it's player's turn using active player indicator
        const activePlayerIndicator = page.locator('[data-testid="active-player-indicator"]');
        const isPlayerTurn = await activePlayerIndicator
          .isVisible({ timeout: 2000 })
          .catch(() => false);

        if (isPlayerTurn) {
          consecutiveNotPlayerTurnCount = 0; // Reset counter

          const drawPile = page.locator('[data-testid="draw-pile"]');

          // Make sure draw pile is actually clickable (not blocked by animations)
          const drawPileVisible = await drawPile.isVisible({ timeout: 5000 }).catch(() => false);
          if (!drawPileVisible) {
            // Draw pile not visible - might be in a different phase
            await page.waitForTimeout(1000);
            continue;
          }

          // Player's turn - play a turn to trigger toss-in phase
          // Use force: true to bypass actionability checks
          await drawPile.click({ force: true });

          // Wait for the choosing phase indicator to appear
          const choosingPhase = page.locator('[data-testid="game-phase-choosing"]');
          const choosingAppeared = await choosingPhase.isVisible({ timeout: 15000 }).catch(() => false);

          if (!choosingAppeared) {
            // Choosing phase didn't appear - might have drawn a non-action card or something else happened
            // Try to recover by waiting and continuing
            await page.waitForTimeout(1000);
            continue;
          }

          // Get action buttons
          const useActionButton = page.getByRole('button', {
            name: /use action/i,
          });
          const swapButton = page.getByRole('button', {
            name: /swap cards/i,
          });
          const discardButton = page.getByRole('button', {
            name: /discard/i,
          });

          // Click discard to trigger toss-in (simplest path)
          const hasDiscard = await discardButton.isVisible().catch(() => false);
          if (hasDiscard) {
            await discardButton.click();
          } else {
            // If no discard button (shouldn't happen with action cards), click use action or swap
            const hasUseAction = await useActionButton
              .isVisible()
              .catch(() => false);
            if (hasUseAction) {
              await useActionButton.click();
              // Wait and click any card that appears (for peek actions)
              await page.waitForTimeout(500);
              const anyCard = page.locator('[data-testid*="card"]').first();
              const cardClickable = await anyCard.count();
              if (cardClickable > 0) {
                await anyCard.click().catch(() => {});
              }
            } else {
              // Must be swap button
              await swapButton.click();
              await page.waitForTimeout(500);
              // Click first player card to complete swap
              const firstPlayerCard = page.locator(
                '[data-testid="player-card-0"]'
              );
              await firstPlayerCard.click().catch(() => {});
            }
          }

          // Now we should be in toss-in phase - check for Call Vinto button using test ID
          const tossInPhase = page.locator('[data-testid="game-phase-toss-in"]');
          const tossInAppeared = await tossInPhase
            .isVisible({ timeout: 5000 })
            .catch(() => false);

          if (tossInAppeared) {
            // We're in toss-in phase - check if Call Vinto button exists
            // (it only appears on player's original turn, not when participating in others' toss-in)
            const vintoButton = page.getByRole('button', {
              name: /call vinto/i,
            });
            const vintoVisible = await vintoButton
              .isVisible({ timeout: 1000 })
              .catch(() => false);

            if (vintoVisible) {
              // Found it! Click the Call Vinto button
              await vintoButton.click();
              vintoButtonClicked = true;

              // Wait for confirmation modal to appear
              await page.waitForTimeout(1000);
              break; // Exit the loop
            } else {
              // No Call Vinto button yet, click Continue to proceed to next turn
              const continueBtn = page.getByRole('button', {
                name: /continue/i,
              });
              const continueVis = await continueBtn
                .isVisible({ timeout: 2000 })
                .catch(() => false);

              if (continueVis) {
                await continueBtn.click();
                // Wait for turn transition and active indicator to disappear
                await activePlayerIndicator.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
                await page.waitForTimeout(1000);
              }
            }
          } else {
            // Toss-in didn't appear - might have been auto-skipped or we need to wait longer
            await page.waitForTimeout(1000);
          }
        } else {
          // Not player's turn - wait for bots to play
          consecutiveNotPlayerTurnCount++;

          if (consecutiveNotPlayerTurnCount >= maxConsecutiveNotPlayerTurns) {
            // We've been waiting too long without seeing the player's turn
            // The game might be stuck - take a screenshot for debugging
            await page.screenshot({
              path: test.info().outputPath('stuck-waiting-for-player-turn.png'),
              fullPage: true,
            });
            throw new Error(
              `Game appears stuck - waited ${maxConsecutiveNotPlayerTurns} seconds without seeing player's turn. This suggests a pending action or blocked state.`
            );
          }

          // Just wait a bit and check again
          await page.waitForTimeout(1000);
        }
      }

      // Verify we successfully clicked the button
      if (!vintoButtonClicked) {
        // Take a screenshot to help debug why we couldn't find the button
        await page.screenshot({
          path: test.info().outputPath('failed-to-find-vinto-button.png'),
          fullPage: true,
        });
        throw new Error(
          `Failed to find and click Call Vinto button within ${maxTime / 1000}s`
        );
      }
    });

    await test.step('Verify game completion', async () => {
      // Look for game over / final round indicators
      const gameEnd = page
        .getByText(/final round|game over|winner|vinto!/i)
        .or(page.locator('[data-testid="game-end"]'));

      await expect(gameEnd).toBeVisible({ timeout: 20000 });

      // Look for score display
      const scoreDisplay = page
        .getByText(/score|points|total/i)
        .or(page.locator('[data-testid="score"]'));

      const hasScore = await scoreDisplay.count();
      if (hasScore > 0) {
        await expect(scoreDisplay.first()).toBeVisible();
      }

      // Take a screenshot of the final state using test.info() for proper path handling
      await page.screenshot({
        path: test.info().outputPath('game-complete.png'),
        fullPage: true,
      });
    });
  });

  test('should load the game homepage successfully', async ({ page }) => {
    await test.step('Verify page loads', async () => {
      // Check that the page loaded
      await expect(page).toHaveTitle(/Vinto/i);

      // Check for key UI elements
      const mainContent = page
        .getByRole('main')
        .or(page.locator('[data-testid="middle-area"]'));
      await expect(mainContent).toBeVisible();
    });
  });

  test('should display game interface elements', async ({ page }) => {
    await test.step('Check for game UI elements', async () => {
      // This is a smoke test to verify core UI elements exist
      // Use web-first assertion to verify page has content
      await expect(page.locator('body')).not.toBeEmpty();
    });
  });
});
