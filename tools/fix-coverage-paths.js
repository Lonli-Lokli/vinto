#!/usr/bin/env node

/**
 * Fix coverage paths for monorepo
 *
 * Problem: Vitest generates coverage with paths relative to each package (e.g., "src/index.ts")
 * Solution: Add the package path prefix (e.g., "packages/engine/src/index.ts")
 *
 * This ensures Codecov can correctly match coverage to files in the GitHub repository.
 */

const fs = require('fs');
const path = require('path');
const glob = require('glob');

// Find all lcov.info files in the coverage directory
const lcovFiles = glob.sync('coverage/packages/*/lcov.info');

console.log(`Found ${lcovFiles.length} coverage files to process`);

lcovFiles.forEach(lcovPath => {
  // Extract package name from path: coverage/packages/engine/lcov.info -> engine
  // Normalize path to use forward slashes for consistent parsing
  const normalizedPath = lcovPath.replace(/\\/g, '/');
  const pathParts = normalizedPath.split('/');
  const packageName = pathParts[pathParts.length - 2]; // Get 'engine', 'bot', etc.
  const packagePrefix = `packages/${packageName}/`;

  console.log(`Processing ${lcovPath} (adding prefix: ${packagePrefix})`);

  // Read the lcov file
  let content = fs.readFileSync(lcovPath, 'utf8');

  // Replace SF:src\ or SF:src/ with SF:packages/engine/src/
  // This handles both Windows (\) and Unix (/) path separators
  content = content.replace(/^SF:src[\\\/]/gm, `SF:${packagePrefix}src/`);

  // Also handle the case where the path might already start with the full path
  // (shouldn't happen, but let's be defensive)
  content = content.replace(/^SF:(.+?)[\\\/](.+?)[\\\/]src[\\\/]/gm, `SF:${packagePrefix}src/`);

  // Normalize all backslashes to forward slashes for consistency
  // This ensures all paths use Unix-style separators (which Codecov expects)
  content = content.replace(/^SF:(.*)$/gm, (match, filePath) => {
    return `SF:${filePath.replace(/\\/g, '/')}`;
  });

  // Write the fixed content back
  fs.writeFileSync(lcovPath, content, 'utf8');

  console.log(`  ✓ Fixed paths in ${lcovPath}`);
});

console.log('\nCoverage paths fixed successfully!');
console.log('All lcov files now have correct monorepo paths.');
