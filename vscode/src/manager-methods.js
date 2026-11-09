/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');

/** Discover local public manager methods without loading project classes. */
function withManagerMethods(index, root) {
  const managerMethods = {};
  const rootResolved = fs.realpathSync(root);
  for (const [manager, metadata] of Object.entries(index.managers)) {
    if (typeof metadata.file !== 'string') continue;
    const full = path.resolve(rootResolved, metadata.file);
    if (!full.startsWith(rootResolved + path.sep)) continue;
    try {
      if (!fs.realpathSync(full).startsWith(rootResolved + path.sep)) continue;
    } catch { continue; }
    try {
      const info = fs.statSync(full);
      if (!info.isFile() || info.size > 2 * 1024 * 1024) continue;
      const source = fs.readFileSync(full, 'utf8');
      // Intended as metadata for method completion, not a PHP parser or type provider.
      managerMethods[manager] = [...new Set([...source.matchAll(/\bpublic\s+(?:static\s+)?function\s+([A-Za-z_]\w*)\s*\(/g)]
        .map(match => match[1]))];
    } catch { /* A missing source file is an unresolved manager, not a fatal error. */ }
  }
  return { ...index, managerMethods };
}
module.exports = { withManagerMethods };
