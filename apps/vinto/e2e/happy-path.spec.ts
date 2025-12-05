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
    isMobile,
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
      const setupPhaseIndicator = page.locator(
        '[data-testid="game-phase-setup"]'
      );
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
      const startButton = page.locator('[data-testid="start-game"]');

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

    await test.step('Play one turn and call Vinto', async () => {
      // Determine if we're on mobile or desktop based on viewport
      const testIdSuffix = isMobile ? '-mobile' : '-desktop';

      // Wait for active player indicator (works for both player and bot turns)
      // In a 4-player game, the first turn might be a bot
      const activePlayerIndicator = page.locator(
        `[data-testid="active-player-indicator${testIdSuffix}"]`
      );

      // Wait up to 30 seconds for it to be the player's turn
      // This handles the case where bots go first
      await expect(activePlayerIndicator).toBeVisible({ timeout: 30000 });

      // Now it's the player's turn - wait for draw pile to be clickable
      const drawPile = page.locator('[data-testid="draw-pile"]');
      await expect(drawPile).toBeVisible();

      // Click draw pile
      await drawPile.click();

      // Wait for choosing phase indicator - this means the card has been drawn
      const choosingPhase = page.locator('[data-testid="game-phase-choosing"]');
      await expect(choosingPhase).toBeVisible({ timeout: 5000 });

      // Prefer to discard for simplicity, but handle other cases
      const discardButton = page.getByRole('button', { name: /discard/i });
      const useActionButton = page.getByRole('button', { name: /use action/i });
      const swapButton = page.getByRole('button', { name: /swap cards/i });

      // Try discard first (simplest path)
      if (await discardButton.isVisible().catch(() => false)) {
        await discardButton.click();
      }
      // If no discard, try use action
      else if (await useActionButton.isVisible().catch(() => false)) {
        await useActionButton.click();
        // For peek actions, click the first available card
        await page
          .locator('[data-testid*="card"]')
          .first()
          .click({ timeout: 3000 })
          .catch(() => { /* empty */ });
      }
      // Otherwise must be swap
      else {
        await swapButton.click();
        // Click first player card to complete swap
        await page
          .locator('[data-testid="player-card-0"]')
          .first()
          .click({ timeout: 3000 });
      }

      // Wait for toss-in phase - this is a deterministic game state change
      const tossInPhase = page.locator('[data-testid="game-phase-toss-in"]');
      await expect(tossInPhase).toBeVisible({ timeout: 5000 });

      // Click "Call Vinto" button in toss-in phase
      const callVintoButton = page.locator('[data-testid="call-vinto"]').first();
      await expect(callVintoButton).toBeVisible({ timeout: 3000 });
      await callVintoButton.click();

      // Wait for and confirm the Vinto confirmation dialog
      const confirmVintoButton = page.locator('[data-testid="confirm-vinto"]');
      await expect(confirmVintoButton).toBeVisible({ timeout: 3000 });
      await confirmVintoButton.click();

      // Wait for confirmation dialog to close
      await expect(confirmVintoButton).toBeHidden({ timeout: 3000 });

      // Handle coalition selection dialog (only appears if a bot called Vinto)
      // If the human called Vinto, this dialog won't appear
      const coalitionConfirmButton = page.locator(
        '[data-testid="confirm-coalition-leader"]'
      );
      const hasCoalitionDialog = await coalitionConfirmButton
        .isVisible({ timeout: 2000 })
        .catch(() => false);

      if (hasCoalitionDialog) {
        // Click the first coalition member button to select them as leader
        const firstCoalitionMember = page
          .getByRole('button')
          .filter({ hasText: /bot [123]|you/i })
          .first();
        await expect(firstCoalitionMember).toBeVisible();
        await firstCoalitionMember.click();

        // Click "Confirm Leader" button using test ID
        await coalitionConfirmButton.click();

        // Wait for dialog to close
        await expect(coalitionConfirmButton).toBeHidden({ timeout: 3000 });
      }
    });

    await test.step('Verify game completion', async () => {
      // After calling Vinto, wait for the final round to complete
      // This requires all 3 bots to take their final turns, which may take time
      const gameEnd = page.locator('[data-testid="game-end"]');

      // Wait up to 60 seconds for all bot turns to complete and scoring to appear
      await expect(gameEnd).toBeVisible({ timeout: 60_000 });

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
