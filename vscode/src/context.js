/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';

const SUPPORTED_METHODS = new Set([
  'get', 'getFieldForCondition', 'addOrderBy', 'select', 'addSelect',
  'join', 'leftJoin', 'rightJoin', 'innerJoin',
  'firstCondition', 'andCondition', 'orCondition', 'xorCondition', 'setParameter', 'addFieldToUpdate',
  'count', 'sum', 'avg', 'min', 'max'
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
  const sanitized = maskStrings(maskComments(source.slice(0,caret)));
  const re = /(\$this->\w+|\$\w+)\s*->\s*(\w+)\s*\(/g;
  const chained = /(\$this->\w+|\$\w+)\s*->\s*where\s*\(\s*\)\s*->\s*(firstCondition|andCondition|orCondition|xorCondition)\s*\(/g;
  const inline = /(\$this->\w+(?:\(\s*\))?|\$\w+)\s*->\s*(?:createQueryBuilder|createScallarQueryBuilder|createStatsQueryBuilder|createUpdateBuilder|createDeleteBuilder)\s*\([^()]*\)\s*->\s*(\w+)\s*\(/g;
  let found = null;
  const consider = (match,method,receiver,inlineRootAlias) => {
    if (!SUPPORTED_METHODS.has(method)) return;
    const open = match.index + match[0].length - 1;
    if (matchingCloseBefore(sanitized,open,caret)) return;
    if (found && found.open >= open) return;
    const rawArgs = source.slice(open + 1,caret);
    const args = splitArguments(rawArgs);
    found = {
      method,receiver,open,args:args.map(arg=>arg.text),
      argumentIndex:args.length-1,
      argumentStart:open+1+args.at(-1).start
    };
    if (inlineRootAlias !== undefined) found.inlineRootAlias = inlineRootAlias;
  };
  for (const match of sanitized.matchAll(re)) consider(match,match[2],match[1]);
  for (const match of sanitized.matchAll(chained)) consider(match,match[2],match[1]);
  for (const match of sanitized.matchAll(inline)) {
    const builderMatch = /->\s*(?:createQueryBuilder|createScallarQueryBuilder|createStatsQueryBuilder|createUpdateBuilder|createDeleteBuilder)\s*\(/.exec(match[0]);
    if (!builderMatch) continue;
    const builderOpen = match.index + builderMatch.index + builderMatch[0].length - 1;
    const builderArgs = parseCallArguments(source,builderOpen);
    if (builderArgs === null) continue;
    consider(match,match[2],match[1],literal(builderArgs));
  }
  return found;
}

function literal(argument) {
  const match = /^\s*(['"])([^'"\\]*)\1\s*$/.exec(argument || '');
  return match ? match[2] : null;
}

function escapeRegExp(value) {
  return value.replace(/[|\\{}()[\]^$+*?.]/g, '\\$&');
}

/** Return the matching brace within sanitized PHP source. */
function matchingBrace(source, open) {
  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++;
    if (source[i] === '}' && --depth === 0) return i;
  }
  return -1;
}

/** Restrict local accessor and property inference to the class under the caret. */
function currentClassSource(source, caret) {
  const masked = maskStrings(maskComments(source));
  let range = null;
  for (const match of masked.matchAll(/\b(?:class|trait)\s+[A-Za-z_]\w*\b/g)) {
    const start = match.index + match[0].length;
    const open = masked.indexOf('{', start);
    const semicolon = masked.indexOf(';', start);
    if (open < 0 || (semicolon >= 0 && semicolon < open)) continue;
    const close = matchingBrace(masked, open);
    if (open < caret && (close < 0 || caret <= close)) {
      if (!range || match.index > range.start) {
        range = { start: match.index, end: close < 0 ? source.length : close + 1 };
      }
    }
  }
  return range ? source.slice(range.start, range.end) : '';
}

/** Only @var inside a PHPDoc block at the beginning of a line is trusted. */
function documentedManagerType(source, scopeStart, caret, variable) {
  const section = source.slice(scopeStart, caret);
  const pattern = new RegExp('@var\\s+\\??([\\\\\\w]+)\\s+\\$' + escapeRegExp(variable) + '\\b', 'g');
  let last = null;
  for (const block of section.matchAll(/\/\*\*([\s\S]*?)\*\//g)) {
    const lineStart = section.lastIndexOf('\n', block.index) + 1;
    // Ignore string literals and single-line comments containing fake PHPDoc.
    if (!/^[ \t]*$/.test(section.slice(lineStart, block.index))) continue;
    const annotations = [...block[1].matchAll(pattern)];
    if (annotations.length) last = annotations.at(-1)[1];
  }
  return last;
}

function parseCallArguments(source, open) {
  let depth = 0;
  let quote = null;
  for (let i = open; i < source.length; i++) {
    const ch = source[i];
    if (quote) {
      if (ch === '\\') { i++; continue; }
      if (ch === quote) quote = null;
    } else if (ch === "'" || ch === '"') quote = ch;
    else if (ch === '(') depth++;
    else if (ch === ')') {
      depth--;
      if (depth === 0) return source.slice(open + 1,i);
    }
  }
  return null;
}

function joinedAliasArgument(argumentsText, position, named) {
  const argumentsList = splitArguments(argumentsText).map(p => p.text.trim());
  const byName = argumentsList.find(value => new RegExp('^' + named + '\\s*:').test(value));
  if (byName) return literal(byName.slice(byName.indexOf(':') + 1));
  return literal(argumentsList.filter(value => !/^[A-Za-z_]\w*\s*:(?!:)/.test(value))[position]);
}

function updateQueryAliases(index, scopedTail, assignmentTailEnd, receiver, aliases) {
  const masked = maskStrings(maskComments(scopedTail));
  const events = [];
  const generic = /->\s*(?:join|leftJoin|rightJoin|innerJoin)\s*\(/g;
  for (const match of masked.slice(0,assignmentTailEnd).matchAll(generic)) {
    events.push({index:match.index,open:match.index + match[0].length - 1});
  }
  const explicit = new RegExp('\\$' + escapeRegExp(receiver) +
    '\\s*->\\s*(?:join|leftJoin|rightJoin|innerJoin)\\s*\\(', 'g');
  for (const match of masked.matchAll(explicit)) {
    events.push({index:match.index,open:match.index + match[0].length - 1});
  }
  const seen = new Set();
  for (const event of events.sort((a,b)=>a.index-b.index)) {
    if (seen.has(event.open)) continue;
    seen.add(event.open);
    const argumentsText = parseCallArguments(scopedTail, event.open);
    if (!argumentsText) continue;
    const fromAlias = joinedAliasArgument(argumentsText,0,'fromAlias');
    const relation = joinedAliasArgument(argumentsText,1,'relationAlias|relation');
    const alias = joinedAliasArgument(argumentsText,2,'alias') || relation;
    if (!fromAlias || !relation || !alias) continue;
    const fromEntity = aliases.get(fromAlias);
    const targetManager = fromEntity && index.entities[fromEntity]?.relations?.[relation]?.manager;
    const target = targetManager && index.managers[targetManager]?.entity;
    if (target && index.entities[target]) aliases.set(alias,target);
  }
}

function aliasMapForQuery(index, source, caret, receiver, inlineRootAlias = undefined) {
  if (inlineRootAlias !== undefined) {
    const manager = resolveManagerForVariable(index,source,caret,receiver);
    if (!manager || !index.managers[manager]) return null;
    const entity = index.managers[manager].entity;
    if (!index.entities[entity]) return null;
    const name = entity.split('\\').at(-1);
    const rootAlias = inlineRootAlias || name[0].toLowerCase() + name.slice(1);
    return {manager,rootAlias,aliases:new Map([[rootAlias,entity]])};
  }
  if (!/^\$\w+$/.test(receiver)) return null;
  const prefix = maskComments(source.slice(0,caret));
  const functions = [...prefix.matchAll(/\bfunction\s+\w+\s*\(/g)];
  const scope = functions.length ? prefix.slice(functions.at(-1).index) : prefix;
  const variableName = receiver.slice(1);
  const pattern = new RegExp('\\$' + escapeRegExp(variableName) +
    '\\s*=\\s*(\\$this->\\w+(?:\\(\\s*\\))?|\\$\\w+)\\s*->\\s*' +
    '(?:createQueryBuilder|createScallarQueryBuilder|createStatsQueryBuilder|createUpdateBuilder|createDeleteBuilder)' +
    '\\s*\\(\\s*(?:([\\x27"])([A-Za-z_]\\w*)\\2)?\\s*\\)', 'g');
  const bindings = [...scope.matchAll(pattern)];
  if (!bindings.length) return null;
  const binding = bindings.at(-1);
  const manager = resolveManagerForVariable(index,source,caret,binding[1]);
  if (!manager || !index.managers[manager]) return null;
  const entity = index.managers[manager].entity;
  if (!index.entities[entity]) return null;
  const short = entity.split('\\').at(-1);
  const rootAlias = binding[3] || short[0].toLowerCase() + short.slice(1);
  const aliases = new Map([[rootAlias,entity]]);
  const tail = scope.slice(binding.index + binding[0].length);
  const semicolon = maskStrings(tail).indexOf(';');
  updateQueryAliases(index,tail,semicolon >= 0 ? semicolon : tail.length,variableName,aliases);
  return {manager,rootAlias,aliases};
}

function resolveManagerFromAccessor(index, source, caret, accessor, visited) {
  if (visited.has(accessor) || visited.size >= 5) return null;
  const ancestors = new Set(visited);
  ancestors.add(accessor);
  const classText = currentClassSource(source, caret);
  if (!classText) return null;
  const escaped = escapeRegExp(accessor);
  // A zero-argument accessor must not be inferred from a method requiring arguments.
  const declaration = new RegExp('\\bfunction\\s+(?:&\\s*)?' + escaped +
    '\\s*\\(\\s*\\)\\s*(?::\\s*\\??([\\\\\\w|&]+))?\\s*\\{', 'g');
  const executableClass = maskStrings(maskComments(classText));
  const matches = [...executableClass.matchAll(declaration)];
  if (matches.length !== 1) return null;
  const matched = matches[0];
  if (matched[1]) {
    const typed = resolveClass(index, source, matched[1]);
    if (typed) return typed;
  }
  const open = matched.index + matched[0].length - 1;
  const close = matchingBrace(executableClass, open);
  if (close < 0) return null;
  const body = executableClass.slice(open + 1, close);
  const returns = [...body.matchAll(/\breturn\s+([^;]*);/g)];
  // Every return must resolve to the same mapped manager.
  if (!returns.length || returns.length !== [...body.matchAll(/\breturn\b/g)].length) return null;
  const targets = new Set();
  for (const found of returns) {
    const expression = found[1].trim();
    let concrete = null;
    const property = /^\$this->([A-Za-z_]\w*)$/.exec(expression);
    const delegate = /^\$this->([A-Za-z_]\w*)\s*\(\s*\)$/.exec(expression);
    const directFactory = /^(\$(?:this->)?[A-Za-z_]\w*)\s*->\s*get\s*\(\s*(?:(?:manager|managerClass)\s*:\s*)?([\\\w]+)\s*::\s*class\s*\)$/.exec(expression);
    if (property) {
      concrete = resolveManagerForVariable(index, source, caret, '$this->' + property[1], ancestors);
    } else if (delegate) {
      concrete = resolveManagerFromAccessor(index, source, caret, delegate[1], ancestors);
    } else if (directFactory && isSmallFactory(source, directFactory[1], caret)) {
      concrete = resolveClass(index, source, directFactory[2]);
    }
    if (!concrete) return null;
    targets.add(concrete);
    if (targets.size > 1) return null;
  }
  return targets.values().next().value || null;
}

function resolveManagerForVariable(index, source, caret, variable, visited = new Set()) {
  if (variable === '$this') {
    const containing = currentClassSource(source, caret);
    const declared = /^class\s+([A-Za-z_]\w*)\b/.exec(containing);
    if (!declared) return null;
    const namespaces = [...source.slice(0, caret).matchAll(/\bnamespace\s+([\\\w]+)\s*;/g)];
    const namespace = namespaces.at(-1)?.[1] || '';
    const candidate = namespace ? namespace + '\\' + declared[1] : declared[1];
    // Do not use unique-basename fallback for $this: it would cross namespaces.
    return Object.hasOwn(index.managers, candidate) ? candidate : null;
  }
  if (/^\$this->\w+\(\s*\)$/.test(variable)) {
    return resolveManagerFromAccessor(index,source,caret,
      variable.slice('$this->'.length).replace(/\(\s*\)$/,''),visited);
  }
  const prefix = maskComments(source.slice(0,caret));
  if (variable.startsWith('$this->')) {
    const property = variable.slice('$this->'.length);
    const section = currentClassSource(source,caret);
    const pattern = new RegExp('\\b(?:private|protected|public)\\s+(?:(?:readonly|static)\\s+)*\\??([\\\\\\w]+)\\s+\\$' +
      escapeRegExp(property) + '\\b','g');
    const candidates = [...section.matchAll(pattern)];
    if (candidates.length) return resolveClass(index,source,candidates.at(-1)[1]);
    return null;
  }
  if (!/^\$\w+$/.test(variable)) return null;
  const escaped = escapeRegExp(variable.slice(1));
  const functions = [...prefix.matchAll(/\bfunction\s+\w+\s*\(/g)];
  const scope = functions.length ? prefix.slice(functions.at(-1).index) : prefix;
  const factories = new RegExp('\\$' + escaped +
    '\\s*=\\s*(\\$this->\\w+|\\$\\w+)\\s*->\\s*get\\s*\\(\\s*' +
    '(?:(?:manager|managerClass)\\s*:\\s*)?([\\\\\\w]+)::class','g');
  const fromFactory = [...maskStrings(scope).matchAll(factories)];
  if (fromFactory.length) {
    const selected = fromFactory.at(-1);
    if (!isSmallFactory(source,selected[1],caret)) return null;
    return resolveClass(index,source,selected[2]);
  }
  const accessor = new RegExp('\\$' + escaped +
    '\\s*=\\s*(\\$this->\\w+\\(\\s*\\))\\s*;','g');
  const byAccessor = [...maskStrings(scope).matchAll(accessor)];
  if (byAccessor.length) return resolveManagerForVariable(index,source,caret,byAccessor.at(-1)[1],visited);
  const typed = new RegExp('\\b([A-Za-z_][\\\\\\w]*)\\s+\\$' + escaped + '\\b','g');
  const matches = [...maskStrings(prefix).matchAll(typed)];
  if (matches.length) return resolveClass(index,prefix,matches.at(-1)[1]);
  // Keep PHPDoc inference scoped to actual annotation blocks in this function.
  const scopeStart = caret - scope.length;
  const documented = documentedManagerType(source, scopeStart, caret, variable.slice(1));
  if (documented) return resolveClass(index, prefix, documented);
  return null;
}

function resolveClass(index, source, name) {
  if (!name) return null;
  const candidate = name.replace(/^\\/, '');
  if (Object.hasOwn(index.managers, candidate)) return candidate;
  const imports = [];
  for (const match of source.matchAll(/(?:^|\n)\s*use\s+([\\\w]+)(?:\s+as\s+(\w+))?\s*;/g)) {
    imports.push({ full: match[1], short: match[2] || match[1].split('\\').at(-1) });
  }
  for (const group of source.matchAll(/(?:^|\n)\s*use\s+([\\\w]+)\\\s*\{([^}]*)\}\s*;/g)) {
    for (const item of group[2].split(',')) {
      const match = /^\s*([\\\w]+)(?:\s+as\s+(\w+))?\s*$/.exec(item);
      if (match) imports.push({ full: group[1] + '\\' + match[1], short: match[2] || match[1].split('\\').at(-1) });
    }
  }
  const first = candidate.split('\\')[0];
  for (const imported of imports) {
    if (first === imported.short) {
      const qualified = imported.full + candidate.slice(first.length);
      if (Object.hasOwn(index.managers, qualified)) return qualified;
    }
  }
  // Resolve local namespace without guessing an ambiguous manager basename.
  const namespace = /\bnamespace\s+([\\\w]+)\s*;/.exec(source)?.[1];
  if (namespace && Object.hasOwn(index.managers, namespace + '\\' + candidate)) {
    return namespace + '\\' + candidate;
  }
  const candidates = Object.keys(index.managers).filter(key => key.split('\\').at(-1) === candidate);
  return candidates.length === 1 ? candidates[0] : null;
}

function isSmallFactory(source, receiver, caret) {
  const prefix = maskStrings(maskComments(source.slice(0, caret)));
  const property = receiver.replace(/^\$this->|^\$/, '');
  const typed = new RegExp('\\b([\\\\\\w]+)\\s+\\$' + escapeRegExp(property) + '\\b', 'g');
  const types = [...prefix.matchAll(typed)];
  if (!types.length) {
    // Legacy projects use conventional variable names without declaring a factory type.
    return /^\$(?:factory|entityManagerFactory)$/.test(receiver);
  }
  const knownFactory = /^Small\\SwooleEntityManager(?:Bundle)?\\(?:Factory|Contract)\\EntityManagerFactory(?:Interface)?$/;
  const value = types.at(-1)[1].replace(/^\\/, '');
  if (knownFactory.test(value)) return true;

  const imports = new Map();
  const before = source.slice(0, caret);
  for (const match of before.matchAll(/(?:^|\n)\s*use\s+([\\\w]+)(?:\s+as\s+(\w+))?\s*;/g)) {
    imports.set(match[2] || match[1].split('\\').at(-1), match[1]);
  }
  for (const group of before.matchAll(/(?:^|\n)\s*use\s+([\\\w]+)\\\s*\{([^}]*)\}\s*;/g)) {
    for (const part of group[2].split(',')) {
      const match = /^\s*([\\\w]+)(?:\s+as\s+(\w+))?\s*$/.exec(part);
      if (match) imports.set(match[2] || match[1].split('\\').at(-1), group[1] + '\\' + match[1]);
    }
  }
  const first = value.split('\\')[0];
  if (imports.has(first)) return knownFactory.test(imports.get(first) + value.slice(first.length));
  // Unqualified factory names without explicit imports are retained for legacy code.
  return value === 'EntityManagerFactory' || value === 'EntityManagerFactoryInterface';
}

module.exports = {
  activeCall, aliasMapForQuery, isSmallFactory, literal,
  maskComments, maskStrings, resolveClass, resolveManagerForVariable, splitArguments
};
