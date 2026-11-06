// @ts-check
import { closeSync, constants, fstatSync, openSync, readFileSync, readSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const maxFileBytes = 10 * 1024 * 1024; // 10 MiB
const maxTotalLines = 100_000;
const usage = 'Usage: node scripts/logs.mjs [--json] [--severity critical|error|warn|all] [--category <name>] [--fail-on critical|error|warn] [--limit 1..100] [--pattern <regex>] [--] [files...]';

/**
 * @typedef {'critical' | 'error' | 'warning' | 'info'} Severity
 */

/**
 * @typedef {{
 *   id: string,
 *   category: string,
 *   severity: Severity,
 *   pattern: RegExp,
 *   description: string,
 *   remedy: string,
 * }} AnomalyRule
 */

/**
 * @typedef {{
 *   file: string,
 *   lineNumber: number,
 *   timestamp: string | null,
 *   level: string | null,
 *   message: string,
 *   raw: string,
 *   stackLines: string[],
 * }} ParsedLogEntry
 */

/**
 * @typedef {{
 *   ruleId: string,
 *   category: string,
 *   severity: Severity,
 *   pattern: string,
 *   description: string,
 *   remedy: string,
 *   count: number,
 *   file: string,
 *   firstLine: number,
 *   lastLine: number,
 *   lines: number[],
 *   sample: string,
 * }} AnomalyGroup
 */

/**
 * @typedef {{
 *   totalLines: number,
 *   totalAnomalies: number,
 *   uniqueAnomalies: number,
 *   countsBySeverity: Record<Severity, number>,
 *   countsByCategory: Record<string, number>,
 * }} LogSummary
 */

/**
 * @typedef {{
 *   summary: LogSummary,
 *   anomalies: AnomalyGroup[],
 * }} ClassificationResult
 */

/** @type {Record<Severity, number>} */
export const SEVERITY_LEVELS = {
  critical: 3,
  error: 2,
  warning: 1,
  info: 0,
};

/** @type {AnomalyRule[]} */
export const DEFAULT_RULES = [
  {
    id: 'CRITICAL_OOM',
    category: 'RESOURCE_EXHAUSTION',
    severity: 'critical',
    pattern: /\b(?:java\.lang\.)?OutOfMemoryError\b|\bOOMKilled\b|\bruntime:\s*out\s+of\s+memory\b|\bheap\s+out\s+of\s+memory\b/i,
    description: 'Process exhausted memory or exceeded container memory limit',
    remedy: 'Increase memory limits (-Xmx, container memory), inspect heap dumps, or profile memory allocations.',
  },
  {
    id: 'CRITICAL_FATAL_PANIC',
    category: 'FATAL_CRASH',
    severity: 'critical',
    pattern: /\bSIGSEGV\b|\bsegmentation\s+fault\b|\bSIGBUS\b|\bcore\s+dumped\b|\bpanic:\s+[^\n]+|\bFATAL(?:\s+ERROR)?:\s+[^\n]+/i,
    description: 'Fatal process crash or unhandled kernel/system panic',
    remedy: 'Inspect native crash dumps, system signals, or unhandled panics.',
  },
  {
    id: 'SERVICE_UNAVAILABLE',
    category: 'SERVICE_UNAVAILABLE',
    severity: 'error',
    pattern: /\b503\s+(?:Service\s+Unavailable|UNAVAILABLE)\b|\bHTTP\s*503\b|\bstatus(?:_code)?\s*[:=]\s*503\b|\b502\s+Bad\s+Gateway\b|\bHTTP\s*502\b|\bstatus(?:_code)?\s*[:=]\s*502\b|\b504\s+Gateway\s+Timeout\b|\bHTTP\s*504\b|\bstatus(?:_code)?\s*[:=]\s*504\b|\btransient\s+503\b|\b503\s+high\s+demand\b/i,
    description: 'Upstream service unavailable (502/503/504) or experiencing high demand',
    remedy: 'Upstream service is degraded or warming up. Verify endpoint availability and retry with backoff.',
  },
  {
    id: 'RATE_LIMIT_EXHAUSTED',
    category: 'RATE_LIMIT_EXHAUSTED',
    severity: 'error',
    pattern: /\b429\s+Too\s+Many\s+Requests\b|\bHTTP\s*429\b|\bstatus(?:_code)?\s*[:=]\s*429\b|\bRESOURCE_EXHAUSTED\b|\brate\s+limit(?:ed|\s+exceeded)\b|\bquota\s+exceeded\b|\b429\s+limit:\s*0\b/i,
    description: 'API rate limit or tier quota exhausted',
    remedy: 'Check API usage quota, billing tier requirements, or throttle request concurrency.',
  },
  {
    id: 'NETWORK_TIMEOUT',
    category: 'NETWORK_TIMEOUT',
    severity: 'error',
    pattern: /\b(?:Socket|Connect|Read)?TimeoutException\b|\bETIMEDOUT\b|\bECONNREFUSED\b|\bECONNRESET\b|\bEHOSTUNREACH\b|\bconnection\s+(?:timed\s+out|refused|reset\s+by\s+peer)\b|\bdeadline\s+exceeded\b/i,
    description: 'Network connection timeout, refusal, or unreachability',
    remedy: 'Check target endpoint connectivity, DNS resolution, port bindings, and network security rules.',
  },
  {
    id: 'AUTH_PERMISSION',
    category: 'AUTH_PERMISSION',
    severity: 'error',
    pattern: /\b401\s+Unauthorized\b|\bHTTP\s*401\b|\bstatus(?:_code)?\s*[:=]\s*401\b|\b403\s+Forbidden\b|\bHTTP\s*403\b|\bstatus(?:_code)?\s*[:=]\s*403\b|\bPERMISSION_DENIED\b|\bAccessDeniedException\b|\bAuthenticationException\b|\binvalid\s+(?:api\s+key|credentials|token)\b/i,
    description: 'Authentication or access permission denied',
    remedy: 'Verify authentication credentials, API keys, and environment variables.',
  },
  {
    id: 'RUNTIME_EXCEPTION',
    category: 'RUNTIME_EXCEPTION',
    severity: 'error',
    pattern: /\b(?:[a-zA-Z_$][a-zA-Z0-9_$]*\.)*(?:NullPointer|IllegalState|IllegalArgument|IndexOutOfBounds|ClassCast|UnsupportedOperation|NoSuchElement)Exception\b|\bUnhandled(?:Promise)?Rejection\b|\bUncaught\s+(?:Type|Reference|Range|Syntax)Error\b|\bException\s+in\s+thread\s+"[^"]+"\s+([a-zA-Z0-9_$.]+)/i,
    description: 'Uncaught runtime exception or language runtime error',
    remedy: 'Inspect the stack trace for null references, missing values, or invalid arguments.',
  },
  {
    id: 'SUBPROCESS_FAILURE',
    category: 'SUBPROCESS_FAILURE',
    severity: 'error',
    pattern: /\bffmpeg\b.*(?:exited\s+with\s+code\s+[1-9]|Conversion\s+failed|Error\s+opening)|spawn\s+(?:ENOENT|EACCES)\b|\bprocess\s+exited\s+with\s+code\s+[1-9]\b/i,
    description: 'Child process or CLI pipeline failure',
    remedy: 'Verify required CLI tools (e.g. ffmpeg) exist on PATH and check input file arguments.',
  },
  {
    id: 'BUILD_FAILURE',
    category: 'BUILD_FAILURE',
    severity: 'error',
    pattern: /\bExecution failed for task\s+'[^']+'\b|\bBUILD FAILED\b|\bCompilation failure\b|\bRule violated for bundle.*coverage\b|\bjacocoTestCoverageVerification\b/i,
    description: 'Build, compilation, or test coverage verification failure',
    remedy: 'Fix compiler errors, resolve failed unit tests, or increase test coverage to meet threshold.',
  },
  {
    id: 'RESOURCE_EXHAUSTION',
    category: 'RESOURCE_EXHAUSTION',
    severity: 'error',
    pattern: /\bNo space left on device\b|\bENOSPC\b|\bToo many open files\b|\bEMFILE\b|\bENFILE\b/i,
    description: 'System resource exhaustion (disk or file descriptors)',
    remedy: 'Free disk space in target directory or increase system ulimit / max open file descriptors.',
  },
  {
    id: 'DEPRECATION_WARNING',
    category: 'DEPRECATION_WARNING',
    severity: 'warning',
    pattern: /\b(?:is\s+deprecated|has\s+been\s+deprecated|deprecated\s+API)\b.*(?:scheduled\s+for\s+removal|will\s+be\s+removed)?|\bThe\s+following\s+features\s+are\s+deprecated\b/i,
    description: 'Deprecated API usage or scheduled feature removal',
    remedy: 'Migrate to the recommended non-deprecated API before upcoming platform upgrades.',
  },
  {
    id: 'GENERIC_ERROR',
    category: 'APPLICATION_ERROR',
    severity: 'error',
    pattern: /(?:^|[\s\[])(?:ERROR|FATAL)(?:[\s\]:]|$)|(?:level|status)=ERROR\b/i,
    description: 'Generic application ERROR log',
    remedy: 'Examine preceding and following log entries for diagnostic error details.',
  },
  {
    id: 'GENERIC_WARNING',
    category: 'APPLICATION_WARNING',
    severity: 'warning',
    pattern: /(?:^|[\s\[])WARN(?:ING)?(?:[\s\]:]|$)|(?:level|status)=WARN(?:ING)?\b/i,
    description: 'Generic application WARN log',
    remedy: 'Monitor condition to ensure non-blocking warning does not escalate into failure.',
  },
];

/**
 * Normalize severity string.
 * @param {string} val
 * @returns {Severity | 'all'}
 */
export function parseSeverity(val) {
  const norm = val.toLowerCase();
  if (norm === 'warn') return 'warning';
  if (norm === 'warning' || norm === 'error' || norm === 'critical' || norm === 'all' || norm === 'info') {
    return norm;
  }
  throw new Error(`Invalid severity: ${val}`);
}

/**
 * Check if a raw line represents a new structured log header.
 * @param {string} line
 * @returns {boolean}
 */
function isNewLogLineHeader(line) {
  return /^\d{4}-\d{2}-\d{2}/.test(line) ||
    /^\d{2}:\d{2}:\d{2}/.test(line) ||
    /^[A-Z][a-z]{2}\s+\d+\s+\d{2}:\d{2}:\d{2}/.test(line) ||
    /^\[(FATAL|CRITICAL|ERROR|WARN(?:ING)?|INFO|DEBUG|TRACE)\]/i.test(line) ||
    /^(?:FATAL|CRITICAL|ERROR|WARN(?:ING)?|INFO|DEBUG|TRACE):/i.test(line) ||
    /^Execution failed for task/i.test(line) ||
    /^>\s*Task\s+/i.test(line);
}

/**
 * Extract timestamp, level, and message content from a log line.
 * @param {string} line
 * @returns {{ timestamp: string | null, level: string | null, message: string }}
 */
function extractLogMeta(line) {
  const tsMatch = line.match(/\b\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?\b/) ||
    line.match(/\b[A-Z][a-z]{2}\s+\d+\s+\d{2}:\d{2}:\d{2}\b/);
  const timestamp = tsMatch ? tsMatch[0] : null;

  const levelMatch = line.match(/\b(FATAL|CRITICAL|ERROR|WARN(?:ING)?|INFO|DEBUG|TRACE)\b/i);
  const level = levelMatch ? levelMatch[1].toUpperCase() : null;

  let message = line;
  if (timestamp) message = message.replace(timestamp, '');
  if (level) message = message.replace(new RegExp(`\\b${level}\\b`, 'i'), '');
  message = message.replace(/^\s*[-:—\s]+/, '').trim();

  return { timestamp, level, message: message || line };
}

/**
 * Parses raw text into structured log entries, grouping multi-line stack traces.
 * @param {string} text
 * @param {string} [fileName]
 * @param {number} [startLineNumber]
 * @returns {ParsedLogEntry[]}
 */
export function parseLogEntries(text, fileName = 'stdin', startLineNumber = 1) {
  const lines = text.split(/\r?\n/);
  /** @type {ParsedLogEntry[]} */
  const entries = [];
  /** @type {ParsedLogEntry | null} */
  let current = null;

  for (let i = 0; i < lines.length; i++) {
    const rawLine = lines[i];
    const lineNumber = startLineNumber + i;
    const trimmed = rawLine.trim();
    if (!trimmed) continue;

    const isContinuation = current !== null && (
      /^\s+(?:at\s+|Caused by:|\.\.\.\s*\d+\s+more)/.test(rawLine) ||
      /^(?:Caused by:|\.\.\.\s*\d+\s+more)/.test(trimmed) ||
      (current.level && /^(?:java\.[a-z.]+|org\.[a-z.]+|com\.[a-z.]+|[A-Za-z]+Exception:)/.test(trimmed)) ||
      ((rawLine.startsWith('\t') || rawLine.startsWith('    ')) && !isNewLogLineHeader(rawLine))
    );

    if (isContinuation && current) {
      current.stackLines.push(rawLine);
      continue;
    }

    const meta = extractLogMeta(rawLine);
    current = {
      file: fileName,
      lineNumber,
      timestamp: meta.timestamp,
      level: meta.level,
      message: meta.message,
      raw: rawLine,
      stackLines: [],
    };
    entries.push(current);
  }

  return entries;
}

/**
 * Classify a parsed log entry against an array of rules.
 * @param {ParsedLogEntry} entry
 * @param {AnomalyRule[]} rules
 * @returns {{ rule: AnomalyRule, matchedText: string } | null}
 */
export function classifyEntry(entry, rules) {
  const fullText = entry.stackLines.length > 0
    ? `${entry.raw}\n${entry.stackLines.join('\n')}`
    : entry.raw;

  for (const rule of rules) {
    if (rule.pattern.test(fullText)) {
      return { rule, matchedText: fullText };
    }
  }
  return null;
}

/**
 * Creates a normalized signature string to group repeating anomaly occurrences.
 * @param {AnomalyRule} rule
 * @param {ParsedLogEntry} entry
 * @returns {string}
 */
export function normalizeSignature(rule, entry) {
  let text = entry.message || entry.raw;
  const excLine = entry.stackLines.find(l => /(?:Exception|Error):/i.test(l));
  if (excLine) {
    text = `${text} ${excLine.trim()}`;
  }

  text = text.replace(/\b\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?\b/g, '');
  text = text.replace(/\b[A-Za-z]{3}\s+\d+\s+\d{2}:\d{2}:\d{2}\b/g, '');
  text = text.replace(/\b\d{2}:\d{2}:\d{2}(?:\.\d+)?\b/g, '');
  text = text.replace(/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/g, '');
  text = text.replace(/\b0x[0-9a-fA-F]+\b/g, '');
  text = text.replace(/@[0-9a-fA-F]{6,}\b/g, '');
  text = text.replace(/\b\d{4,6}\b/g, '');
  text = text.replace(/---\s*\[[^\]]+\]/g, '');
  text = text.replace(/\s+/g, ' ').trim();

  return `${rule.id}::${entry.file}::${text.slice(0, 120)}`;
}

/**
 * Creates a clean sample string for an anomaly incident.
 * @param {ParsedLogEntry} entry
 * @returns {string}
 */
function createSample(entry) {
  const excLine = entry.stackLines.find(l => /(?:Exception|Error):/i.test(l));
  if (excLine) {
    const combined = `${entry.raw.trim()} [${excLine.trim()}]`;
    return combined.replace(/\s+/gu, ' ').slice(0, 240);
  }
  return entry.raw.trim().replace(/\s+/gu, ' ').slice(0, 240);
}

/**
 * Classifies log text or pre-parsed entries into grouped anomalies.
 * @param {string | ParsedLogEntry[]} input
 * @param {{
 *   fileName?: string,
 *   rules?: AnomalyRule[],
 *   minSeverity?: Severity | 'all',
 *   category?: string,
 *   limit?: number,
 *   customPattern?: RegExp | null,
 * }} [options]
 * @returns {ClassificationResult}
 */
export function classifyLog(input, options = {}) {
  const {
    fileName = 'stdin',
    rules = DEFAULT_RULES,
    minSeverity = 'warning',
    category,
    limit = 20,
    customPattern = null,
  } = options;

  let effectiveRules = [...rules];
  if (customPattern) {
    /** @type {AnomalyRule} */
    const customRule = {
      id: 'CUSTOM_PATTERN',
      category: 'CUSTOM',
      severity: 'error',
      pattern: customPattern,
      description: `Custom pattern: ${customPattern.source}`,
      remedy: 'Review matched lines for user-specified anomaly pattern.',
    };
    effectiveRules = [customRule, ...effectiveRules];
  }

  const entries = typeof input === 'string' ? parseLogEntries(input, fileName) : input;
  const totalLines = typeof input === 'string' ? input.split(/\r?\n/).length : entries.length;

  /** @type {Map<string, AnomalyGroup>} */
  const groups = new Map();
  /** @type {Record<Severity, number>} */
  const countsBySeverity = { critical: 0, error: 0, warning: 0, info: 0 };
  /** @type {Record<string, number>} */
  const countsByCategory = {};
  let totalAnomalies = 0;

  for (const entry of entries) {
    const classification = classifyEntry(entry, effectiveRules);
    if (!classification) continue;

    const { rule } = classification;
    const ruleSeverity = rule.severity;

    // Filter by category if requested
    if (category && rule.category.toLowerCase() !== category.toLowerCase()) {
      continue;
    }

    // Filter by minSeverity
    if (minSeverity !== 'all' && SEVERITY_LEVELS[ruleSeverity] < SEVERITY_LEVELS[minSeverity]) {
      continue;
    }

    totalAnomalies++;
    countsBySeverity[ruleSeverity] = (countsBySeverity[ruleSeverity] || 0) + 1;
    countsByCategory[rule.category] = (countsByCategory[rule.category] || 0) + 1;

    const sig = normalizeSignature(rule, entry);
    let group = groups.get(sig);
    if (!group) {
      group = {
        ruleId: rule.id,
        category: rule.category,
        severity: rule.severity,
        pattern: rule.pattern.source,
        description: rule.description,
        remedy: rule.remedy,
        count: 0,
        file: entry.file,
        firstLine: entry.lineNumber,
        lastLine: entry.lineNumber,
        lines: [],
        sample: createSample(entry),
      };
      groups.set(sig, group);
    }

    group.count++;
    group.lastLine = entry.lineNumber;
    if (group.lines.length < 20) {
      group.lines.push(entry.lineNumber);
    }
  }

  // Sort groups: critical > error > warning > info, then count descending, then firstLine ascending
  const sortedAnomalies = [...groups.values()].sort((a, b) => {
    const sevDiff = SEVERITY_LEVELS[b.severity] - SEVERITY_LEVELS[a.severity];
    if (sevDiff !== 0) return sevDiff;
    const countDiff = b.count - a.count;
    if (countDiff !== 0) return countDiff;
    return a.firstLine - b.firstLine;
  }).slice(0, limit);

  return {
    summary: {
      totalLines,
      totalAnomalies,
      uniqueAnomalies: groups.size,
      countsBySeverity,
      countsByCategory,
    },
    anomalies: sortedAnomalies,
  };
}

/**
 * Format classification result as human-readable report.
 * @param {ClassificationResult} result
 * @returns {string}
 */
export function formatReport(result) {
  const { summary, anomalies } = result;
  if (summary.totalAnomalies === 0) {
    return `No anomalies detected in log output (${summary.totalLines} lines parsed).`;
  }

  const lines = [
    `Diagnostic Log Anomaly Report (${summary.totalLines} lines parsed)`,
    `Found ${summary.totalAnomalies} anomalies (${summary.countsBySeverity.critical} critical, ${summary.countsBySeverity.error} error, ${summary.countsBySeverity.warning} warning) across ${summary.uniqueAnomalies} unique signatures:`,
    '',
  ];

  for (const anomaly of anomalies) {
    const loc = anomaly.firstLine === anomaly.lastLine
      ? `${anomaly.file}:${anomaly.firstLine}`
      : `${anomaly.file}:${anomaly.firstLine}..${anomaly.lastLine}`;
    lines.push(`[${anomaly.severity.toUpperCase()}] ${anomaly.category} (x${anomaly.count}) — ${loc}`);
    lines.push(`  Rule: ${anomaly.ruleId} (${anomaly.description})`);
    lines.push(`  Sample: ${anomaly.sample}`);
    lines.push(`  Remedy: ${anomaly.remedy}`);
    lines.push('');
  }

  return lines.join('\n').trimEnd();
}

/**
 * Safely read a log file within bounds, refusing symlinks and non-files.
 * @param {string} filePath
 * @returns {string}
 */
export function readLogFile(filePath) {
  const descriptor = openSync(filePath, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const stat = fstatSync(descriptor);
    if (!stat.isFile()) {
      throw new Error(`Path is not a regular file: ${filePath}`);
    }
    if (stat.size > maxFileBytes) {
      throw new Error(`Log file exceeds size limit of 10 MiB: ${filePath}`);
    }
    const buffer = Buffer.alloc(stat.size);
    let bytesRead = 0;
    while (bytesRead < stat.size) {
      const count = readSync(descriptor, buffer, bytesRead, stat.size - bytesRead, null);
      if (count === 0) break;
      bytesRead += count;
    }
    return buffer.toString('utf8', 0, bytesRead);
  } finally {
    closeSync(descriptor);
  }
}

/**
 * Safely read standard input within bounded size.
 * @returns {string}
 */
export function readStdin() {
  const buffer = readFileSync(0);
  if (buffer.length > maxFileBytes) {
    throw new Error('Standard input exceeds maximum size limit of 10 MiB.');
  }
  return buffer.toString('utf8');
}

/**
 * CLI main function.
 * @param {string[]} args
 * @returns {number}
 */
export function main(args) {
  if (args.length === 1 && (args[0] === '--help' || args[0] === '-h')) {
    console.log(usage);
    return 0;
  }

  let json = false;
  /** @type {Severity | 'all'} */
  let minSeverity = 'warning';
  /** @type {string | undefined} */
  let category;
  /** @type {Severity | null} */
  let failOn = null;
  let limit = 20;
  /** @type {RegExp | null} */
  let customPattern = null;

  /** @type {string[]} */
  const filePaths = [];
  let positional = false;

  try {
    for (let i = 0; i < args.length; i++) {
      const arg = args[i];
      if (!positional && arg === '--') {
        positional = true;
      } else if (!positional && arg === '--json') {
        json = true;
      } else if (!positional && arg === '--severity') {
        const next = args[++i];
        if (!next) throw new Error('Missing argument for --severity');
        minSeverity = parseSeverity(next);
      } else if (!positional && arg === '--category') {
        category = args[++i];
        if (!category) throw new Error('Missing argument for --category');
      } else if (!positional && arg === '--fail-on') {
        const next = args[++i];
        if (!next) throw new Error('Missing argument for --fail-on');
        const parsed = parseSeverity(next);
        if (parsed === 'all' || parsed === 'info') throw new Error('Invalid --fail-on threshold');
        failOn = parsed;
      } else if (!positional && arg === '--limit') {
        const next = args[++i];
        if (!next || !/^(?:[1-9]\d?|100)$/.test(next)) throw new Error('Limit must be an integer from 1 to 100.');
        limit = Number(next);
      } else if (!positional && arg === '--pattern') {
        const next = args[++i];
        if (!next) throw new Error('Missing argument for --pattern');
        if (next.length > 256) throw new Error('Pattern must not exceed 256 characters.');
        customPattern = new RegExp(next, 'i');
      } else if (!positional && arg.startsWith('-') && arg !== '-') {
        throw new Error(`Unknown option: ${arg}`);
      } else {
        filePaths.push(arg);
      }
    }
  } catch {
    console.error(usage);
    return 2;
  }

  // If no files and stdin is a TTY, print usage and exit 2
  if (filePaths.length === 0 && Boolean(process.stdin.isTTY)) {
    console.error(usage);
    return 2;
  }

  /** @type {ParsedLogEntry[]} */
  const allEntries = [];
  let totalRawLines = 0;

  try {
    if (filePaths.length === 0 || (filePaths.length === 1 && filePaths[0] === '-')) {
      const content = readStdin();
      totalRawLines += content.split(/\r?\n/).length;
      allEntries.push(...parseLogEntries(content, 'stdin'));
    } else {
      for (const filePath of filePaths) {
        if (filePath === '-') {
          const content = readStdin();
          totalRawLines += content.split(/\r?\n/).length;
          allEntries.push(...parseLogEntries(content, 'stdin'));
        } else {
          const resolved = resolve(filePath);
          const content = readLogFile(resolved);
          totalRawLines += content.split(/\r?\n/).length;
          allEntries.push(...parseLogEntries(content, filePath));
        }
      }
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error(`Log read failure: ${message}`);
    return 1;
  }

  if (totalRawLines > maxTotalLines) {
    console.error(`Log input exceeds maximum of ${maxTotalLines} lines.`);
    return 1;
  }

  const result = classifyLog(allEntries, {
    minSeverity,
    category,
    limit,
    customPattern,
  });
  result.summary.totalLines = totalRawLines;

  if (json) {
    console.log(JSON.stringify(result, null, 2));
  } else {
    console.log(formatReport(result));
  }

  if (failOn) {
    const thresholdLevel = SEVERITY_LEVELS[failOn];
    const hasViolation = Object.entries(result.summary.countsBySeverity).some(
      ([sev, count]) => count > 0 && SEVERITY_LEVELS[/** @type {Severity} */ (sev)] >= thresholdLevel
    );
    if (hasViolation) {
      return 1;
    }
  }

  return 0;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = main(process.argv.slice(2));
}
