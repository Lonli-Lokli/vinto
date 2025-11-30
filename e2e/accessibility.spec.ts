import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * Accessibility E2E Tests for Vinto Card Game
 *
 * This test suite uses axe-core to automatically scan for accessibility violations,
 * with a strong focus on color contrast and WCAG compliance.
 *
 * Tests are performed on both light and dark themes to ensure accessibility
 * across all color schemes.
 */

test.describe('Accessibility Tests', () => {
  test.describe('Homepage Accessibility', () => {
    test('should not have accessibility violations on homepage (light theme)', async ({ page }) => {
      // Navigate to the page
      await page.goto('/');

      // Wait for the page to fully load
      await page.waitForLoadState('domcontentloaded');

      // Set theme to light
      // The theme is controlled by next-themes which uses localStorage
      await page.evaluate(() => {
        localStorage.setItem('theme', 'light');
      });

      // Reload to apply theme
      await page.reload();
      await page.waitForLoadState('domcontentloaded');

      // Wait a bit for theme to be applied
      await page.waitForTimeout(500);

      // Verify light theme is active by checking the class on html element
      const htmlClass = await page.locator('html').getAttribute('class');
      expect(htmlClass).toContain('light');

      // Run accessibility scan with WCAG 2.1 AA standards
      const accessibilityScanResults = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();

      // Log violations for debugging if any are found
      if (accessibilityScanResults.violations.length > 0) {
        console.log('Accessibility violations found (light theme):');
        accessibilityScanResults.violations.forEach((violation) => {
          console.log(`- ${violation.id}: ${violation.description}`);
          console.log(`  Impact: ${violation.impact}`);
          console.log(`  Help: ${violation.helpUrl}`);
          console.log(`  Affected elements: ${violation.nodes.length}`);
          violation.nodes.forEach((node) => {
            console.log(`    - ${node.html}`);
          });
        });
      }

      // Assert that there are no violations
      expect(accessibilityScanResults.violations).toEqual([]);
    });

    test('should not have accessibility violations on homepage (dark theme)', async ({ page }) => {
      // Navigate to the page
      await page.goto('/');

      // Wait for the page to fully load
      await page.waitForLoadState('domcontentloaded');

      // Set theme to dark
      await page.evaluate(() => {
        localStorage.setItem('theme', 'dark');
      });

      // Reload to apply theme
      await page.reload();
      await page.waitForLoadState('domcontentloaded');

      // Wait a bit for theme to be applied
      await page.waitForTimeout(500);

      // Verify dark theme is active
      const htmlClass = await page.locator('html').getAttribute('class');
      expect(htmlClass).toContain('dark');

      // Run accessibility scan with WCAG 2.1 AA standards
      const accessibilityScanResults = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();

      // Log violations for debugging if any are found
      if (accessibilityScanResults.violations.length > 0) {
        console.log('Accessibility violations found (dark theme):');
        accessibilityScanResults.violations.forEach((violation) => {
          console.log(`- ${violation.id}: ${violation.description}`);
          console.log(`  Impact: ${violation.impact}`);
          console.log(`  Help: ${violation.helpUrl}`);
          console.log(`  Affected elements: ${violation.nodes.length}`);
          violation.nodes.forEach((node) => {
            console.log(`    - ${node.html}`);
          });
        });
      }

      // Assert that there are no violations
      expect(accessibilityScanResults.violations).toEqual([]);
    });
  });

  test.describe('Color Contrast Validation', () => {
    test('should detect color contrast violations when present', async ({ page }) => {
      // Create a page with known bad contrast for validation
      await page.setContent(`
        <!DOCTYPE html>
        <html>
          <head>
            <title>Color Contrast Test</title>
          </head>
          <body>
            <button style="background-color: #888; color: #999; padding: 10px;">
              Low Contrast Button
            </button>
            <p style="color: #777; background-color: #888;">
              Low contrast text
            </p>
          </body>
        </html>
      `);

      // Run accessibility scan
      const accessibilityScanResults = await new AxeBuilder({ page }).analyze();

      // Expect violations to be found
      expect(accessibilityScanResults.violations.length).toBeGreaterThan(0);

      // Check that at least one violation is related to color contrast
      const contrastViolation = accessibilityScanResults.violations.find(
        (v) => v.id === 'color-contrast'
      );
      expect(contrastViolation).toBeDefined();
    });

    test('should pass with good color contrast', async ({ page }) => {
      // Create a page with good contrast
      await page.setContent(`
        <!DOCTYPE html>
        <html>
          <head>
            <title>Good Contrast Test</title>
          </head>
          <body>
            <button style="background-color: #000; color: #fff; padding: 10px;">
              Good Contrast Button
            </button>
            <p style="color: #000; background-color: #fff;">
              Good contrast text
            </p>
          </body>
        </html>
      `);

      // Run accessibility scan
      const accessibilityScanResults = await new AxeBuilder({ page }).analyze();

      // Should have no color contrast violations
      const contrastViolation = accessibilityScanResults.violations.find(
        (v) => v.id === 'color-contrast'
      );
      expect(contrastViolation).toBeUndefined();
    });
  });

  test.describe('Game Interface Accessibility', () => {
    test('should not have accessibility violations on game board (light theme)', async ({ page }) => {
      // Navigate to the page
      await page.goto('/');

      // Set theme to light
      await page.evaluate(() => {
        localStorage.setItem('theme', 'light');
      });

      await page.reload();
      await page.waitForLoadState('domcontentloaded');

      // Wait for game board to be visible
      const gameBoard = page.locator('[data-testid="game-board"]').or(page.getByRole('main'));
      await expect(gameBoard).toBeVisible({ timeout: 15000 });

      // Run accessibility scan
      const accessibilityScanResults = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();

      // Log violations if any
      if (accessibilityScanResults.violations.length > 0) {
        console.log('Accessibility violations found on game board (light theme):');
        accessibilityScanResults.violations.forEach((violation) => {
          console.log(`- ${violation.id}: ${violation.description}`);
        });
      }

      expect(accessibilityScanResults.violations).toEqual([]);
    });

    test('should not have accessibility violations on game board (dark theme)', async ({ page }) => {
      // Navigate to the page
      await page.goto('/');

      // Set theme to dark
      await page.evaluate(() => {
        localStorage.setItem('theme', 'dark');
      });

      await page.reload();
      await page.waitForLoadState('domcontentloaded');

      // Wait for game board to be visible
      const gameBoard = page.locator('[data-testid="game-board"]').or(page.getByRole('main'));
      await expect(gameBoard).toBeVisible({ timeout: 15000 });

      // Run accessibility scan
      const accessibilityScanResults = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();

      // Log violations if any
      if (accessibilityScanResults.violations.length > 0) {
        console.log('Accessibility violations found on game board (dark theme):');
        accessibilityScanResults.violations.forEach((violation) => {
          console.log(`- ${violation.id}: ${violation.description}`);
        });
      }

      expect(accessibilityScanResults.violations).toEqual([]);
    });
  });

  test.describe('Specific WCAG Rules', () => {
    test('should have valid ARIA attributes', async ({ page }) => {
      await page.goto('/');
      await page.waitForLoadState('domcontentloaded');

      const accessibilityScanResults = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa'])
        .analyze();

      // Check for ARIA-related violations
      const ariaViolations = accessibilityScanResults.violations.filter(
        (v) => v.id.includes('aria') || v.tags.includes('aria')
      );

      expect(ariaViolations).toEqual([]);
    });

    test('should have proper heading hierarchy', async ({ page }) => {
      await page.goto('/');
      await page.waitForLoadState('domcontentloaded');

      const accessibilityScanResults = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa'])
        .analyze();

      // Check for heading order violations
      const headingViolations = accessibilityScanResults.violations.filter(
        (v) => v.id === 'heading-order'
      );

      expect(headingViolations).toEqual([]);
    });

    test('should have accessible form controls', async ({ page }) => {
      await page.goto('/');
      await page.waitForLoadState('domcontentloaded');

      const accessibilityScanResults = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa'])
        .analyze();

      // Check for form-related violations
      const formViolations = accessibilityScanResults.violations.filter(
        (v) => v.id.includes('label') || v.id.includes('form')
      );

      expect(formViolations).toEqual([]);
    });
  });
});
