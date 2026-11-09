/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';

const fs = require('node:fs');
const path = require('node:path');

const extensionRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(extensionRoot, '..');
const target = path.join(extensionRoot, 'core');
const cli = path.join(repoRoot, 'bin', 'sem-ide');
const php = path.join(repoRoot, 'src');
if (!fs.existsSync(cli) || !fs.existsSync(path.join(php, 'Autoload.php'))) {
  throw new Error('Cannot bundle the IDE core. Run this script from the monorepo root checkout.');
}
fs.rmSync(target, { recursive: true, force: true });
fs.mkdirSync(path.join(target, 'bin'), { recursive: true });
fs.copyFileSync(cli, path.join(target, 'bin', 'sem-ide'));
fs.cpSync(php, path.join(target, 'src'), { recursive: true });
console.log('Embedded the read-only Small PHP metadata core into vscode/core.');
