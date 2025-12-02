#!/usr/bin/env node

/**
 * Generates an E2E test report summary by parsing Playwright test results
 * and accessibility reports from the playwright-report folder.
 *
 * Outputs a JSON object with test status and accessibility violations.
 * Usage: node tools/generate-e2e-report.js
 */

const fs = require('fs');
const path = require('path');

const playwrightReportPath = path.join(__dirname, '..', 'playwright-report');
const accessibilityReportPath = path.join(__dirname, '..', 'accessibility-reports');
const resultsPath = path.join(playwrightReportPath, 'results.json');

/**
 * Parses Playwright test results JSON
 */
function parseTestResults() {
  if (!fs.existsSync(resultsPath)) {
    console.log('Test results JSON not found');
    return null;
  }

  try {
    const content = fs.readFileSync(resultsPath, 'utf-8');
    const results = JSON.parse(content);

    const stats = {
      total: 0,
      passed: 0,
      failed: 0,
      skipped: 0,
      flaky: 0,
      duration: results.stats?.duration || 0,
    };

    const failedTests = [];

    // Parse suites and tests
    if (results.suites) {
      const parseTests = (suite) => {
        if (suite.specs) {
          suite.specs.forEach((spec) => {
            stats.total++;

            if (spec.ok === true) {
              stats.passed++;
            } else if (spec.ok === false) {
              stats.failed++;
              failedTests.push({
                title: spec.title,
                file: spec.file || suite.file,
                error: spec.tests?.[0]?.results?.[0]?.error?.message || 'Test failed',
              });
            }
          });
        }

        if (suite.suites) {
          suite.suites.forEach(parseTests);
        }
      };

      results.suites.forEach(parseTests);
    }

    console.log(`Test results: ${stats.passed}/${stats.total} passed`);

    return { stats, failedTests };
  } catch (error) {
    console.error('Error parsing test results:', error.message);
    return null;
  }
}

/**
 * Parses accessibility report files and extracts violation counts and tickets
 */
function parseAccessibilityReports() {
  const reports = [];
  let totalViolations = 0;
  let totalTickets = 0;

  console.log(`Looking for accessibility reports in: ${accessibilityReportPath}`);

  if (!fs.existsSync(accessibilityReportPath)) {
    console.log('Accessibility report directory does not exist');
    return { reports, totalViolations, totalTickets, hasReports: false };
  }

  try {
    const files = fs.readdirSync(accessibilityReportPath);
    console.log(`Found ${files.length} files in accessibility report directory`);

    const accessibilityFiles = files.filter(
      (f) => f.startsWith('accessibility-report-') && f.endsWith('.md') && f !== 'README.md'
    );
    console.log(`Found ${accessibilityFiles.length} accessibility report files`);

    for (const file of accessibilityFiles) {
      const filePath = path.join(accessibilityReportPath, file);
      const content = fs.readFileSync(filePath, 'utf-8');

      // Extract issue count from the new format
      const issueMatch = content.match(/\*\*Total Issues\*\*:\s*(\d+)/);
      // Also check old format for backward compatibility
      const violationMatch = content.match(/\*\*Total Violations\*\*:\s*(\d+)/);

      const count = issueMatch
        ? parseInt(issueMatch[1], 10)
        : violationMatch
        ? parseInt(violationMatch[1], 10)
        : 0;

      // Count tickets in the new format
      const ticketMatches = content.match(/## Ticket \d+:/g) || [];
      const ticketCount = ticketMatches.length;

      totalViolations += count;
      totalTickets += ticketCount;
      console.log(`  ${file}: ${count} violations, ${ticketCount} tickets`);

      reports.push({
        file,
        violations: count,
        tickets: ticketCount,
        status: count === 0 ? 'passed' : 'failed',
        content: content,
      });
    }
  } catch (error) {
    console.error('Error reading accessibility reports:', error.message);
  }

  return {
    reports,
    totalViolations,
    totalTickets,
    hasReports: reports.length > 0,
  };
}

/**
 * Strips ANSI escape codes from text
 */
function stripAnsi(text) {
  // Remove ANSI escape codes (color, formatting, etc.)
  // eslint-disable-next-line no-control-regex
  return text.replace(/\x1b\[[0-9;]*m/g, '');
}

/**
 * Generates markdown summary for test results
 */
function generateTestSummary(testResults) {
  if (!testResults) {
    return '';
  }

  const { stats, failedTests } = testResults;
  let summary = '### 🎭 Test Results\n\n';

  // Overall status
  if (stats.total === 0) {
    summary += `⚠️ **No tests were run!** Check for syntax errors or test collection failures.\n\n`;
  } else if (stats.failed === 0) {
    summary += `✅ **All tests passed!** (${stats.passed}/${stats.total})\n\n`;
  } else {
    summary += `❌ **${stats.failed} test(s) failed** (${stats.passed}/${stats.total} passed)\n\n`;
  }

  // Duration
  const durationSec = (stats.duration / 1000).toFixed(1);
  summary += `⏱️ Duration: ${durationSec}s\n\n`;

  // Failed tests details
  if (failedTests.length > 0) {
    summary += '**Failed tests:**\n';
    failedTests.forEach((test) => {
      summary += `- ❌ ${test.title}\n`;
      if (test.error) {
        // Strip ANSI codes and truncate error message if too long
        const cleanError = stripAnsi(test.error);
        const errorMsg = cleanError.split('\n')[0];
        const truncatedError = errorMsg.length > 100 ? errorMsg.substring(0, 100) + '...' : errorMsg;
        summary += `  \`\`\`\n  ${truncatedError}\n  \`\`\`\n`;
      }
    });
    summary += '\n';
  }

  return summary;
}

/**
 * Generates markdown summary for accessibility reports with collapsible details
 */
function generateAccessibilitySummary(accessibilityData) {
  if (!accessibilityData.hasReports) {
    return '';
  }

  let summary = '### ♿ Accessibility Reports\n\n';

  if (accessibilityData.totalViolations === 0) {
    summary += '✅ **No accessibility violations detected**\n\n';
    return summary;
  }

  // Overall summary
  summary += `⚠️ **Total Violations**: ${accessibilityData.totalViolations}\n`;
  summary += `🎫 **Total Tickets**: ${accessibilityData.totalTickets}\n\n`;

  // Generate collapsible sections for each report with violations
  for (const report of accessibilityData.reports) {
    if (report.violations === 0) {
      summary += `✅ **${getSuiteName(report.file)}**: No violations\n\n`;
      continue;
    }

    const suiteName = getSuiteName(report.file);
    summary += `<details>\n`;
    summary += `<summary>`;
    summary += `❌ <strong>${suiteName}</strong>: ${report.violations} violation(s) - ${report.tickets} ticket(s) ready to report`;
    summary += `</summary>\n\n`;

    // Include the full report content
    summary += report.content + '\n';

    summary += `</details>\n\n`;
  }

  return summary;
}

/**
 * Extracts a human-readable suite name from filename
 */
function getSuiteName(filename) {
  // Convert filename like "accessibility-report-homepage-accessibility.md" to "Homepage Accessibility"
  const match = filename.match(/accessibility-report-(.+)\.md/);
  if (!match) return filename;

  return match[1]
    .split('-')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

/**
 * Main function
 */
function main() {
  const testResults = parseTestResults();
  const accessibilityData = parseAccessibilityReports();

  // Combine summaries
  let summary = '';
  if (testResults) {
    summary += generateTestSummary(testResults);
  }
  if (accessibilityData.hasReports) {
    summary += generateAccessibilitySummary(accessibilityData);
  }

  const result = {
    hasReport: fs.existsSync(playwrightReportPath),
    testResults,
    accessibility: accessibilityData,
    summary,
  };

  // When run in GitHub Actions, write to output file
  if (process.env.GITHUB_OUTPUT) {
    const outputFile = process.env.GITHUB_OUTPUT;
    // Use base64 encoding to safely pass JSON through GitHub Actions
    const jsonBase64 = Buffer.from(JSON.stringify(result)).toString('base64');
    fs.appendFileSync(outputFile, `report=${jsonBase64}\n`);
    console.log('Report data written to GITHUB_OUTPUT');
  } else {
    // When run locally, output pretty JSON to console
    console.log(JSON.stringify(result, null, 2));
  }
}

main();
