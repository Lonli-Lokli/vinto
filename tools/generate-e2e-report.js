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

/**
 * Parses accessibility report files and extracts violation counts
 */
function parseAccessibilityReports() {
  const reports = [];
  let totalViolations = 0;

  if (!fs.existsSync(reportPath)) {
    return { reports, totalViolations, hasReports: false };
  }

  try {
    const files = fs.readdirSync(reportPath);
    const accessibilityFiles = files.filter(
      (f) => f.startsWith('accessibility-report-') && f.endsWith('.md')
    );

    for (const file of accessibilityFiles) {
      const filePath = path.join(reportPath, file);
      const content = fs.readFileSync(filePath, 'utf-8');

      // Extract violation count from the report
      const violationMatch = content.match(/\*\*Total Violations\*\*:\s*(\d+)/);
      if (violationMatch) {
        const count = parseInt(violationMatch[1], 10);
        totalViolations += count;

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
  const accessibilityData = parseAccessibilityReports();

  const result = {
    hasReport: fs.existsSync(reportPath),
    accessibility: accessibilityData,
    summary: generateAccessibilitySummary(accessibilityData),
  };

  // Output JSON for GitHub Actions to parse
  console.log(JSON.stringify(result, null, 2));
}

main();
