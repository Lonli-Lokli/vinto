# Vinto E2E Tests

End-to-end tests for the Vinto card game using Playwright.

## Running Tests

### Local Testing (with dev server)

```bash
# Install Playwright browsers (one-time setup)
npx playwright install chromium

# Run tests in headless mode
npm run test:e2e

# Run tests with interactive UI
npm run test:e2e:ui

# Run tests in debug mode
npm run test:e2e:debug

# View HTML report after tests
npm run test:e2e:report
```

### Testing Against Vercel Preview

To test against a specific Vercel preview URL:

```bash
PLAYWRIGHT_TEST_BASE_URL=https://your-preview-url.vercel.app npm run test:e2e
```

### CI/CD Integration

The tests automatically run on PRs to master via GitHub Actions. The workflow:

1. Waits for Vercel to deploy the preview
2. Extracts the preview URL from Vercel deployment
3. Runs Playwright tests against the preview URL
4. Reports results back to the PR

## Test Structure

- `happy-path.spec.ts` - Complete game flow from start to finish
  - Game initialization
  - Memory phase (peeking at cards)
  - Gameplay turns
  - Calling Vinto
  - Game completion verification

## Writing Tests

### Best Practices

1. **Use role-based selectors** when possible:
   ```ts
   page.getByRole('button', { name: /start game/i })
   ```

2. **Use data-testid for stable selectors**:
   ```ts
   page.locator('[data-testid="draw-pile"]')
   ```

3. **Use test.step() for organization**:
   ```ts
   await test.step('Draw a card', async () => {
     // test code
   });
   ```

4. **Wait for visibility before interaction**:
   ```ts
   await expect(button).toBeVisible();
   await button.click();
   ```

5. **Take screenshots for debugging**:
   ```ts
   await page.screenshot({ path: 'screenshot.png' });
   ```

## Debugging

### Run in headed mode
```bash
npx playwright test --headed
```

### Run in debug mode with Playwright Inspector
```bash
npx playwright test --debug
```

### View trace of failed tests
```bash
npx playwright show-report
```

## Configuration

See `playwright.config.ts` for configuration details. Key settings:

- **Timeout**: 60 seconds per test
- **Retries**: 2 on CI, 0 locally
- **Browsers**: Chromium (Firefox and WebKit can be enabled)
- **Screenshots**: Captured on failure
- **Video**: Recorded on failure (CI only)
- **Trace**: Retained on failure for debugging

## Troubleshooting

### Tests timing out

Increase timeout in test:
```ts
test('my test', async ({ page }) => {
  test.setTimeout(120000); // 2 minutes
  // ...
});
```

### Element not found

Use multiple selectors with .or():
```ts
const button = page.getByRole('button', { name: /start/i })
  .or(page.locator('[data-testid="start-button"]'));
```

### Flaky tests

Add appropriate waits:
```ts
await expect(element).toBeVisible();
await page.waitForLoadState('networkidle');
```
