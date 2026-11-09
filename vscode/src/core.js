/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';

const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const { Worker } = require('node:worker_threads');
const path = require('node:path');
const run = promisify(execFile);

async function readNativeIndex(workspacePath, timeoutMs = 30000) {
  return new Promise((resolve, reject) => {
    const worker = new Worker(path.join(__dirname, '..', 'dist', 'native-worker.js'), {workerData: {root:workspacePath}});
    const timeout = setTimeout(() => { worker.terminate(); reject(new Error('Small ORM native indexing timed out.')); }, timeoutMs);
    let settled = false;
    const complete = (error, result) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      if (error) reject(error); else resolve(result);
    };
    worker.once('message', message => message.error ? complete(new Error(message.error)) : complete(null, message.index));
    worker.once('error', error => complete(error));
    worker.once('exit', code => {if (!settled) complete(new Error('Small ORM indexing worker exited: ' + code));});
  });
}

async function readIndex({ phpExecutable, cliPath, workspacePath, exec = run, mode = 'php' }) {
  if (mode === 'native') return readNativeIndex(workspacePath);
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

module.exports = { readIndex, readNativeIndex, IndexCache };
