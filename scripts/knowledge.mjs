// @ts-check
import { closeSync, constants, fstatSync, openSync, readSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repository = dirname(dirname(fileURLToPath(import.meta.url)));
const usage = 'Usage: node scripts/knowledge.mjs [--json] [--limit 1..20] [--] <query...>';
const maxFileBytes = 256 * 1024;

/** @typedef {{ path: string, content: string }} Document */
/** @typedef {{ path: string, line: number, title: string, snippet: string, score: number }} SearchResult */
/** @typedef {{ path: string, line: number, title: string, body: string }} Section */

/** @param {string} text */
function tokens(text) {
  return text.normalize('NFKC').toLowerCase().match(/[\p{L}\p{N}]+/gu) ?? [];
}

/** @param {string} query */
function queryTerms(query) {
  if (query.length > 256) throw new Error('Use 1..16 search terms and at most 256 characters.');
  const terms = [...new Set(tokens(query))];
  if (terms.length === 0 || terms.length > 16) {
    throw new Error('Use 1..16 search terms and at most 256 characters.');
  }
  return terms;
}

/**
 * Build once per process; queries intersect postings rather than rescan documents.
 * Runbooks use a single H1 title and H2 sections; fenced code stays in its section.
 * @param {Document[]} documents
 */
export function buildIndex(documents) {
  /** @type {Section[]} */
  const sections = [];
  /** @type {Map<string, Map<number, number>>} */
  const postings = new Map();
  for (const document of documents) {
    let title = document.path;
    /** @type {Section} */
    let section = { path: document.path, line: 1, title, body: '' };
    let fence = '';
    /** @param {Section} value */
    function add(value) {
      if (!value.body.trim()) return;
      const id = sections.length;
      sections.push(value);
      /** @type {[string, number][]} */
      const fields = [[value.title, 4], [value.body, 1]];
      for (const [text, weight] of fields) {
        for (const term of tokens(text)) {
          let posting = postings.get(term);
          if (!posting) {
            posting = new Map();
            postings.set(term, posting);
          }
          posting.set(id, (posting.get(id) ?? 0) + weight);
        }
      }
    }
    for (const [index, line] of document.content.split(/\r?\n/).entries()) {
      const marker = line.match(/^\s{0,3}(`{3,}|~{3,})/u)?.[1];
      if (marker) {
        if (!fence) fence = marker;
        else if (marker[0] === fence[0] && marker.length >= fence.length) fence = '';
      } else if (!fence && line.startsWith('# ')) {
        title = line.slice(2).trim();
        section.title = title;
        continue;
      } else if (!fence && line.startsWith('## ')) {
        add(section);
        section = { path: document.path, line: index + 1, title: `${title} / ${line.slice(3).trim()}`, body: '' };
        continue;
      }
      section.body += `${line}\n`;
    }
    add(section);
  }

  /** @param {string} query @param {number} [limit] @returns {SearchResult[]} */
  function search(query, limit = 5) {
    const terms = queryTerms(query);
    if (!Number.isInteger(limit) || limit < 1 || limit > 20) throw new Error('Limit must be 1..20.');
    /** @type {Map<number, number>[]} */
    const lists = terms.map(term => postings.get(term) ?? new Map()).sort((a, b) => a.size - b.size);
    /** @type {SearchResult[]} */
    const results = [];
    for (const id of lists[0].keys()) {
      if (!lists.every(list => list.has(id))) continue;
      const section = sections[id];
      // Prefer a matching body line; title-only matches use the first content line.
      const lines = section.body.split('\n').map(line => line.trim()).filter(line => line && !/^(`{3,}|~{3,})/u.test(line));
      const excerpt = lines.find(line => tokens(line).some(term => terms.includes(term))) ?? lines[0] ?? '';
      results.push({
        path: section.path, line: section.line, title: section.title,
        snippet: excerpt.replace(/\s+/gu, ' ').slice(0, 240),
        score: lists.reduce((score, list) => score + (list.get(id) ?? 0), 0),
      });
    }
    return results.sort((a, b) => b.score - a.score
      || (a.path < b.path ? -1 : a.path > b.path ? 1 : 0) || a.line - b.line).slice(0, limit);
  }
  return { search };
}

/**
 * Only curated Markdown files, never the rest of the checkout or a remote source.
 * Bound reads and skip symlinks; no generated index, dependencies, or daemon.
 * @param {string} [root]
 */
export function loadIndex(root = repository) {
  const directory = join(root, 'docs/runbooks');
  const files = readdirSync(directory, { withFileTypes: true })
    .filter(entry => entry.isFile() && entry.name.endsWith('.md')).map(entry => entry.name).sort();
  if (files.length === 0 || files.length > 128) throw new Error('Expected 1..128 curated runbooks.');
  let total = 0;
  const documents = files.map(file => {
    const descriptor = openSync(join(directory, file), constants.O_RDONLY | constants.O_NOFOLLOW);
    try {
      const stat = fstatSync(descriptor);
      if (!stat.isFile() || stat.size > maxFileBytes) {
        throw new Error('Runbook corpus exceeds size limits.');
      }
      // A fixed buffer also bounds reads if an editor grows a file after fstat.
      const buffer = Buffer.alloc(maxFileBytes + 1);
      let size = 0;
      while (size < buffer.length) {
        const count = readSync(descriptor, buffer, size, buffer.length - size, null);
        if (!count) break;
        size += count;
      }
      total += size;
      if (size > maxFileBytes || total > 4 * 1024 * 1024) throw new Error('Runbook corpus exceeds size limits.');
      return { path: `docs/runbooks/${file}`, content: buffer.toString('utf8', 0, size) };
    } finally {
      closeSync(descriptor);
    }
  });
  return buildIndex(documents);
}

/** @param {string[]} args */
export function main(args) {
  if (args.length === 1 && (args[0] === '--help' || args[0] === '-h')) {
    console.log(usage);
    return 0;
  }
  let json = false;
  let limit = 5;
  let limitSeen = false;
  let positional = false;
  /** @type {string[]} */
  const words = [];
  try {
    for (let index = 0; index < args.length; index++) {
      const arg = args[index];
      if (!positional && arg === '--') positional = true;
      else if (!positional && arg === '--json' && !json) json = true;
      else if (!positional && arg === '--limit' && !limitSeen && /^(?:[1-9]|1\d|20)$/u.test(args[index + 1] ?? '')) {
        limit = Number(args[++index]);
        limitSeen = true;
      } else if (!positional && arg.startsWith('-')) throw new Error('Unknown option.');
      else words.push(arg);
    }
    queryTerms(words.join(' '));
  } catch {
    console.error(usage);
    return 2;
  }
  const query = words.join(' ');
  try {
    const results = loadIndex().search(query, limit);
    if (json) console.log(JSON.stringify({ query, results }, null, 2));
    else if (!results.length) console.log('No matching runbook sections.');
    else for (const result of results) {
      console.log(`${result.path}:${result.line} — ${result.title}\n  ${result.snippet}`);
    }
    return 0;
  } catch {
    console.error('Knowledge lookup failed. Check that docs/runbooks contains readable Markdown files within the documented limits.');
    return 1;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = main(process.argv.slice(2));
}
