/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const Module = require('node:module');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const syntheticIndex = {
  schemaVersion: 1, engine: 'small/entity-manager-ide-core',
  entities: { 'Demo\\Entity\\User': { fields: { email: {} }, relations: {} } },
  managers: { 'Demo\\EntityManager\\UserManager': { entity: 'Demo\\Entity\\User' } },
  diagnostics: []
};

test('VS Code provider invokes bundled indexer, offers completion and diagnostics', async () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'sem-ide-vscode-'));
  const phpFile = path.join(temp, 'demo.php');
  const bin = path.join(temp, 'core', 'bin');
  fs.mkdirSync(bin, {recursive: true});
  fs.writeFileSync(path.join(bin, 'sem-ide'), `process.stdout.write(${JSON.stringify(JSON.stringify(syntheticIndex))});\n`);
  const source = `<?php\nuse Demo\\EntityManager\\UserManager;\nfunction test(EntityManagerFactory $factory) {\n$manager = $factory->get(UserManager::class);\n$query = $manager->createQueryBuilder('user');\n$query->getFieldForCondition('');\n}`;
  fs.writeFileSync(phpFile, source);
  const caret = source.indexOf("getFieldForCondition('") + "getFieldForCondition('".length;
  const folder = { uri: { fsPath: temp, toString: () => 'file://' + temp } };
  const commands = new Map();
  let provider;
  let watcherChange;
  const messages = [];
  const mock = {
    workspace: {
      workspaceFolders: [folder],
      createFileSystemWatcher: () => ({
        onDidCreate: fn => { watcherChange = fn; },
        onDidChange: fn => { watcherChange = fn; },
        onDidDelete: fn => { watcherChange = fn; },
        dispose() {}
      }),
      getWorkspaceFolder: () => folder,
      getConfiguration: () => ({get: () => process.execPath}),
      onDidChangeWorkspaceFolders: () => ({dispose() {}}),
      onDidChangeConfiguration: () => ({dispose() {}})
    },
    languages: { registerCompletionItemProvider: (selector, registered) => {
      assert.equal(selector.language, 'php');
      provider = registered;
      return {dispose() {}};
    }},
    commands: { registerCommand: (name, fn) => {
      commands.set(name, fn);
      return {dispose() {}};
    }},
    window: {
      activeTextEditor: {document: {uri: {fsPath: phpFile}}},
      createOutputChannel: () => ({appendLine: message => messages.push(message), show() {}, dispose() {}}),
      showInformationMessage: text => messages.push(text),
      showWarningMessage: text => messages.push(text),
      showErrorMessage: text => { throw new Error(text); }
    },
    RelativePattern: class {constructor(folder, pattern) { this.folder = folder; this.pattern = pattern; }},
    Range: class {constructor(start, end) {this.start = start;this.end = end;}},
    CompletionItem: class {constructor(label, kind) {this.label = label;this.kind = kind;}},
    CompletionItemKind: {Class:1,Field:2,Property:3,Variable:4}
  };
  const oldLoad = Module._load;
  Module._load = function(id, ...args) {
    if (id === 'vscode') return mock;
    return oldLoad.call(this, id, ...args);
  };
  let extension;
  try { extension = require('../src/extension'); }
  finally { Module._load = oldLoad; }
  try {
    const context = {extensionPath: temp, subscriptions: []};
    extension.activate(context);
    const document = {
      uri: {fsPath: phpFile},
      getText: () => source,
      offsetAt: pos => pos.offset,
      positionAt: offset => ({offset})
    };
    const items = await provider.provideCompletionItems(document, {offset: caret}, {isCancellationRequested:false});
    assert.deepEqual(items.map(item => item.label), ['email']);
    assert.equal(items[0].insertText, 'email');
    assert.ok(items[0].range.start.offset < items[0].range.end.offset || items[0].range.start.offset === items[0].range.end.offset);
    assert.equal(commands.has('smallEntityManager.refreshIndex'), true);
    assert.equal(commands.has('smallEntityManager.showDiagnostics'), true);
    await commands.get('smallEntityManager.showDiagnostics')();
    assert.ok(messages.some(message => message.includes('1 managers, 1 entities, 0 diagnostics')));
    watcherChange();
    await commands.get('smallEntityManager.refreshIndex')();
    for (const resource of context.subscriptions) resource.dispose?.();
  } finally {
    fs.rmSync(temp, {recursive: true, force: true});
  }
});
