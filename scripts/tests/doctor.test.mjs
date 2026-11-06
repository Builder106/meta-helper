// @ts-check
import assert from 'node:assert/strict';
import { chmodSync, mkdirSync, mkdtempSync, rmSync, unlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { audit, probe } from '../doctor.mjs';

const script = fileURLToPath(new URL('../doctor.mjs', import.meta.url));

/** @param {import('node:test').TestContext} t */
function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'meta-helper doctor '));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const bin = join(root, 'bin');
  mkdirSync(bin);
  /** @param {string} path @param {string} content @param {number} [mode] */
  function file(path, content, mode = 0o644) {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, content, { mode });
  }
  /** @param {string} name @param {string} output @param {string} [flag] */
  function tool(name, output, flag = '--version') {
    file(join(bin, name), `#!/bin/sh\n[ "$#" -eq 1 ] && [ "$1" = '${flag}' ] || exit 2\nprintf '%s\\n' '${output}' >&2\n`, 0o755);
  }
  tool('git', 'git version 2.50.0');
  tool('java', 'openjdk version "26.0.2" 2026-07-21', '-version');
  tool('javac', 'javac 26.0.2', '-version');
  tool('ffmpeg', 'ffmpeg version 8.0', '-version');
  tool('node', 'v24.20.0');
  tool('npm', '12.0.1');
  file(join(root, 'backend/gradlew'), '#!/bin/sh\nexit 99\n', 0o755);
  file(join(root, 'backend/gradle/wrapper/gradle-wrapper.jar'), 'fixture');
  file(join(root, 'backend/gradle/wrapper/gradle-wrapper.properties'), 'fixture');
  file(join(root, 'web/node_modules/astro/package.json'), '{}');
  file(join(root, 'web/node_modules/.bin/astro'), '#!/bin/sh\nexit 99\n', 0o755);
  /** @type {NodeJS.ProcessEnv} */
  const env = { PATH: bin, SHELL: '/bin/sh' };
  return { root, bin, env, file, tool };
}

test('a healthy checkout passes all local prerequisites without invoking build tools', t => {
  const f = fixture(t);
  const report = audit(f);
  assert.equal(report.ok, true);
  assert.ok(report.checks.length >= 10);
  assert.ok(report.checks.every(check => check.status === 'pass'));
});

test('profiles isolate backend and web prerequisites', t => {
  const f = fixture(t);
  for (const name of ['java', 'javac', 'ffmpeg']) unlinkSync(join(f.bin, name));
  assert.equal(audit({ ...f, profile: 'web' }).ok, true);
  assert.equal(audit({ ...f, profile: 'backend' }).ok, false);
  for (const name of ['node', 'npm']) unlinkSync(join(f.bin, name));
  assert.equal(audit({ ...f, profile: 'shell' }).ok, true);
});

test('missing tools produce separate actionable failures', t => {
  const f = fixture(t);
  for (const name of ['git', 'java', 'javac', 'ffmpeg', 'node', 'npm']) unlinkSync(join(f.bin, name));
  const report = audit(f);
  assert.equal(report.ok, false);
  for (const name of ['git', 'java', 'javac', 'ffmpeg', 'node', 'npm']) {
    assert.ok(report.checks.some(check => check.name === name && check.status === 'fail' && check.message.includes('PATH')));
  }
});

test('unsupported Java/compiler and Node versions fail the corresponding checks', t => {
  const f = fixture(t);
  f.tool('java', 'openjdk version "21.0.9"', '-version');
  f.tool('javac', 'javac 21.0.9', '-version');
  f.tool('node', 'v22.0.0');
  const report = audit(f);
  for (const name of ['java', 'javac', 'node baseline']) {
    assert.ok(report.checks.some(check => check.name === name && check.status === 'fail'));
  }
  f.tool('java', 'java version "26"', '-version');
  f.tool('javac', 'javac 26', '-version');
  f.tool('node', 'v26.0.0');
  assert.equal(audit(f).ok, true);
});

test('JAVA_HOME takes precedence over healthy PATH tools, including paths with spaces', t => {
  const f = fixture(t);
  f.env.JAVA_HOME = join(f.root, 'missing jdk');
  let report = audit({ ...f, profile: 'backend' });
  assert.equal(report.ok, false);
  assert.equal(report.checks.find(check => check.name === 'JAVA_HOME')?.status, 'fail');
  f.env.JAVA_HOME = f.root;
  assert.equal(audit({ ...f, profile: 'backend' }).ok, true);
  unlinkSync(join(f.bin, 'javac'));
  report = audit({ ...f, profile: 'backend' });
  assert.equal(report.checks.find(check => check.name === 'javac')?.status, 'fail');
});

test('PATH rejects missing, empty, and relative entries without echoing their values', t => {
  const f = fixture(t);
  for (const path of [undefined, '', `:${f.bin}`, `${f.bin}:`, `${f.bin}::/bin`, `${f.bin}:private-relative`]) {
    const report = audit({ ...f, profile: 'shell', env: { ...f.env, PATH: path } });
    assert.equal(report.checks.find(check => check.name === 'PATH')?.status, 'fail');
    assert.ok(!JSON.stringify(report).includes('private-relative'));
  }
});

test('SHELL is optional in CI but must be an executable absolute file when configured', t => {
  const f = fixture(t);
  const noShell = audit({ ...f, profile: 'shell', env: { ...f.env, SHELL: undefined } });
  assert.equal(noShell.ok, true);
  assert.equal(noShell.checks.find(check => check.name === 'SHELL')?.status, 'warn');
  for (const shell of ['sh', join(f.root, 'missing'), f.bin]) {
    assert.equal(audit({ ...f, profile: 'shell', env: { ...f.env, SHELL: shell } }).ok, false);
  }
});

test('missing wrapper files and executable permissions are detected without running Gradle', t => {
  const f = fixture(t);
  chmodSync(join(f.root, 'backend/gradlew'), 0o644);
  assert.equal(audit({ ...f, profile: 'backend' }).ok, false);
  chmodSync(join(f.root, 'backend/gradlew'), 0o755);
  unlinkSync(join(f.root, 'backend/gradle/wrapper/gradle-wrapper.jar'));
  assert.equal(audit({ ...f, profile: 'backend' }).checks.find(check => check.name === 'backend wrapper')?.status, 'fail');
});

test('an absent local Astro installation gives an npm ci remedy', t => {
  const f = fixture(t);
  unlinkSync(join(f.root, 'web/node_modules/astro/package.json'));
  const report = audit({ ...f, profile: 'web' });
  assert.equal(report.ok, false);
  assert.match(report.checks.find(check => check.name === 'web dependencies')?.message ?? '', /npm ci/);
});

test('broken, unparseable and excessively noisy tools cannot pass or leak their output', t => {
  const f = fixture(t);
  f.file(join(f.bin, 'git'), '#!/bin/sh\nprintf "secret-output"\nexit 1\n', 0o755);
  f.env.GOOGLE_API_KEY = 'secret-key';
  f.file(join(f.root, '.env'), 'this file must never be loaded');
  let report = audit({ ...f, profile: 'shell' });
  assert.equal(report.ok, false);
  assert.ok(!JSON.stringify(report).includes('secret-'));
  f.tool('git', 'unrecognizable output');
  assert.equal(audit({ ...f, profile: 'shell' }).ok, false);
  f.file(join(f.bin, 'git'), '#!/bin/sh\nwhile :; do printf "%01024d" 0; done\n', 0o755);
  report = audit({ ...f, profile: 'shell' });
  assert.equal(report.ok, false);
});

test('a hung version command is killed at the deadline', t => {
  const f = fixture(t);
  const hung = join(f.bin, 'hung');
  f.file(hung, '#!/bin/sh\nwhile :; do :; done\n', 0o755);
  assert.equal(probe(hung, [], f.env, 50), null);
});

test('CLI JSON is machine readable, uses exit codes, and works outside the checkout', t => {
  const f = fixture(t);
  const run = () => spawnSync(process.execPath, [script, '--profile', 'shell', '--json'], {
    cwd: f.root, env: f.env, encoding: 'utf8', timeout: 10_000,
  });
  let result = run();
  assert.equal(result.status, 0);
  assert.equal(result.stderr, '');
  assert.equal(JSON.parse(result.stdout).ok, true);
  unlinkSync(join(f.bin, 'git'));
  result = run();
  assert.equal(result.status, 1);
  assert.equal(JSON.parse(result.stdout).ok, false);
});

test('help and invalid arguments never run diagnostics', t => {
  const f = fixture(t);
  for (const args of [['--profile'], ['--profile', 'unknown'], ['--unknown'], ['web']]) {
    const result = spawnSync(process.execPath, [script, ...args], { env: f.env, encoding: 'utf8' });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /Usage:/);
    assert.equal(result.stdout, '');
  }
  const result = spawnSync(process.execPath, [script, '--help'], { env: f.env, encoding: 'utf8' });
  assert.equal(result.status, 0);
  assert.match(result.stdout, /Usage:/);
  assert.throws(() => audit({ profile: 'unknown' }), /Unknown diagnostic profile/);
});
