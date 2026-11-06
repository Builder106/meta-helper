// @ts-check
import { accessSync, constants, statSync } from 'node:fs';
import { delimiter, dirname, isAbsolute, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const repository = dirname(dirname(fileURLToPath(import.meta.url)));
const profiles = ['all', 'shell', 'backend', 'web'];

/** @typedef {{ name: string, status: 'pass' | 'fail' | 'warn', message: string }} Check */

/** @param {string} path @param {number} mode */
function accessibleFile(path, mode) {
  try {
    accessSync(path, mode);
    return statSync(path).isFile();
  } catch {
    return false;
  }
}

/**
 * Run only a version query, without a shell or unbounded output/runtime.
 * Raw output stays internal so a broken tool cannot echo secrets into reports.
 * @param {string} command
 * @param {string[]} args
 * @param {NodeJS.ProcessEnv} env
 * @param {number} [timeout]
 */
export function probe(command, args, env, timeout = 5000) {
  const result = spawnSync(command, args, {
    env, encoding: 'utf8', timeout, maxBuffer: 64 * 1024,
    killSignal: 'SIGKILL', windowsHide: true,
  });
  return result.error || result.status !== 0
    ? null
    : `${result.stdout}\n${result.stderr}`;
}

/**
 * Audit local prerequisites only; never install, source shell files, or run Gradle.
 * @param {{ profile?: string, env?: NodeJS.ProcessEnv, root?: string }} [options]
 */
export function audit({ profile = 'all', env = process.env, root = repository } = {}) {
  if (!profiles.includes(profile)) throw new Error('Unknown diagnostic profile');
  /** @type {Check[]} */
  const checks = [];
  /** @param {string} name @param {boolean} ok @param {string} success @param {string} remedy */
  function check(name, ok, success, remedy) {
    checks.push({ name, status: ok ? 'pass' : 'fail', message: ok ? success : remedy });
  }
  /** @param {string} name @param {string} command @param {string[]} args @param {RegExp} pattern @param {string} remedy */
  function version(name, command, args, pattern, remedy) {
    const output = probe(command, args, env);
    check(name, output !== null && pattern.test(output), 'Version query succeeded.', remedy);
    return output;
  }

  const pathEntries = (env.PATH ?? '').split(delimiter);
  check('PATH', pathEntries.every(entry => entry !== '' && isAbsolute(entry)),
    'All entries are absolute and nonempty.',
    'Set PATH to absolute directories; remove empty and relative entries.');
  if (!env.SHELL) {
    checks.push({ name: 'SHELL', status: 'warn', message: 'SHELL is unset; set it to your login shell if running interactively.' });
  } else {
    check('SHELL', isAbsolute(env.SHELL) && accessibleFile(env.SHELL, constants.X_OK),
      'Configured login shell is executable.', 'Set SHELL to an absolute, executable shell path.');
  }
  version('git', 'git', ['--version'], /^git version \d/m, 'Install Git and make it available on PATH.');

  if (profile === 'all' || profile === 'backend') {
    // Match the Gradle wrapper's JAVA_HOME precedence over PATH.
    const java = env.JAVA_HOME ? join(env.JAVA_HOME, 'bin', 'java') : 'java';
    const javac = env.JAVA_HOME ? join(env.JAVA_HOME, 'bin', 'javac') : 'javac';
    if (env.JAVA_HOME) {
      check('JAVA_HOME', isAbsolute(env.JAVA_HOME)
        && accessibleFile(java, constants.X_OK) && accessibleFile(javac, constants.X_OK),
      'Points to an executable JDK.', 'Set JAVA_HOME to an absolute JDK 26 directory, or unset it to use PATH.');
    }
    version('java', java, ['-version'], /version "26(?:[."])/,
      'Select JDK 26 for the backend using JAVA_HOME or PATH.');
    version('javac', javac, ['-version'], /^javac 26(?:\.|\s|$)/m,
      'Select the JDK 26 compiler using JAVA_HOME or PATH.');
    version('ffmpeg', 'ffmpeg', ['-version'], /^ffmpeg version \S+/m,
      'Install ffmpeg and make it available on PATH for backend audio processing.');
    check('backend wrapper', ['gradlew', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties']
      .every(file => accessibleFile(join(root, 'backend', file), constants.R_OK))
      && accessibleFile(join(root, 'backend/gradlew'), constants.X_OK),
    'Backend Gradle wrapper files are present and executable.',
    'Restore the backend Gradle wrapper files and executable permission on backend/gradlew.');
  }

  if (profile === 'all' || profile === 'web') {
    const node = version('node', 'node', ['--version'], /^v\d+\.\d+\.\d+/m,
      'Install Node.js 24 or newer and make it available on PATH.');
    const major = node?.match(/^v(\d+)\./m)?.[1];
    check('node baseline', major !== undefined && Number(major) >= 24,
      'Node.js meets the website CI baseline (24+).', 'Use Node.js 24 or newer for the website.');
    version('npm', 'npm', ['--version'], /^\d+\.\d+\.\d+/m,
      'Install npm alongside Node.js and make it available on PATH.');
    check('web dependencies', accessibleFile(join(root, 'web/node_modules/astro/package.json'), constants.R_OK)
      && accessibleFile(join(root, 'web/node_modules/.bin/astro'), constants.X_OK),
    'Local Astro installation is present.', 'Run npm ci from web/ to install the locked website dependencies.');
  }

  return { profile, ok: checks.every(item => item.status !== 'fail'), checks };
}

/** @param {string[]} args */
export function main(args) {
  const usage = 'Usage: node scripts/doctor.mjs [--profile all|shell|backend|web] [--json]';
  let profile = 'all';
  let json = false;
  for (let index = 0; index < args.length; index++) {
    if (args[index] === '--help' || args[index] === '-h') {
      console.log(usage);
      return 0;
    }
    if (args[index] === '--json') {
      json = true;
    } else if (args[index] === '--profile' && profiles.includes(args[index + 1] ?? '')) {
      profile = args[++index];
    } else {
      console.error(usage);
      return 2;
    }
  }
  const report = audit({ profile });
  if (json) {
    console.log(JSON.stringify(report, null, 2));
  } else {
    for (const item of report.checks) {
      console.log(`${item.status.toUpperCase()} ${item.name}: ${item.message}`);
    }
    console.log(report.ok ? 'Environment checks passed.' : 'Environment checks failed; follow the remedies above.');
  }
  return report.ok ? 0 : 1;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = main(process.argv.slice(2));
}
