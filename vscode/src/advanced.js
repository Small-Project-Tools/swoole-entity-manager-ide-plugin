/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';

const { aliasMapForQuery, resolveManagerForVariable, maskComments, maskStrings } = require('./context');

// Cases from small/swoole-entity-manager ConditionOperatorType (Core 3).
const OPERATORS = Object.freeze([
  'equal', 'inferior', 'superior', 'inferiorOrEqual', 'superiorOrEqual',
  'notEqual', 'like', 'notLike', 'regex', 'isNull', 'isNotNull',
  'exists', 'notExists', 'in', 'notIn'
]);
const ENUM = '\\Small\\SwooleEntityManager\\QueryBuilder\\RelationalQueryBuilder\\Enum\\ConditionOperatorType';
const CONDITION_METHODS = new Set(['firstCondition', 'andCondition', 'orCondition', 'xorCondition']);

function conditionQuery(source, receiver, caret) {
  const prefix = source.slice(0, caret);
  if (/^\$\w+$/.test(receiver)) {
    const variable = receiver.slice(1).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const matches = [...prefix.matchAll(new RegExp('\\$' + variable + '\\s*=\\s*(\\$\\w+)\\s*->\\s*where\\s*\\(', 'g'))];
    if (matches.length) return matches.at(-1)[1];
    if (/\$\w+->(?:createQueryBuilder|where)\s*\(/.test(prefix)) return receiver;
  }
  return receiver;
}

function conditionSuggestions(index, source, caret, call, range) {
  if (!CONDITION_METHODS.has(call.method)) return null;
  const receiver = conditionQuery(source, call.receiver, caret);
  const context = aliasMapForQuery(index, source, caret, receiver, call.inlineRootAlias);
  if (!context) return [];
  const names = [];
  if (call.argumentIndex === 0 && !range.quoted) {
    for (const [alias, entityName] of context.aliases) {
      for (const field of Object.keys(index.entities[entityName]?.fields || {})) {
        const aliasArg = alias === context.rootAlias ? '' : `, '${alias}'`;
        names.push({
          label: alias === context.rootAlias ? field : alias + '.' + field,
          kind: 'expression',
          insertText: `${receiver}->getFieldForCondition('${field}'${aliasArg})`,
          start: range.start, end: range.end
        });
      }
    }
    return names.sort((a, b) => a.label.localeCompare(b.label));
  }
  if (call.argumentIndex === 1 && !range.quoted) {
    const startText = source.slice(range.start, caret);
    const suffixOnly = /(?:\\?\w+\\)*ConditionOperatorType::\w*$/.test(startText);
    return OPERATORS.map(operator => ({
      label: operator, kind: 'operator',
      insertText: suffixOnly ? operator : ENUM + '::' + operator,
      start: suffixOnly ? range.start + startText.lastIndexOf('::') + 2 : range.start,
      end: range.end
    }));
  }
  if (call.argumentIndex === 2) return parameterSuggestions(source, range, caret, receiver, true);
  return [];
}

function parameterSuggestions(source, range, caret, receiver, withColon = false) {
  // Local placeholders only: never guess values from arbitrary identifiers.
  const prefix = source.slice(0, caret);
  const found = new Set();
  for (const match of prefix.matchAll(/(['"]):([A-Za-z_]\w*)\1/g)) found.add(match[2]);
  for (const match of prefix.matchAll(/\bsetParameter\s*\(\s*(['"])([A-Za-z_]\w*)\1/g)) found.add(match[2]);
  return [...found].sort().map(label => ({
    label, kind: 'parameter',
    insertText: range.quoted ? (withColon ? ':' : '') + label : `'${withColon ? ':' : ''}${label}'`,
    start: range.start, end: range.end
  }));
}

function memberSuggestions(index, source, caret) {
  const prefix = maskStrings(maskComments(source.slice(0, caret)));
  const match = /(\$this->\w+(?:\(\s*\))?|\$\w+)\s*->\s*([A-Za-z_]\w*)?$/.exec(prefix);
  if (!match) return [];
  const manager = resolveManagerForVariable(index, source, caret, match[1]);
  if (!manager || !index.managers[manager]) return [];
  const declared = index.managerMethods?.[manager] || [];
  const names = [...new Set([...declared, 'createQueryBuilder', 'createScallarQueryBuilder',
    'createStatsQueryBuilder', 'createUpdateBuilder', 'createDeleteBuilder', 'getResult', 'findBy', 'findOneBy'])];
  const start = caret - (match[2] || '').length;
  return names.sort().map(name => ({ label: name, kind: 'method', insertText: `${name}()`, start, end: caret, detail: manager }));
}

module.exports = { OPERATORS, conditionSuggestions, memberSuggestions, parameterSuggestions };
