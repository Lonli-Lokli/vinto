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

const reportPath = path.join(__dirname, '..', 'playwright-report');
const resultsPath = path.join(reportPath, 'results.json');

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
 * Parses accessibility report files and extracts violation counts
 */
function parseAccessibilityReports() {
  const reports = [];
  let totalViolations = 0;

  console.log(`Looking for reports in: ${reportPath}`);

  if (!fs.existsSync(reportPath)) {
    console.log('Report directory does not exist');
    return { reports, totalViolations, hasReports: false };
  }

  try {
    const files = fs.readdirSync(reportPath);
    console.log(`Found ${files.length} files in report directory`);

    const accessibilityFiles = files.filter(
      (f) => f.startsWith('accessibility-report-') && f.endsWith('.md')
    );
    console.log(`Found ${accessibilityFiles.length} accessibility report files`);

    for (const file of accessibilityFiles) {
      const filePath = path.join(reportPath, file);
      const content = fs.readFileSync(filePath, 'utf-8');

      // Extract violation count from the report
      const violationMatch = content.match(/\*\*Total Violations\*\*:\s*(\d+)/);
      if (violationMatch) {
        const count = parseInt(violationMatch[1], 10);
        totalViolations += count;
        console.log(`  ${file}: ${count} violations`);

        reports.push({
          file,
          violations: count,
          status: count === 0 ? 'passed' : 'failed',
        });
      }
    }
  } catch (error) {
    console.error('Error reading accessibility reports:', error.message);
  }

  return {
    reports,
    totalViolations,
    hasReports: reports.length > 0,
  };
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
  if (stats.failed === 0) {
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
        // Truncate error message if too long
        const errorMsg = test.error.split('\n')[0];
        const truncatedError = errorMsg.length > 100 ? errorMsg.substring(0, 100) + '...' : errorMsg;
        summary += `  \`\`\`\n  ${truncatedError}\n  \`\`\`\n`;
      }
    });
    summary += '\n';
  }

  return summary;
}

/**
 * Generates markdown summary for accessibility reports
 */
function generateAccessibilitySummary(accessibilityData) {
  if (!accessibilityData.hasReports) {
    return '';
  }

  let summary = '### ♿ Accessibility Reports\n\n';

  for (const report of accessibilityData.reports) {
    const icon = report.status === 'passed' ? '✅' : '❌';
    summary += `- ${icon} **${report.file}**: ${
      report.violations === 0 ? 'No violations found' : `${report.violations} violation(s) found`
    }\n`;
  }

  summary += '\n';

  if (accessibilityData.totalViolations > 0) {
    summary += `⚠️ **Total Accessibility Violations**: ${accessibilityData.totalViolations}\n`;
  } else {
    summary += '✅ **No accessibility violations detected**\n';
  }

  return summary;
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
    hasReport: fs.existsSync(reportPath),
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
