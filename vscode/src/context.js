/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';

const SUPPORTED_METHODS = new Set([
  'get', 'getFieldForCondition', 'addOrderBy', 'select', 'addSelect',
  'join', 'leftJoin', 'rightJoin', 'innerJoin'
]);

/**
 * Mask comments without changing offsets. PHP strings remain intact because
 * query builder aliases and factory class strings are parsed from literals.
 * This is intentionally a conservative lexer, not a full PHP parser.
 */
function maskComments(source) {
  let output = '';
  let state = 'code';
  for (let i = 0; i < source.length; i++) {
    const char = source[i];
    const next = source[i + 1];
    if (state === 'line') {
      if (char === '\n') { output += '\n'; state = 'code'; } else { output += ' '; }
      continue;
    }
    if (state === 'block') {
      if (char === '*' && next === '/') { output += '  '; i++; state = 'code'; }
      else { output += char === '\n' ? '\n' : ' '; }
      continue;
    }
    if (state === 'single' || state === 'double') {
      output += char;
      if (char === '\\' && i + 1 < source.length) { output += source[++i]; continue; }
      if ((state === 'single' && char === "'") || (state === 'double' && char === '"')) state = 'code';
      continue;
    }
    if (char === "'" || char === '"') { state = char === "'" ? 'single' : 'double'; output += char; continue; }
    if ((char === '/' && next === '/') || (char === '/' && next === '*')) {
      state = next === '/' ? 'line' : 'block'; output += '  '; i++; continue;
    }
    if (char === '#' && next !== '[') { state = 'line'; output += ' '; continue; }
    output += char;
  }
  return output;
}

/** Mask PHP literals when locating executable calls (preserving offsets). */
function maskStrings(source) {
  let result = '';
  let quote = null;
  for (let i = 0; i < source.length; i++) {
    const ch = source[i];
    if (quote) {
      if (ch === '\\' && i + 1 < source.length) {
        result += '  '; i++; continue;
      }
      if (ch === quote) quote = null;
      result += ch === '\n' ? '\n' : ' ';
    } else if (ch === "'" || ch === '"') {
      quote = ch;
      result += ' ';
    } else {
      result += ch;
    }
  }
  return result;
}

function splitArguments(source) {
  const parts = [];
  let start = 0;
  let quote = null;
  let depth = 0;
  for (let i = 0; i < source.length; i++) {
    const ch = source[i];
    if (quote) {
      if (ch === '\\') { i++; continue; }
      if (ch === quote) quote = null;
    } else if (ch === "'" || ch === '"') quote = ch;
    else if (ch === '(' || ch === '[' || ch === '{') depth++;
    else if (ch === ')' || ch === ']' || ch === '}') depth--;
    else if (ch === ',' && depth === 0) {
      parts.push({ text: source.slice(start, i), start });
      start = i + 1;
    }
  }
  parts.push({ text: source.slice(start), start });
  return parts;
}

function matchingCloseBefore(source, open, caret) {
  let quote = null;
  let depth = 0;
  for (let i = open; i < caret; i++) {
    const ch = source[i];
    if (quote) {
      if (ch === '\\') { i++; continue; }
      if (ch === quote) quote = null;
    } else if (ch === "'" || ch === '"') quote = ch;
    else if (ch === '(') depth++;
    else if (ch === ')') {
      depth--;
      if (depth === 0) return true;
    }
  }
  return false;
}

function activeCall(source, caret) {
  if (!Number.isInteger(caret) || caret < 0 || caret > source.length) return null;
  const sanitized = maskStrings(maskComments(source.slice(0, caret)));
  // A single method (or $this->property) receiver: do not guess through arbitrary expressions.
  const re = /(\$this->\w+|\$\w+)\s*->\s*(\w+)\s*\(/g;
  let found = null;
  for (const match of sanitized.matchAll(re)) {
    if (!SUPPORTED_METHODS.has(match[2])) continue;
    const open = match.index + match[0].length - 1;
    if (matchingCloseBefore(sanitized, open, caret)) continue;
    if (!found || open > found.open) {
      const rawArgs = source.slice(open + 1, caret);
      const args = splitArguments(rawArgs);
      found = {
        method: match[2], receiver: match[1], open,
        args: args.map(arg => arg.text),
        argumentIndex: args.length - 1,
        argumentStart: open + 1 + args[args.length - 1].start
      };
    }
  }
  return found;
}

function literal(argument) {
  const match = /^\s*(['"])([^'"\\]*)\1\s*$/.exec(argument || '');
  return match ? match[2] : null;
}

function aliasMapForQuery(index, source, caret, receiver) {
  const prefix = maskComments(source.slice(0, caret));
  const queryName = receiver.replace('$', '');
  // Scope assignments to the current method to avoid leaking from unrelated functions.
  const functionMatches = [...prefix.matchAll(/\bfunction\s+\w+\s*\(/g)];
  const scope = functionMatches.length ? prefix.slice(functionMatches.at(-1).index) : prefix;
  const escaped = queryName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const queryPattern = new RegExp('\\$' + escaped + '\\s*=\\s*(\\$\\w+)\\s*->\\s*(?:createQueryBuilder|createScallarQueryBuilder|createStatsQueryBuilder)\\s*\\(\\s*([\\x27\"])([A-Za-z_]\\w*)\\2\\s*\\)', 'g');
  const bindings = [...scope.matchAll(queryPattern)];
  if (bindings.length === 0) return null;
  const binding = bindings.at(-1);
  const managerVar = binding[1];
  const rootAlias = binding[3];
  const manager = resolveManagerForVariable(index, source, caret, managerVar);
  if (!manager || !index.managers[manager]) return null;
  const entity = index.managers[manager].entity;
  if (!index.entities[entity]) return null;
  const aliases = new Map([[rootAlias, entity]]);
  const joins = new RegExp('\\$' + escaped + '\\s*->\\s*(?:join|leftJoin|rightJoin|innerJoin)\\s*\\(\\s*([\\x27\"])(\\w+)\\1\\s*,\\s*([\\x27\"])(\\w+)\\3\\s*,\\s*([\\x27\"])(\\w+)\\5\\s*\\)', 'g');
  // Start with this binding, never apply joins from a previous query assignment.
  const afterBinding = scope.slice(binding.index + binding[0].length);
  for (const join of afterBinding.matchAll(joins)) {
    const from = aliases.get(join[2]);
    const relatedManager = from && index.entities[from]?.relations?.[join[4]]?.manager;
    const target = relatedManager && index.managers[relatedManager]?.entity;
    if (target && index.entities[target]) aliases.set(join[6], target);
  }
  return { manager, rootAlias, aliases };
}

function resolveManagerForVariable(index, source, caret, variable) {
  const prefix = maskComments(source.slice(0, caret));
  const name = variable.replace('$', '');
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const functionMatches = [...prefix.matchAll(/\bfunction\s+\w+\s*\(/g)];
  const scope = functionMatches.length ? prefix.slice(functionMatches.at(-1).index) : prefix;
  const fromFactory = new RegExp('\\$' + escaped + '\\s*=\\s*(?:\\$\\w+|\\$this->\\w+)\\s*->\\s*get\\s*\\(\\s*(?:(?:manager|managerClass)\\s*:\\s*)?([\\\\\\w]+)::class', 'g');
  const matches = [...scope.matchAll(fromFactory)];
  if (matches.length) return resolveClass(index, prefix, matches.at(-1)[1]);
  const typed = new RegExp('\\b([A-Za-z_][\\\\\\w]*)\\s+\\$' + escaped + '\\b', 'g');
  const types = [...prefix.matchAll(typed)];
  if (types.length) return resolveClass(index, prefix, types.at(-1)[1]);
  return null;
}

function resolveClass(index, source, name) {
  if (!name) return null;
  const candidate = name.replace(/^\\/, '');
  if (Object.hasOwn(index.managers, candidate)) return candidate;
  const imports = [...source.matchAll(/(?:^|\n)\s*use\s+([\\\w]+)(?:\s+as\s+(\w+))?\s*;/g)];
  const first = candidate.split('\\')[0];
  for (const imported of imports) {
    const short = imported[2] || imported[1].split('\\').at(-1);
    if (first === short) {
      const qualified = imported[1] + candidate.slice(first.length);
      if (Object.hasOwn(index.managers, qualified)) return qualified;
    }
  }
  // Basename fallback only when unambiguous.
  const candidates = Object.keys(index.managers).filter(key => key.split('\\').at(-1) === candidate);
  return candidates.length === 1 ? candidates[0] : null;
}

function isSmallFactory(source, receiver, caret) {
  const prefix = maskComments(source.slice(0, caret));
  const key = receiver.replace(/^\$this->|^\$/, '');
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  // Typed properties, promoted parameters, method parameters and @var declarations.
  const type = /(?:EntityManagerFactory|EntityManagerFactoryInterface)/.source;
  const typed = new RegExp(type + '\\s+\\$' + escaped + '\\b');
  return typed.test(prefix);
}

module.exports = {
  activeCall, aliasMapForQuery, isSmallFactory, literal,
  maskComments, maskStrings, resolveClass, resolveManagerForVariable, splitArguments
};
