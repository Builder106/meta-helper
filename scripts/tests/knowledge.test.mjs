// @ts-check
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { buildIndex, loadIndex } from '../knowledge.mjs';

const script = fileURLToPath(new URL('../knowledge.mjs', import.meta.url));

/** @param {import('node:test').TestContext} t */
function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'meta-helper knowledge '));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(join(root, 'scripts'));
  mkdirSync(join(root, 'docs/runbooks'), { recursive: true });
  copyFileSync(script, join(root, 'scripts/knowledge.mjs'));
  /** @param {string} name @param {string} content */
  function runbook(name, content) {
    writeFileSync(join(root, 'docs/runbooks', name), content);
  }
  /** @param {string[]} args */
  function run(args) {
    return spawnSync(process.execPath, [join(root, 'scripts/knowledge.mjs'), ...args], {
      cwd: tmpdir(), encoding: 'utf8', timeout: 5000,
      // Lookup needs neither external tools nor network credentials.
      env: { PATH: '', HOME: root },
    });
  }
  return { root, runbook, run };
}

test('search intersects whole tokens within sections and normalizes case and Unicode', () => {
  const index = buildIndex([{ path: 'a.md', content: '# Setup\n## Java\nUse JAVA_HOME for the JDK.\n## Sound\nInstall ffmpeg.\n' }]);
  const [result] = index.search('ｊｄｋ java_HOME');
  assert.equal(result.title, 'Setup / Java');
  assert.equal(result.path, 'a.md');
  assert.equal(result.line, 2);
  assert.equal(result.snippet, 'Use JAVA_HOME for the JDK.');
  assert.deepEqual(index.search('jdk ffmpeg'), []);
  assert.deepEqual(index.search('java_home missing'), []);
  assert.deepEqual(index.search('ffm'), []);
  assert.deepEqual(index.search('jdk JDK'), index.search('jdk'));
});

test('title weights, body frequency, file path, and section lines determine stable ranking', () => {
  const index = buildIndex([
    { path: 'z.md', content: '# Notes\n## ffmpeg\nInstall it.\n' },
    { path: 'b.md', content: '# Notes\n## Other\nffmpeg ffmpeg\n' },
    { path: 'a.md', content: '# Notes\n## First\nffmpeg\n## Second\nffmpeg\n' },
  ]);
  assert.deepEqual(index.search('ffmpeg').map(result => [result.path, result.line, result.score]), [
    ['z.md', 2, 4], ['b.md', 2, 2], ['a.md', 2, 1], ['a.md', 4, 1],
  ]);
  assert.equal(index.search('ffmpeg', 1)[0].path, 'z.md');
});

test('CRLF, preambles, fenced code, and bounded snippets retain valid references', () => {
  const index = buildIndex([{ path: 'code.md', content: [
    '# Notes', 'Intro search.', '## Example', '```sh', '## This is code', 'echo search', '```',
    '## Long', `search ${'x'.repeat(400)}`,
  ].join('\r\n') }]);
  const results = index.search('search');
  assert.deepEqual(results.map(result => result.line), [1, 3, 8]);
  assert.equal(results[1].title, 'Notes / Example');
  assert.equal(results[1].snippet, 'echo search');
  assert.equal(results[2].snippet.length, 240);
  assert.deepEqual(buildIndex([]).search('anything'), []);
});

test('invalid queries and limits are rejected', () => {
  const index = buildIndex([]);
  for (const query of ['', '---', 'x'.repeat(257), Array.from({ length: 17 }, (_, i) => `term${i}`).join(' ')]) {
    assert.throws(() => index.search(query), /search terms/);
  }
  for (const limit of [0, 21, -1, 1.5, NaN]) assert.throws(() => index.search('jdk', limit), /Limit/);
});

test('loader limits itself to curated regular Markdown and refreshes on the next invocation', t => {
  const f = fixture(t);
  f.runbook('setup.md', '# Setup\n## Java\nSelect JDK 26.\n');
  f.runbook('ignored.txt', 'Secret unrelated content.');
  writeFileSync(join(f.root, '.env'), 'Secret unrelated content.');
  symlinkSync(join(f.root, '.env'), join(f.root, 'docs/runbooks/link.md'));
  const before = readdirSync(join(f.root, 'docs/runbooks'));
  const index = loadIndex(f.root);
  assert.equal(index.search('jdk')[0].path, 'docs/runbooks/setup.md');
  assert.deepEqual(index.search('secret'), []);
  f.runbook('setup.md', '# Setup\n## Audio\nInstall ffmpeg.\n');
  assert.equal(index.search('jdk').length, 1);
  assert.equal(index.search('ffmpeg').length, 0);
  assert.equal(loadIndex(f.root).search('ffmpeg').length, 1);
  assert.equal(loadIndex(f.root).search('jdk').length, 0);
  assert.deepEqual(readdirSync(join(f.root, 'docs/runbooks')), before);
});

test('missing, empty, oversized, and excessive runbook corpora fail clearly', t => {
  const f = fixture(t);
  assert.throws(() => loadIndex(f.root), /curated runbooks/);
  f.runbook('huge.md', 'x'.repeat(256 * 1024 + 1));
  assert.throws(() => loadIndex(f.root), /size limits/);
  rmSync(join(f.root, 'docs/runbooks/huge.md'));
  for (let i = 0; i < 17; i++) f.runbook(`${i}.md`, 'x'.repeat(256 * 1024));
  assert.throws(() => loadIndex(f.root), /size limits/);
  for (let i = 17; i < 129; i++) f.runbook(`${i}.md`, 'small');
  assert.throws(() => loadIndex(f.root), /curated runbooks/);
  rmSync(join(f.root, 'docs/runbooks'), { recursive: true });
  assert.throws(() => loadIndex(f.root), /ENOENT/);
});

test('real CLI runs offline from another directory and supports text, JSON, limits, and no matches', t => {
  const f = fixture(t);
  f.runbook('setup.md', '# Setup\n## Java\nSelect JDK 26.\n## Other\nJDK notes.\n');
  const text = f.run(['JDK']);
  assert.equal(text.status, 0, text.stderr);
  assert.match(text.stdout, /docs\/runbooks\/setup.md:2 — Setup \/ Java/);
  const json = f.run(['--json', '--limit', '1', 'jdk']);
  assert.equal(json.status, 0, json.stderr);
  const report = JSON.parse(json.stdout);
  assert.equal(report.query, 'jdk');
  assert.equal(report.results.length, 1);
  assert.equal(report.results[0].line, 2);
  assert.equal(typeof report.results[0].score, 'number');
  assert.deepEqual(JSON.parse(f.run(['--json', 'missing']).stdout).results, []);
  assert.match(f.run(['missing']).stdout, /No matching runbook sections/);
  assert.equal(f.run(['--', '--jdk']).status, 0);
  assert.deepEqual(readdirSync(f.root).sort(), ['docs', 'scripts']);
});

test('CLI usage errors exit 2 and missing corpus exits 1 without a stack trace', t => {
  const f = fixture(t);
  assert.equal(f.run(['--help']).status, 0);
  for (const args of [[], ['--wat'], ['--limit'], ['--limit', '21', 'jdk'], ['--limit', '0', 'jdk'],
    ['--limit', '1.5', 'jdk'], ['--json', '--json', 'jdk'], ['--limit', '1', '--limit', '2', 'jdk'],
    ['!!!'], ['x'.repeat(257)]]) {
    const result = f.run(args);
    assert.equal(result.status, 2, JSON.stringify(args));
    assert.match(result.stderr, /Usage:/);
  }
  const failure = f.run(['jdk']);
  assert.equal(failure.status, 1);
  assert.match(failure.stderr, /Knowledge lookup failed.*docs\/runbooks/);
  assert.doesNotMatch(failure.stderr, /at main|Error:/);
});

test('the shipped corpus retrieves real procedures with correct heading references', () => {
  const index = loadIndex();
  for (const query of ['java jdk', 'ffmpeg', 'coverage', 'speech configuration', 'gallery polling']) {
    const results = index.search(query);
    assert.ok(results.length > 0, query);
    for (const result of results) {
      const content = readFileSync(new URL(`../../${result.path}`, import.meta.url), 'utf8');
      const line = content.split('\n')[result.line - 1];
      assert.ok(line.startsWith('#'), `${result.path}:${result.line}`);
      assert.ok(result.title.endsWith(line.replace(/^#+ /u, '')));
    }
  }
});
