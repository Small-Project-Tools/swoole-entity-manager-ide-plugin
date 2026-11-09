/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';

const { activeCall, aliasMapForQuery, isSmallFactory, literal } = require('./context');

/**
 * Pure completion engine over the immutable v1 PHP index. An editor adapter
 * converts replacement offsets to VS Code ranges; no PHP is executed here.
 */
function getCompletions(index, source, caret) {
  const call = activeCall(source, caret);
  if (!call) return [];
  const argument = call.args[call.argumentIndex] || '';
  const range = replacement(source, caret, call.argumentStart, argument);
  if (!range) return [];
  if (call.method === 'get') {
    if (call.argumentIndex !== 0 || !isSmallFactory(source, call.receiver, caret) || range.quoted) return [];
    return Object.keys(index.managers).sort().map(manager => ({
      label: manager.split('\\').at(-1),
      detail: manager,
      insertText: '\\' + manager + '::class',
      start: range.start, end: range.end,
      kind: 'manager'
    }));
  }
  const context = aliasMapForQuery(index, source, caret, call.receiver);
  if (!context) return [];
  let names = [];
  let kind = 'field';
  const { aliases, rootAlias } = context;
  const fieldMethods = ['getFieldForCondition', 'addOrderBy', 'select', 'addSelect'];
  let qualified = null;
  if (fieldMethods.includes(call.method)) {
    if (call.argumentIndex === 0) {
      const dotted = /^\s*(['"])([A-Za-z_]\w*)\.[A-Za-z_]*$/.exec(argument);
      if (dotted && call.method === 'getFieldForCondition') {
        const entityName = aliases.get(dotted[2]);
        if (!entityName || dotted[2] === rootAlias) return [];
        qualified = { alias: dotted[2], quote: dotted[1] };
        names = Object.keys(index.entities[entityName]?.fields || {});
      } else if (!argument.includes('.')) {
        const entity = index.entities[aliases.get(rootAlias)];
        names = Object.keys(entity?.fields || {});
      }
    } else if (call.argumentIndex === 1) {
      kind = 'alias';
      names = [...aliases.keys()];
    }
  } else if (['join', 'leftJoin', 'rightJoin', 'innerJoin'].includes(call.method)) {
    if (call.argumentIndex === 0) {
      kind = 'alias';
      names = [...aliases.keys()];
    } else if (call.argumentIndex === 1) {
      kind = 'relation';
      const alias = literal(call.args[0]);
      const entity = alias && index.entities[aliases.get(alias)];
      names = Object.keys(entity?.relations || {});
    }
  }
  return names.sort().map(name => ({
    label: name, kind,
    insertText: qualified ? name + qualified.quote + ', ' + qualified.quote + qualified.alias :
      (range.quoted ? name : "'" + name + "'"),
    start: range.start, end: range.end,
    detail: kind === 'field' ? context.manager : undefined
  }));
}

function replacement(source, caret, argStart, argument) {
  const leftWhitespace = /^\s*/.exec(argument)?.[0].length || 0;
  let start = argStart + leftWhitespace;
  const quote = source[start];
  const quoted = quote === "'" || quote === '"';
  if (quoted) {
    start++;
    // Suggest only for a still-open literal; not inside an already-closed string.
    let end = caret;
    for (let i = start; i < caret; i++) {
      if (source[i] === '\\') { i++; continue; }
      if (source[i] === quote) return null;
    }
    while (end < source.length && source[end] !== '\n') {
      if (source[end] === '\\') { end += 2; continue; }
      if (source[end] === quote) break;
      // Only replace the literal content, never the following PHP expression.
      if (source[end] === ')' || source[end] === ',' || source[end] === ';') break;
      end++;
    }
    return { start, end, quoted: true };
  }
  // Ignore expressions like $variable or Class::class; only complete bare identifiers.
  if (!/^[A-Za-z_\\\w]*$/.test(source.slice(start, caret))) return null;
  let end = caret;
  while (end < source.length && /[A-Za-z_\\\w]/.test(source[end])) end++;
  return { start, end, quoted: false };
}

module.exports = { getCompletions, replacement };
