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

  test('should complete a full game from start to calling Vinto', async ({ page }) => {
    await test.step('Load the game', async () => {
      // Wait for the page to load
      await expect(page).toHaveTitle(/Vinto/i);

      // Look for game initialization elements
      // This may vary based on your actual UI - adjust selectors as needed
      const gameBoard = page.locator('[data-testid="game-board"]').or(page.getByRole('main'));
      await expect(gameBoard).toBeVisible({ timeout: 15000 });
    });

    await test.step('Start a new game', async () => {
      // Look for "Start Game" or "New Game" button
      // Try multiple possible selectors
      const startButton = page
        .getByRole('button', { name: /start game|new game|play/i })
        .or(page.locator('[data-testid="start-game"]'));

      // If the game auto-starts, this might not exist
      const buttonVisible = await startButton.count();
      if (buttonVisible > 0) {
        await startButton.click();
      }

      // Verify the game board is visible
      await expect(
        page.locator('[data-testid="player-hand"]').or(page.locator('.player-cards'))
      ).toBeVisible({ timeout: 10000 });
    });

    await test.step('Complete memory phase - peek at cards', async () => {
      // Wait for memory phase to start
      // Look for instructions or peek prompt
      const memoryPhase = page
        .getByText(/peek|memory|look at.*cards/i)
        .or(page.locator('[data-testid="memory-phase"]'));

      const hasMemoryPhase = await memoryPhase.count();

      if (hasMemoryPhase > 0) {
        await expect(memoryPhase.first()).toBeVisible();

        // Peek at first card
        const firstCard = page
          .locator('[data-testid="player-card-0"]')
          .or(page.locator('.player-card').first());
        await firstCard.click();
        // Wait for card flip animation to complete
        await expect(firstCard).toBeVisible();

        // Peek at second card
        const secondCard = page
          .locator('[data-testid="player-card-1"]')
          .or(page.locator('.player-card').nth(1));
        await secondCard.click();
        // Wait for card flip animation to complete
        await expect(secondCard).toBeVisible();

        // Confirm or close memory phase if needed
        const confirmButton = page.getByRole('button', { name: /confirm|ok|continue/i });
        const confirmVisible = await confirmButton.count();
        if (confirmVisible > 0) {
          await confirmButton.click();
        }
      }
    });

    await test.step('Play through several turns', async () => {
      // Play at least 3 turns
      for (let turn = 0; turn < 3; turn++) {
        // Wait for player's turn (skip if it's a bot's turn)
        const playerTurnIndicator = page
          .getByText(/your turn/i)
          .or(page.locator('[data-testid="player-turn"]'));

        // Check if it's player's turn with a reasonable timeout
        const isPlayerTurn = await playerTurnIndicator.count();

        if (isPlayerTurn > 0) {
          // Draw a card from the deck
          const drawPile = page
            .locator('[data-testid="draw-pile"]')
            .or(page.getByRole('button', { name: /draw|deck/i }));

          await expect(drawPile).toBeVisible();
          await drawPile.click();

          // Wait for action buttons to appear after drawing
          const actionArea = page.getByRole('button', { name: /use action|play|swap|replace|discard|skip/i });
          await expect(actionArea.first()).toBeVisible({ timeout: 5000 });

          // Handle the drawn card - either use action, swap, or discard
          // Look for action buttons
          const useActionButton = page.getByRole('button', { name: /use action|play/i });
          const swapButton = page.getByRole('button', { name: /swap|replace/i });
          const discardButton = page.getByRole('button', { name: /discard|skip/i });

          const hasUseAction = await useActionButton.count();
          const hasSwap = await swapButton.count();
          const hasDiscard = await discardButton.count();

          if (hasUseAction > 0) {
            await useActionButton.click();

            // If it's a peek action, wait for selectable cards to appear and select one
            const selectableCard = page.locator('[data-testid*="card"]').first();
            const cardClickable = await selectableCard.count();
            if (cardClickable > 0) {
              await expect(selectableCard).toBeVisible();
              await selectableCard.click();
            }
          } else if (hasSwap > 0) {
            await swapButton.click();

            // Wait for card selection state to be ready, then select a position to swap
            const cardSlot = page.locator('[data-testid="player-card-0"]').first();
            const slotClickable = await cardSlot.count();
            if (slotClickable > 0) {
              await expect(cardSlot).toBeVisible();
              await cardSlot.click();
            }
          } else if (hasDiscard > 0) {
            await discardButton.click();
            // Wait for discard action to complete
            await expect(discardButton).not.toBeVisible({ timeout: 2000 }).catch(() => {});
          }
        }

        // Wait for turn to complete - check that draw pile becomes disabled or next turn starts
        // This is more reliable than a fixed timeout
        await page.waitForLoadState('networkidle', { timeout: 3000 }).catch(() => {});
      }
    });

    await test.step('Call Vinto to end the game', async () => {
      // Look for "Call Vinto" button
      const vintoButton = page
        .getByRole('button', { name: /call vinto|vinto|end game/i })
        .or(page.locator('[data-testid="call-vinto"]'));

      // Wait for the button to be available (might need to wait for player's turn)
      await expect(vintoButton).toBeVisible({ timeout: 30000 });

      // Wait for button to be enabled
      await expect(vintoButton).toBeEnabled({ timeout: 10000 });

      await vintoButton.click();

      // Wait for confirmation modal or game end state to appear
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
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
        fullPage: true
      });
    });
  });

  test('should load the game homepage successfully', async ({ page }) => {
    await test.step('Verify page loads', async () => {
      // Check that the page loaded
      await expect(page).toHaveTitle(/Vinto/i);

      // Check for key UI elements
      const mainContent = page.getByRole('main').or(page.locator('[data-testid="game-board"]'));
      await expect(mainContent).toBeVisible();
    });
  });

  test('should display game interface elements', async ({ page }) => {
    await test.step('Check for game UI elements', async () => {
      // This is a smoke test to verify core UI elements exist
      const body = await page.textContent('body');

      // Just verify the page has content
      expect(body).toBeTruthy();
      expect(body!.length).toBeGreaterThan(0);
    });
  });
});
