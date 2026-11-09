/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';

const path = require('node:path');
const vscode = require('vscode');
const { readIndex, IndexCache } = require('./core');
const { withManagerMethods } = require('./manager-methods');
const { activeCall } = require('./context');
const { getCompletions } = require('./completion');

function activate(context) {
  const output = vscode.window.createOutputChannel('Small Entity Manager');
  const cliPath = path.join(context.extensionPath, 'core', 'bin', 'sem-ide');
  const config = () => vscode.workspace.getConfiguration('smallEntityManager');
  const index = new IndexCache(async workspacePath => withManagerMethods(await readIndex({
    phpExecutable: config().get('phpExecutable', 'php'),
    cliPath,
    workspacePath
  }), workspacePath));
  const watchers = new Map();
  const watch = folder => {
    const key = folder.uri.toString();
    if (watchers.has(key)) return;
    const watcher = vscode.workspace.createFileSystemWatcher(new vscode.RelativePattern(folder, '**/*.php'));
    watcher.onDidCreate(() => index.invalidate(folder.uri.fsPath));
    watcher.onDidChange(() => index.invalidate(folder.uri.fsPath));
    watcher.onDidDelete(() => index.invalidate(folder.uri.fsPath));
    watchers.set(key, watcher);
    context.subscriptions.push(watcher);
  };
  for (const folder of vscode.workspace.workspaceFolders || []) watch(folder);
  context.subscriptions.push(vscode.workspace.onDidChangeWorkspaceFolders(event => {
    for (const folder of event.removed) {
      watchers.get(folder.uri.toString())?.dispose();
      watchers.delete(folder.uri.toString());
      index.invalidate(folder.uri.fsPath);
    }
    for (const folder of event.added) watch(folder);
  }));
  context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(event => {
    if (event.affectsConfiguration('smallEntityManager')) index.invalidateAll();
  }));

  context.subscriptions.push(vscode.languages.registerCompletionItemProvider(
    { language: 'php', scheme: 'file' },
    {
      async provideCompletionItems(document, position, token) {
        const source = document.getText();
        const caret = document.offsetAt(position);
        if (!activeCall(source, caret) && !/(\$this->\w+|\$\w+)\s*->\s*\w*$/.test(source.slice(0, caret))) return [];
        const folder = vscode.workspace.getWorkspaceFolder(document.uri);
        if (!folder) return [];
        try {
          const metadata = await index.get(folder.uri.fsPath);
          if (token.isCancellationRequested) return [];
          return getCompletions(metadata, source, caret).map(item => {
            const completion = new vscode.CompletionItem(item.label, {
              manager: vscode.CompletionItemKind.Class,
              field: vscode.CompletionItemKind.Field,
              relation: vscode.CompletionItemKind.Property,
              alias: vscode.CompletionItemKind.Variable,
              operator: vscode.CompletionItemKind.EnumMember,
              parameter: vscode.CompletionItemKind.Variable,
              expression: vscode.CompletionItemKind.Snippet,
              method: vscode.CompletionItemKind.Method
            }[item.kind]);
            completion.insertText = item.insertText;
            completion.range = new vscode.Range(document.positionAt(item.start), document.positionAt(item.end));
            completion.detail = item.detail;
            completion.sortText = item.label;
            return completion;
          });
        } catch (error) {
          output.appendLine('Index unavailable: ' + String(error));
          return [];
        }
      }
    }, "'", '"', '(', ',', '>')
  );

  context.subscriptions.push(vscode.commands.registerCommand('smallEntityManager.refreshIndex', async () => {
    index.invalidateAll();
    vscode.window.showInformationMessage('Small Entity Manager: index invalidated; next completion will rebuild it.');
  }));
  context.subscriptions.push(vscode.commands.registerCommand('smallEntityManager.showDiagnostics', async () => {
    const folder = vscode.window.activeTextEditor &&
      vscode.workspace.getWorkspaceFolder(vscode.window.activeTextEditor.document.uri);
    if (!folder) {
      vscode.window.showWarningMessage('Open a PHP file in a workspace to inspect Small Entity Manager.');
      return;
    }
    try {
      const result = await index.get(folder.uri.fsPath);
      const message = `Small Entity Manager: ${Object.keys(result.managers).length} managers, ` +
        `${Object.keys(result.entities).length} entities, ${result.diagnostics.length} diagnostics.`;
      output.appendLine(message);
      for (const diagnostic of result.diagnostics) {
        output.appendLine(JSON.stringify(diagnostic));
      }
      output.show(true);
      vscode.window.showInformationMessage(message);
    } catch (error) {
      output.appendLine(String(error));
      output.show(true);
      vscode.window.showErrorMessage('Small Entity Manager indexing failed: ' + String(error));
    }
  }));
  context.subscriptions.push(output);
}

function deactivate() {}

module.exports = { activate, deactivate };
