/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';

const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const run = promisify(execFile);

async function readIndex({ phpExecutable, cliPath, workspacePath, exec = run }) {
  const { stdout } = await exec(phpExecutable, [cliPath, 'index', workspacePath], {
    cwd: workspacePath,
    timeout: 30000,
    maxBuffer: 32 * 1024 * 1024,
    windowsHide: true,
    shell: false
  });
  let parsed;
  try { parsed = JSON.parse(stdout); }
  catch { throw new Error('Small ORM indexer returned invalid JSON.'); }
  if (parsed?.schemaVersion !== 1 || parsed?.engine !== 'small/entity-manager-ide-core' ||
      !parsed.entities || typeof parsed.entities !== 'object' ||
      !parsed.managers || typeof parsed.managers !== 'object' ||
      !Array.isArray(parsed.diagnostics)) {
    throw new Error('Unsupported Small ORM metadata protocol (expected schemaVersion 1).');
  }
  return parsed;
}

/** One in-flight scan per workspace; filesystem changes invalidate the snapshot. */
class IndexCache {
  constructor(loader) {
    this.loader = loader;
    this.cache = new Map();
  }
  invalidate(key) {
    const old = this.cache.get(key);
    this.cache.set(key, { generation: (old?.generation || 0) + 1 });
  }
  invalidateAll() {
    for (const key of this.cache.keys()) this.invalidate(key);
  }
  async get(key) {
    let item = this.cache.get(key);
    if (!item) { item = { generation: 0 }; this.cache.set(key, item); }
    if (item.value) return item.value;
    if (!item.pending) {
      const generation = item.generation;
      item.pending = Promise.resolve().then(() => this.loader(key)).then(value => {
        const current = this.cache.get(key);
        if (current?.generation === generation) current.value = value;
        return value;
      }).finally(() => {
        const current = this.cache.get(key);
        if (current?.generation === generation) delete current.pending;
      });
    }
    const value = await item.pending;
    return this.cache.get(key)?.value || this.get(key);
  }
}

module.exports = { readIndex, IndexCache };
