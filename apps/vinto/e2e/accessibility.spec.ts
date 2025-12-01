/* eslint-disable playwright/expect-expect */
import { test, expect, type Page, TestInfo } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import type { Result as AxeResult, NodeResult } from 'axe-core';

/**
 * Accessibility E2E Tests for Vinto Card Game
 *
 * This test suite uses axe-core to automatically scan for accessibility violations,
 * with a strong focus on color contrast and WCAG compliance.
 *
 * Tests are performed on both light and dark themes to ensure accessibility
 * across all color schemes.
 *
 * If violations are found, the test fails and generates a detailed accessibility-report.md file.
 */

const THEME_TRANSITION_TIMEOUT = 5000;

test.describe('Accessibility Tests', () => {
  (['light', 'dark'] as const).forEach((theme) => {
    test.describe(`Homepage Accessibility (${theme} theme)`, () => {
      test(`should not have accessibility violations on homepage (${theme} theme)`, async ({
        page,
      }, testInfo) => {
        // Navigate to the page
        await page.goto('/');
        await page.waitForLoadState('domcontentloaded');

        // Set theme to light
        await setTheme(page, theme);

        // Run accessibility scan
        await runAccessibilityScan(
          page,
          theme,
          `accessibility-report-homepage-${theme}.md`,
          testInfo
        );
      });
    });

    test.describe(`Color Contrast Validation (${theme} theme)`, () => {
      test('should detect color contrast violations when present', async ({
        page,
      }) => {
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
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();

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
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();

        // Should have no color contrast violations
        const contrastViolation = accessibilityScanResults.violations.find(
          (v) => v.id === 'color-contrast'
        );
        expect(contrastViolation).toBeUndefined();
      });
    });

    test.describe(`Game Interface Accessibility (${theme} theme)`, () => {
      test(`should not have accessibility violations on game board (${theme} theme)`, async ({
        page,
      }, testInfo) => {
        // Navigate to the page
        await page.goto('/');

        await setTheme(page, theme);

        // Wait for game board to be visible
        const gameBoard = page
          .locator('[data-testid="middle-area"]')
          .or(page.getByRole('main'));
        await expect(gameBoard).toBeVisible({ timeout: 15000 });

        // Run accessibility scan
        await runAccessibilityScan(
          page,
          theme,
          `accessibility-report-game-${theme}.md`,
          testInfo
        );
      });
    });

    test.describe(`Specific WCAG Rules (${theme} theme)`, () => {
      test('should have valid ARIA attributes', async ({ page }) => {
        await page.goto('/');
        await page.waitForLoadState('domcontentloaded');

        const accessibilityScanResults = await new AxeBuilder({ page })
          .withTags(['wcag2a', 'wcag2aa'])
          .analyze();

        // Check for ARIA-related violations
        const ariaViolations = accessibilityScanResults.violations.filter((v) =>
          Boolean(v.id.includes('aria') || v.tags.includes('aria'))
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
        const formViolations = accessibilityScanResults.violations.filter((v) =>
          Boolean(v.id.includes('label') || v.id.includes('form'))
        );

        expect(formViolations).toEqual([]);
      });
    });
  });
});

/**
 * Generates a detailed accessibility report in Markdown format
 */
function generateAccessibilityReport(
  url: string,
  violations: AxeResult[],
  theme: string
): string {
  let report = `# Accessibility Test Report\n\n`;
  report += `**Test Date**: ${new Date().toISOString()}\n`;
  report += `**URL**: ${url}\n`;
  report += `**Theme**: ${theme}\n`;
  report += `**Total Violations**: ${violations.length}\n\n`;

  if (violations.length === 0) {
    report += `✅ **No accessibility violations found!**\n`;
    return report;
  }

  report += `## Violations Summary\n\n`;

  // Group violations by impact
  const byImpact = violations.reduce((acc, v) => {
    const impact = v.impact || 'unknown';
    if (!acc[impact]) acc[impact] = [];
    acc[impact].push(v);
    return acc;
  }, {} as Record<string, AxeResult[]>);

  Object.keys(byImpact)
    .sort((a, b) => {
      const order = ['critical', 'serious', 'moderate', 'minor', 'unknown'];
      return order.indexOf(a) - order.indexOf(b);
    })
    .forEach((impact) => {
      report += `- **${impact.toUpperCase()}**: ${
        byImpact[impact].length
      } violation(s)\n`;
    });

  report += `\n---\n\n`;

  // Detailed violations
  violations.forEach((violation, index) => {
    report += `## ${index + 1}. ${violation.id}\n\n`;
    report += `**Description**: ${violation.description}\n\n`;
    report += `**Impact**: ${violation.impact || 'unknown'}\n\n`;
    report += `**WCAG Tags**: ${violation.tags.join(', ')}\n\n`;
    report += `**Help**: ${violation.help}\n\n`;
    report += `**Learn More**: [${violation.helpUrl}](${violation.helpUrl})\n\n`;
    report += `**Affected Elements**: ${violation.nodes.length}\n\n`;

    violation.nodes.forEach((node: NodeResult, nodeIndex: number) => {
      report += `### Element ${nodeIndex + 1}\n\n`;
      report += `**CSS Selector**: \`${node.target.join(' ')}\`\n\n`;
      report += `**HTML**:\n\`\`\`html\n${node.html}\n\`\`\`\n\n`;
      report += `**How to Fix**:\n${node.failureSummary}\n\n`;

      if (node.any && node.any.length > 0) {
        report += `**Fix any of the following**:\n`;
        node.any.forEach((fix) => {
          report += `- ${fix.message}\n`;
        });
        report += `\n`;
      }

      if (node.all && node.all.length > 0) {
        report += `**Fix all of the following**:\n`;
        node.all.forEach((fix) => {
          report += `- ${fix.message}\n`;
        });
        report += `\n`;
      }

      if (node.none && node.none.length > 0) {
        report += `**Fix none of the following**:\n`;
        node.none.forEach((fix) => {
          report += `- ${fix.message}\n`;
        });
        report += `\n`;
      }

      report += `---\n\n`;
    });
  });

  return report;
}

/**
 * Sets the theme and waits for it to be applied
 */
async function setTheme(page: Page, theme: 'light' | 'dark'): Promise<void> {
  await page.evaluate((t) => {
    localStorage.setItem('theme', t);
  }, theme);

  await page.reload();
  await page.waitForLoadState('domcontentloaded');

  // Wait for the theme class to be applied to the html element
  await page.locator(`html.${theme}`).waitFor({
    state: 'attached',
    timeout: THEME_TRANSITION_TIMEOUT,
  });

  // Verify theme is active
  const htmlClass = await page.locator('html').getAttribute('class');
  expect(htmlClass).toContain(theme);
}

/**
 * Runs accessibility scan and generates report if violations are found
 */
async function runAccessibilityScan(
  page: Page,
  theme: string,
  reportFilename: string,
  testInfo: TestInfo
): Promise<void> {
  // Run accessibility scan with WCAG 2.1 AA standards
  const accessibilityScanResults = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .disableRules(['meta-viewport']) // we do not want them
    .analyze();

  // Always generate and save report (for both pass and fail cases)
  const report = generateAccessibilityReport(
    page.url(),
    accessibilityScanResults.violations,
    theme
  );

  await testInfo.attach(reportFilename, {
    body: report,
    contentType: 'text/markdown',
  });

  // If violations found, log details and fail
  if (accessibilityScanResults.violations.length > 0) {
    console.log(`❌ Accessibility violations found (${theme} theme):`);
    accessibilityScanResults.violations.forEach((violation) => {
      console.log(`- ${violation.id}: ${violation.description}`);
      console.log(`  Impact: ${violation.impact}`);
      console.log(`  Help: ${violation.helpUrl}`);
      console.log(`  Affected elements: ${violation.nodes.length}`);
    });

    // Fail the test
    expect(accessibilityScanResults.violations).toEqual([]);
  } else {
    console.log(`✅ No accessibility violations found (${theme} theme)`);
  }
}
