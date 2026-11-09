/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';

const {
  activeCall, aliasMapForQuery, isSmallFactory, literal, resolveClass, splitArguments
} = require('./context');

const FIELD_METHODS = new Set([
  'getFieldForCondition', 'addOrderBy', 'select', 'addSelect',
  'addFieldToUpdate', 'count', 'sum', 'avg', 'min', 'max'
]);
const JOIN_METHODS = new Set(['join', 'innerJoin', 'leftJoin', 'rightJoin']);

/** Extract only an actual identifier under the cursor, not quotes or expressions. */
function identifierAt(source, caret) {
  if (!Number.isInteger(caret) || caret < 0 || caret > source.length) return null;
  let left = caret;
  let right = caret;
  while (left > 0 && /[A-Za-z_0-9]/.test(source[left - 1])) left--;
  while (right < source.length && /[A-Za-z_0-9]/.test(source[right])) right++;
  const name = source.slice(left,right);
  return /^[A-Za-z_]\w*$/.test(name) ? {name,left,right} : null;
}

function argumentValue(value) {
  return (value || '').trim().replace(/^[A-Za-z_]\w*\s*:(?!:)\s*/, '');
}

function completeArguments(source, open) {
  let quote = null;
  let depth = 0;
  for (let i = open; i < source.length; i++) {
    const char = source[i];
    if (quote) {
      if (char === '\\') { i++; continue; }
      if (char === quote) quote = null;
      continue;
    }
    if (char === '"' || char === "'") { quote = char; continue; }
    if (char === '(') depth++;
    if (char === ')') {
      depth--;
      if (!depth) return splitArguments(source.slice(open + 1,i)).map(arg => arg.text);
    }
  }
  return [];
}

function argumentAt(args, position, named) {
  const found = args.find(arg => new RegExp('^\\s*(?:' + named + ')\\s*:').test(arg));
  if (found) return argumentValue(found);
  const unnamed = args.filter(arg => !/^\s*[A-Za-z_]\w*\s*:(?!:)/.test(arg));
  return argumentValue(unnamed[position]);
}

function definitionAt(index, source, caret) {
  const word = identifierAt(source,caret);
  if (!word) return null;
  const call = activeCall(source,caret);
  if (!call) return null;
  // Don't navigate out of the active argument into an unrelated method invocation.
  if (word.left < call.argumentStart) return null;
  const args = completeArguments(source,call.open);
  if (call.method === 'get' && call.argumentIndex === 0 &&
      isSmallFactory(source,call.receiver,caret)) {
    const requested = argumentAt(args,0,'manager|managerClass');
    const match = /^([\\\w]+)::class$/.exec(requested);
    if (!match || !match[1].split('\\').includes(word.name)) return null;
    const manager = resolveClass(index,source,match[1]);
    if (!manager) return null;
    const metadata = index.managers[manager];
    return metadata && metadata.file ? {kind:'manager',name:manager,entry:metadata} : null;
  }
  if (!FIELD_METHODS.has(call.method) && !JOIN_METHODS.has(call.method)) return null;
  const context = aliasMapForQuery(index,source,caret,call.receiver,call.inlineRootAlias);
  if (!context) return null;
  const {aliases,rootAlias} = context;
  if (FIELD_METHODS.has(call.method) && call.argumentIndex === 0) {
    const argument = argumentAt(args,0,'fieldName|fields');
    const specified = literal(argument);
    if (!specified) return null;
    let alias = rootAlias;
    let field = specified;
    const dot = /^([A-Za-z_]\w*)\.([A-Za-z_]\w*)$/.exec(specified);
    if (dot) { alias = dot[1]; field = dot[2]; }
    else alias = literal(argumentAt(args,1,'entityAlias')) || rootAlias;
    if (field !== word.name) return null;
    const entity = index.entities[aliases.get(alias)];
    const entry = entity?.fields?.[field];
    return entry && entry.file ? {kind:'field',name:field,entity:entity.class,entry} : null;
  }
  if (JOIN_METHODS.has(call.method) && call.argumentIndex === 1) {
    const from = literal(argumentAt(args,0,'fromAlias'));
    const relation = literal(argumentAt(args,1,'relationAlias|relation'));
    if (relation !== word.name) return null;
    const entity = index.entities[aliases.get(from)];
    const entry = entity?.relations?.[relation];
    return entry && entry.file ? {kind:'relation',name:relation,entity:entity.class,entry} : null;
  }
  return null;
}

module.exports = { definitionAt, identifierAt };
