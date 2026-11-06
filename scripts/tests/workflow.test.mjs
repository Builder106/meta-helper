// @ts-check
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { execute } from '../workflow.mjs';

/** @param {import('node:test').TestContext} t */
function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'meta-helper workflow '));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(join(root, 'scripts/tests'), { recursive: true });
  mkdirSync(join(root, 'backend'));
  copyFileSync(fileURLToPath(new URL('../workflow.mjs', import.meta.url)), join(root, 'scripts/workflow.mjs'));
  // Exercise the real orchestrator with recording commands in an isolated checkout.
  writeFileSync(join(root, 'scripts/doctor.mjs'), `
    import { appendFileSync } from 'node:fs';
    appendFileSync('calls', JSON.stringify({ tool: 'doctor', args: process.argv.slice(2), cwd: process.cwd() }) + '\\n');
    process.exitCode = Number(process.env.FIXTURE_DOCTOR_EXIT ?? 0);
  `);
  writeFileSync(join(root, 'scripts/tests/sample.test.mjs'), `
    import { appendFileSync } from 'node:fs';
    import { test } from 'node:test';
    test('fixture', () => appendFileSync('calls', JSON.stringify({ tool: 'test' }) + '\\n'));
  `);
  writeFileSync(join(root, 'backend/gradlew'), `#!${process.execPath}
    const fs = require('node:fs');
    fs.appendFileSync('../calls', JSON.stringify({ tool: 'gradle', args: process.argv.slice(2), cwd: process.cwd() }) + '\\n');
    process.exitCode = Number(process.env.FIXTURE_GRADLE_EXIT ?? 0);
  `, { mode: 0o755 });
  /** @param {string[]} args @param {NodeJS.ProcessEnv} [env] */
  function run(args, env = {}) {
    // A nested CLI must launch its own test runner rather than inherit our worker context.
    const childEnv = { ...process.env, ...env };
    delete childEnv.NODE_TEST_CONTEXT;
    return spawnSync(process.execPath, [join(root, 'scripts/workflow.mjs'), ...args], {
      cwd: tmpdir(), env: childEnv, encoding: 'utf8', timeout: 15_000,
    });
  }
  /** @returns {{ tool: string, args?: string[], cwd?: string }[]} */
  function calls() {
    return existsSync(join(root, 'calls'))
      ? readFileSync(join(root, 'calls'), 'utf8').trim().split('\n').map(line => JSON.parse(line))
      : [];
  }
  return { root, run, calls };
}

test('combined workflow runs tooling before backend and anchors working directories', t => {
  const f = fixture(t);
  const result = f.run([]);
  assert.equal(result.status, 0, result.stderr);
  const calls = f.calls();
  assert.deepEqual(calls.map(call => call.tool), ['doctor', 'test', 'doctor', 'gradle']);
  assert.deepEqual(calls[0].args, ['--profile', 'shell']);
  assert.deepEqual(calls[2].args, ['--profile', 'backend']);
  assert.equal(calls[0].cwd, f.root);
  assert.equal(calls[3].cwd, join(f.root, 'backend'));
  for (const task of ['test', 'jacocoTestCoverageVerification', 'bootJar', '--no-daemon']) {
    assert.ok(calls[3].args?.includes(task));
  }
  assert.match(result.stdout, /Workflow verify passed/);
});

test('individual workflows isolate tooling and backend', t => {
  const f = fixture(t);
  assert.equal(f.run(['verify-tools']).status, 0);
  assert.deepEqual(f.calls().map(call => call.tool), ['doctor', 'test']);
  rmSync(join(f.root, 'calls'));
  assert.equal(f.run(['verify-backend']).status, 0);
  assert.deepEqual(f.calls().map(call => call.tool), ['doctor', 'gradle']);
});

test('a prerequisite failure stops before running tests or builds', t => {
  const f = fixture(t);
  const result = f.run(['verify'], { FIXTURE_DOCTOR_EXIT: '1' });
  assert.equal(result.status, 1);
  assert.deepEqual(f.calls().map(call => call.tool), ['doctor']);
  assert.match(result.stderr, /stopped: Audit shell prerequisites failed/);
});

test('a syntax failure prevents tests and backend execution', t => {
  const f = fixture(t);
  writeFileSync(join(f.root, 'scripts/broken.mjs'), 'const = ;');
  const result = f.run(['verify']);
  assert.equal(result.status, 1);
  assert.deepEqual(f.calls().map(call => call.tool), ['doctor']);
  assert.match(result.stderr, /Check syntax: scripts\/broken.mjs failed/);
});

test('a test failure prevents backend execution', t => {
  const f = fixture(t);
  writeFileSync(join(f.root, 'scripts/tests/failure.test.mjs'), "throw new Error('fixture failure');");
  const result = f.run(['verify']);
  assert.equal(result.status, 1);
  assert.ok(f.calls().every(call => call.tool !== 'gradle'));
  assert.match(result.stderr, /Test repository tooling failed/);
});

test('backend failure propagates a failing workflow exit code', t => {
  const f = fixture(t);
  const result = f.run(['verify-backend'], { FIXTURE_GRADLE_EXIT: '7' });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /build backend failed/);
  assert.doesNotMatch(result.stdout, /Workflow verify-backend passed/);
});

test('dry run returns a bounded plan and executes no commands', t => {
  const f = fixture(t);
  const result = f.run(['verify', '--dry-run']);
  assert.equal(result.status, 0);
  const report = JSON.parse(result.stdout);
  assert.equal(report.workflow, 'verify');
  assert.ok(report.steps.length > 0);
  assert.ok(report.steps.every((/** @type {import('../workflow.mjs').Step} */ step) => step.timeoutMs > 0));
  assert.deepEqual(f.calls(), []);
});

test('help and invalid arguments never execute commands', t => {
  const f = fixture(t);
  assert.equal(f.run(['--help']).status, 0);
  for (const args of [['unknown'], ['verify; touch unwanted'], ['verify', 'verify-tools'], ['--dry-run', '--dry-run'], ['--unknown']]) {
    const result = f.run(args);
    assert.equal(result.status, 2);
    assert.match(result.stderr, /Usage:/);
  }
  assert.deepEqual(f.calls(), []);
});

test('execution handles success, failure, missing commands, signals, and timeouts', () => {
  const step = { name: 'fixture', command: process.execPath, args: ['-e', ''], cwd: tmpdir(), timeoutMs: 5000 };
  assert.equal(execute(step), 'passed');
  assert.equal(execute({ ...step, args: ['-e', 'process.exitCode = 7'] }), 'failed');
  assert.equal(execute({ ...step, command: join(tmpdir(), 'missing-workflow-command') }), 'could not start');
  assert.equal(execute({ ...step, args: ['-e', "process.kill(process.pid, 'SIGTERM')"] }), 'failed');
  assert.equal(execute({ ...step, args: ['-e', 'setInterval(() => {}, 1000)'], timeoutMs: 100 }), 'timed out');
});
