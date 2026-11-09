/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const { readIndex, IndexCache } = require('../src/core');
const valid = { schemaVersion: 1, engine: 'small/entity-manager-ide-core', entities: {}, managers: {}, diagnostics: [] };

test('calls PHP CLI with a safe argument vector and no shell', async () => {
  let args;
  const metadata = await readIndex({
    phpExecutable: '/custom php', cliPath: '/bundle/bin/sem-ide', workspacePath: '/project with space',
    exec: async (...call) => { args = call; return { stdout: JSON.stringify(valid) }; }
  });
  assert.deepEqual(metadata, valid);
  assert.deepEqual(args[1], ['/bundle/bin/sem-ide', 'index', '/project with space']);
  assert.equal(args[2].shell, false);
  assert.equal(args[2].cwd, '/project with space');
});
test('rejects unknown protocol versions', async () => {
  await assert.rejects(() => readIndex({ phpExecutable: 'php', cliPath: 'cli', workspacePath: '/tmp',
    exec: async () => ({ stdout: JSON.stringify({ ...valid, schemaVersion: 999 }) })
  }), /protocol/);
});
test('rejects malformed output', async () => {
  await assert.rejects(() => readIndex({ phpExecutable: 'php', cliPath: 'cli', workspacePath: '/tmp',
    exec: async () => ({ stdout: '<not json>' })
  }), /invalid JSON/);
});
test('index cache shares concurrent scans per workspace', async () => {
  let scans = 0;
  const cache = new IndexCache(async key => { scans++; return { root: key }; });
  const [a, b] = await Promise.all([cache.get('/a'), cache.get('/a')]);
  assert.deepEqual(a, b);
  assert.equal(scans, 1);
  assert.equal((await cache.get('/a')).root, '/a');
  assert.equal(scans, 1);
  assert.equal((await cache.get('/b')).root, '/b');
  assert.equal(scans, 2);
});
test('cache invalidation replaces stale scan results', async () => {
  let scans = 0;
  let resolveFirst;
  const cache = new IndexCache(async () => {
    scans++;
    if (scans === 1) return await new Promise(r => { resolveFirst = r; });
    return { version: scans };
  });
  const stale = cache.get('/a');
  await Promise.resolve();
  cache.invalidate('/a');
  resolveFirst({ version: 1 });
  assert.equal((await stale).version, 2);
  assert.equal((await cache.get('/a')).version, 2);
});
