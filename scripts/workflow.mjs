// @ts-check
import { spawnSync } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repository = dirname(dirname(fileURLToPath(import.meta.url)));
const workflows = ['verify', 'verify-tools', 'verify-backend'];
const usage = 'Usage: node scripts/workflow.mjs [verify|verify-tools|verify-backend] [--dry-run]';

/** @typedef {{ name: string, command: string, args: string[], cwd: string, timeoutMs: number }} Step */

/**
 * Fixed local commands only; paths are anchored to the checkout, not the caller.
 * @param {string} workflow
 * @param {string} [root]
 * @returns {Step[]}
 */
export function plan(workflow, root = repository) {
  if (!workflows.includes(workflow)) throw new Error('Unknown workflow');
  /** @type {Step[]} */
  const steps = [];
  /** @param {string} name @param {string[]} args */
  function node(name, args) {
    steps.push({ name, command: process.execPath, args, cwd: root, timeoutMs: 60_000 });
  }

  if (workflow === 'verify' || workflow === 'verify-tools') {
    node('Audit shell prerequisites', ['scripts/doctor.mjs', '--profile', 'shell']);
    const scripts = readdirSync(join(root, 'scripts')).filter(file => file.endsWith('.mjs'))
      .map(file => join('scripts', file));
    const tests = readdirSync(join(root, 'scripts/tests')).filter(file => file.endsWith('.test.mjs'))
      .map(file => join('scripts/tests', file));
    for (const file of [...scripts, ...tests].sort()) node(`Check syntax: ${file}`, ['--check', file]);
    node('Test repository tooling', ['--test', ...tests.sort()]);
  }
  if (workflow === 'verify' || workflow === 'verify-backend') {
    node('Audit backend prerequisites', ['scripts/doctor.mjs', '--profile', 'backend']);
    steps.push({
      name: 'Test, check coverage, and build backend',
      command: join(root, 'backend/gradlew'),
      args: ['--no-daemon', 'clean', 'test', 'jacocoTestReport', 'jacocoTestCoverageVerification', 'bootJar', '--warning-mode', 'fail'],
      cwd: join(root, 'backend'),
      timeoutMs: 15 * 60_000,
    });
  }
  return steps;
}

/**
 * Stream build output while bounding runtime. Never invoke a shell.
 * @param {Step} step
 * @returns {'passed' | 'failed' | 'timed out' | 'could not start'}
 */
export function execute(step) {
  const result = spawnSync(step.command, step.args, {
    cwd: step.cwd, stdio: 'inherit', timeout: step.timeoutMs,
    killSignal: 'SIGKILL', windowsHide: true,
  });
  if (result.error && 'code' in result.error && result.error.code === 'ETIMEDOUT') return 'timed out';
  if (result.error) return 'could not start';
  return result.status === 0 ? 'passed' : 'failed';
}

/** @param {string[]} args */
export function main(args) {
  if (args.length === 1 && (args[0] === '--help' || args[0] === '-h')) {
    console.log(usage);
    return 0;
  }
  const names = args.filter(arg => arg !== '--dry-run');
  const workflow = names[0] ?? 'verify';
  if (names.length > 1 || !workflows.includes(workflow)
      || args.filter(arg => arg === '--dry-run').length > 1) {
    console.error(usage);
    return 2;
  }
  const steps = plan(workflow);
  if (args.includes('--dry-run')) {
    console.log(JSON.stringify({ workflow, steps }, null, 2));
    return 0;
  }
  for (const [index, step] of steps.entries()) {
    console.log(`[${index + 1}/${steps.length}] ${step.name}`);
    const result = execute(step);
    if (result !== 'passed') {
      console.error(`Workflow ${workflow} stopped: ${step.name} ${result}.`);
      return 1;
    }
  }
  console.log(`Workflow ${workflow} passed (${steps.length} steps).`);
  return 0;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = main(process.argv.slice(2));
}
