// @ts-check
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import {
  classifyLog,
  DEFAULT_RULES,
  formatReport,
  main,
  normalizeSignature,
  parseLogEntries,
  parseSeverity,
  SEVERITY_LEVELS,
} from '../logs.mjs';

const script = fileURLToPath(new URL('../logs.mjs', import.meta.url));

/** @param {import('node:test').TestContext} t */
function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'meta-helper logs '));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(join(root, 'scripts'));
  copyFileSync(script, join(root, 'scripts/logs.mjs'));

  /** @param {string} name @param {string} content */
  function logFile(name, content) {
    const fullPath = join(root, name);
    writeFileSync(fullPath, content);
    return fullPath;
  }

  /** @param {string[]} args @param {{ input?: string }} [opts] */
  function run(args, opts = {}) {
    return spawnSync(process.execPath, [join(root, 'scripts/logs.mjs'), ...args], {
      cwd: tmpdir(),
      encoding: 'utf8',
      timeout: 5000,
      input: opts.input,
      env: { PATH: process.env.PATH ?? '', HOME: root },
    });
  }

  return { root, logFile, run };
}

test('parses log entries and groups multi-line stack traces', () => {
  const text = [
    '2026-11-06T10:00:00Z INFO Server started on port 8080',
    '2026-11-06T10:00:01Z ERROR Failed to handle image request',
    'java.lang.NullPointerException: Cannot invoke method on null object',
    '\tat com.example.ImageController.process(ImageController.java:42)',
    '\t... 12 more',
    '2026-11-06T10:00:02Z WARN Low memory threshold reached',
  ].join('\n');

  const entries = parseLogEntries(text);
  assert.equal(entries.length, 3);
  assert.equal(entries[0].level, 'INFO');
  assert.equal(entries[0].lineNumber, 1);

  assert.equal(entries[1].level, 'ERROR');
  assert.equal(entries[1].lineNumber, 2);
  assert.equal(entries[1].stackLines.length, 3);
  assert.ok(entries[1].stackLines[0].includes('NullPointerException'));

  assert.equal(entries[2].level, 'WARN');
  assert.equal(entries[2].lineNumber, 6);
});

test('classifies core anomaly categories with automated regex rules', () => {
  const samples = [
    {
      log: '2026-11-06 12:00:00 [ERROR] java.lang.OutOfMemoryError: Java heap space',
      expectedCategory: 'RESOURCE_EXHAUSTION',
      expectedSeverity: 'critical',
    },
    {
      log: '2026-11-06 12:00:00 [FATAL] panic: runtime error: invalid memory address or nil pointer dereference',
      expectedCategory: 'FATAL_CRASH',
      expectedSeverity: 'critical',
    },
    {
      log: '2026-11-06 12:00:00 [ERROR] Upstream returned 503 UNAVAILABLE: high demand',
      expectedCategory: 'SERVICE_UNAVAILABLE',
      expectedSeverity: 'error',
    },
    {
      log: '2026-11-06 12:00:00 [ERROR] Call failed: HTTP 429 Too Many Requests (RESOURCE_EXHAUSTED)',
      expectedCategory: 'RATE_LIMIT_EXHAUSTED',
      expectedSeverity: 'error',
    },
    {
      log: '2026-11-06 12:00:00 [ERROR] ConnectTimeoutException: connection timed out to backend',
      expectedCategory: 'NETWORK_TIMEOUT',
      expectedSeverity: 'error',
    },
    {
      log: '2026-11-06 12:00:00 [ERROR] HTTP 403 Forbidden: PERMISSION_DENIED on /process-image',
      expectedCategory: 'AUTH_PERMISSION',
      expectedSeverity: 'error',
    },
    {
      log: '2026-11-06 12:00:00 [ERROR] Uncaught TypeError: Cannot read property of undefined',
      expectedCategory: 'RUNTIME_EXCEPTION',
      expectedSeverity: 'error',
    },
    {
      log: '2026-11-06 12:00:00 [ERROR] ffmpeg exited with code 1: Conversion failed',
      expectedCategory: 'SUBPROCESS_FAILURE',
      expectedSeverity: 'error',
    },
    {
      log: 'Execution failed for task \':backend:jacocoTestCoverageVerification\'',
      expectedCategory: 'BUILD_FAILURE',
      expectedSeverity: 'error',
    },
    {
      log: '2026-11-06 12:00:00 [ERROR] No space left on device (ENOSPC)',
      expectedCategory: 'RESOURCE_EXHAUSTION',
      expectedSeverity: 'error',
    },
    {
      log: '2026-11-06 12:00:00 [WARN] The following features are deprecated and scheduled for removal',
      expectedCategory: 'DEPRECATION_WARNING',
      expectedSeverity: 'warning',
    },
    {
      log: '2026-11-06 12:00:00 [ERROR] Unhandled custom system state failure',
      expectedCategory: 'APPLICATION_ERROR',
      expectedSeverity: 'error',
    },
    {
      log: '2026-11-06 12:00:00 [WARN] Degraded cache hit ratio detected',
      expectedCategory: 'APPLICATION_WARNING',
      expectedSeverity: 'warning',
    },
  ];

  for (const { log, expectedCategory, expectedSeverity } of samples) {
    const result = classifyLog(log, { minSeverity: 'all' });
    assert.equal(
      result.anomalies.length,
      1,
      `Expected 1 anomaly for: ${log}`,
    );
    assert.equal(
      result.anomalies[0].category,
      expectedCategory,
      `Category mismatch for: ${log}`,
    );
    assert.equal(
      result.anomalies[0].severity,
      expectedSeverity,
      `Severity mismatch for: ${log}`,
    );
    assert.ok(result.anomalies[0].remedy.length > 0);
  }
});

test('inspects multi-line stack trace to classify root runtime exception', () => {
  const log = [
    '2026-11-06 12:00:00 [ERROR] Request processing failed',
    'java.lang.IllegalStateException: Missing required header',
    '\tat org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)',
  ].join('\n');

  const result = classifyLog(log);
  assert.equal(result.anomalies.length, 1);
  assert.equal(result.anomalies[0].category, 'RUNTIME_EXCEPTION');
  assert.ok(result.anomalies[0].sample.includes('IllegalStateException'));
});

test('normalizes timestamps, IDs, and threads to deduplicate recurring anomalies', () => {
  const log = [
    '2026-11-06T12:00:01.123Z ERROR 45123 --- [nio-8080-exec-1] c.e.ImageController : 503 UNAVAILABLE: high demand',
    '2026-11-06T12:00:05.456Z ERROR 45124 --- [nio-8080-exec-2] c.e.ImageController : 503 UNAVAILABLE: high demand',
    '2026-11-06T12:00:09.789Z ERROR 45125 --- [nio-8080-exec-3] c.e.ImageController : 503 UNAVAILABLE: high demand',
  ].join('\n');

  const result = classifyLog(log);
  assert.equal(result.summary.totalLines, 3);
  assert.equal(result.summary.totalAnomalies, 3);
  assert.equal(result.summary.uniqueAnomalies, 1);
  assert.equal(result.anomalies.length, 1);
  assert.equal(result.anomalies[0].count, 3);
  assert.equal(result.anomalies[0].firstLine, 1);
  assert.equal(result.anomalies[0].lastLine, 3);
  assert.deepEqual(result.anomalies[0].lines, [1, 2, 3]);
});

test('filters anomalies by minSeverity, category, and limit', () => {
  const log = [
    '2026-11-06 12:00:00 [WARN] Deprecated API call in shared module',
    '2026-11-06 12:00:01 [ERROR] ConnectTimeoutException: connection timed out',
    '2026-11-06 12:00:02 [FATAL] java.lang.OutOfMemoryError: Java heap space',
  ].join('\n');

  // minSeverity = critical
  const critOnly = classifyLog(log, { minSeverity: 'critical' });
  assert.equal(critOnly.anomalies.length, 1);
  assert.equal(critOnly.anomalies[0].severity, 'critical');

  // minSeverity = error (includes critical + error)
  const errAndCrit = classifyLog(log, { minSeverity: 'error' });
  assert.equal(errAndCrit.anomalies.length, 2);

  // Filter by category
  const categoryFilter = classifyLog(log, { category: 'DEPRECATION_WARNING', minSeverity: 'all' });
  assert.equal(categoryFilter.anomalies.length, 1);
  assert.equal(categoryFilter.anomalies[0].category, 'DEPRECATION_WARNING');

  // Limit results
  const limited = classifyLog(log, { minSeverity: 'all', limit: 1 });
  assert.equal(limited.anomalies.length, 1);
  // Sorted critical first
  assert.equal(limited.anomalies[0].severity, 'critical');
});

test('supports custom regex pattern rule', () => {
  const log = [
    '2026-11-06 12:00:00 [INFO] Task started',
    '2026-11-06 12:00:01 [INFO] Checkpoint reached: BAD_MAGIC_BYTES_FOUND',
  ].join('\n');

  const result = classifyLog(log, { customPattern: /BAD_MAGIC_BYTES/i });
  assert.equal(result.anomalies.length, 1);
  assert.equal(result.anomalies[0].category, 'CUSTOM');
  assert.equal(result.anomalies[0].ruleId, 'CUSTOM_PATTERN');
});

test('formatReport produces clear human-readable output', () => {
  const cleanResult = classifyLog('2026-11-06 12:00:00 [INFO] All operations normal');
  assert.match(formatReport(cleanResult), /No anomalies detected/);

  const anomalyResult = classifyLog('2026-11-06 12:00:00 [ERROR] 503 UNAVAILABLE: high demand');
  const report = formatReport(anomalyResult);
  assert.match(report, /Diagnostic Log Anomaly Report/);
  assert.match(report, /\[ERROR\] SERVICE_UNAVAILABLE/);
  assert.match(report, /Remedy:/);
});

test('CLI classifies log files and piped stdin with JSON and text output', t => {
  const f = fixture(t);
  const logPath = f.logFile('app.log', [
    '2026-11-06 10:00:00 [INFO] Booting',
    '2026-11-06 10:00:01 [ERROR] 503 UNAVAILABLE: high demand',
    '2026-11-06 10:00:02 [ERROR] 503 UNAVAILABLE: high demand',
  ].join('\n'));

  // File input
  const resFile = f.run([logPath]);
  assert.equal(resFile.status, 0);
  assert.match(resFile.stdout, /SERVICE_UNAVAILABLE \(x2\)/);

  // JSON mode
  const resJson = f.run(['--json', logPath]);
  assert.equal(resJson.status, 0);
  const json = JSON.parse(resJson.stdout);
  assert.equal(json.summary.totalAnomalies, 2);
  assert.equal(json.summary.uniqueAnomalies, 1);
  assert.equal(json.anomalies[0].category, 'SERVICE_UNAVAILABLE');
  assert.equal(json.anomalies[0].count, 2);

  // Stdin input
  const resStdin = f.run(['-'], {
    input: '2026-11-06 12:00:00 [ERROR] ConnectTimeoutException: connection timed out\n',
  });
  assert.equal(resStdin.status, 0);
  assert.match(resStdin.stdout, /NETWORK_TIMEOUT/);
});

test('CLI fail-on gates build on anomaly threshold', t => {
  const f = fixture(t);
  const warnLog = f.logFile('warn.log', '2026-11-06 10:00:00 [WARN] Feature is deprecated\n');
  const errLog = f.logFile('err.log', '2026-11-06 10:00:00 [ERROR] ConnectTimeoutException\n');

  // --fail-on error with warning log: exit 0
  const passRes = f.run(['--fail-on', 'error', warnLog]);
  assert.equal(passRes.status, 0);

  // --fail-on error with error log: exit 1
  const failRes = f.run(['--fail-on', 'error', errLog]);
  assert.equal(failRes.status, 1);

  // --fail-on warn with warning log: exit 1
  const failWarn = f.run(['--fail-on', 'warn', warnLog]);
  assert.equal(failWarn.status, 1);
});

test('CLI usage errors exit 2 and file read errors exit 1', t => {
  const f = fixture(t);
  assert.equal(f.run(['--help']).status, 0);

  // Invalid arguments exit 2
  for (const args of [
    ['--unknown'],
    ['--severity', 'super-critical'],
    ['--fail-on', 'all'],
    ['--limit', '101'],
    ['--limit', '0'],
  ]) {
    const res = f.run(args);
    assert.equal(res.status, 2, `Expected exit 2 for args: ${args.join(' ')}`);
    assert.match(res.stderr, /Usage:/);
  }

  // Non-existent file exits 1
  const missing = f.run([join(f.root, 'non-existent.log')]);
  assert.equal(missing.status, 1);
  assert.match(missing.stderr, /Log read failure/);
});

test('rejects symlinks and oversized files safely', t => {
  const f = fixture(t);
  const target = f.logFile('real.log', '2026-11-06 [ERROR] Test error\n');
  const symlinkPath = join(f.root, 'symlink.log');
  symlinkSync(target, symlinkPath);

  const symlinkRes = f.run([symlinkPath]);
  assert.equal(symlinkRes.status, 1);
  assert.match(symlinkRes.stderr, /Log read failure/);
});
