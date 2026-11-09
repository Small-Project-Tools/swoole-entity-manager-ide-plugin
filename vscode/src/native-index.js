/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const PhpParser = require('php-parser');

const ENTITY_ATTRIBUTE = 'Small\\SwooleEntityManager\\Entity\\Attribute\\OrmEntity';
const MANAGER_ATTRIBUTE = 'Small\\SwooleEntityManager\\EntityManager\\Attribute\\Entity';
const CONNECTION_ATTRIBUTE = 'Small\\SwooleEntityManager\\EntityManager\\Attribute\\Connection';
const ENTITY_PREFIX = 'Small\\SwooleEntityManager\\Entity\\Attribute\\';
const OMIT = new Set(['.git', '.idea', '.vscode', '.svn', 'node_modules', 'vendor', 'var', 'cache', 'build', 'dist']);
const ENGINE = new PhpParser.Engine({parser:{phpVersion:'8.4',suppressErrors:true},ast:{withPositions:true}});
const MAX_FILES = 10000;
const MAX_FILE_BYTES = 2 * 1024 * 1024;

function nameOf(ast) {
  if (!ast) return null;
  if (typeof ast === 'string') return ast;
  if (ast.kind === 'identifier' || ast.kind === 'name') return ast.name;
  return null;
}

function importsOf(children) {
  const imports = new Map();
  for (const node of children) {
    if (node.kind !== 'usegroup' || (node.type && node.type !== 'normal')) continue;
    for (const item of node.items || []) {
      if (item.type && item.type !== 'normal') continue;
      const full = [node.name, item.name].filter(Boolean).join('\\');
      const short = item.alias ? nameOf(item.alias) : full.split('\\').at(-1);
      if (short) imports.set(short, full.replace(/^\\/, ''));
    }
  }
  return imports;
}

function resolver(namespace, imports) {
  return name => {
    if (!name) return null;
    if (name.startsWith('\\')) return name.replace(/^\\/, '');
    const root = name.split('\\')[0];
    if (imports.has(root)) return imports.get(root) + name.slice(root.length);
    return namespace ? namespace + '\\' + name : name;
  };
}

function attributesOf(node, resolve) {
  const result = new Map();
  for (const group of node?.attrGroups || []) {
    for (const attr of group.attrs || []) {
      const full = resolve(nameOf(attr.name));
      if (full) result.set(full, attr);
    }
  }
  return result;
}

function arg(attr, position, key) {
  if (!attr) return null;
  const positional = [];
  for (const candidate of attr.args || []) {
    if (candidate.kind === 'namedargument') {
      if (nameOf(candidate.name) === key) return candidate.value;
    } else positional.push(candidate);
  }
  return positional[position] || null;
}

function classArg(attr, position, key, resolve) {
  const value = arg(attr, position, key);
  if (!value || value.kind !== 'staticlookup' || String(nameOf(value.offset)).toLowerCase() !== 'class') return null;
  return resolve(nameOf(value.what));
}
function stringArg(attr, position, key) {
  const value = arg(attr, position, key);
  return value?.kind === 'string' ? value.value : null;
}

function extractSymbols(body, namespace, imports, file) {
  const symbols = [];
  const resolve = resolver(namespace, imports);
  for (const node of body) {
    if (!['class', 'trait'].includes(node.kind) || node.isAnonymous) continue;
    const short = nameOf(node.name);
    if (!short) continue;
    const cls = namespace ? namespace + '\\' + short : short;
    const attrs = attributesOf(node, resolve);
    const properties = [];
    const traits = [];
    for (const child of node.body || []) {
      if (child.kind === 'traituse') {
        for (const ref of child.traits || []) {
          const name = resolve(nameOf(ref));
          if (name) traits.push(name);
        }
      } else if (child.kind === 'propertystatement') {
        for (const property of child.properties || []) {
          const named = nameOf(property.name);
          if (named) properties.push({name:named,file,line:property.loc?.start?.line || child.loc?.start?.line || 1,
            attrs:attributesOf(property,resolve)});
        }
      }
    }
    symbols.push({kind:node.kind,class:cls,attrs,properties,traits,parent:resolve(nameOf(node.extends)),resolve,file,line:node.loc?.start?.line || 1});
  }
  return symbols;
}

function scanSource(source, file) {
  const root = ENGINE.parseCode(source, file);
  const symbols = [];
  for (const node of root.children || []) {
    if (node.kind === 'namespace') {
      const namespace = nameOf(node.name) || '';
      const body = node.children || [];
      symbols.push(...extractSymbols(body,namespace,importsOf(body),file));
    }
  }
  // Files without an explicit namespace also contain classes at the program root.
  symbols.push(...extractSymbols(root.children || [],'',importsOf(root.children || []),file));
  return symbols;
}

function collectProperties(symbol, symbolMap, fields, relations, visited) {
  if (visited.has(symbol.class)) return;
  visited.add(symbol.class);
  for (const cls of [symbol.parent, ...symbol.traits]) {
    if (cls && symbolMap.has(cls)) collectProperties(symbolMap.get(cls), symbolMap, fields, relations, visited);
  }
  for (const property of symbol.properties) {
    const common = {name:property.name,file:property.file,line:property.line};
    if (property.attrs.has(ENTITY_PREFIX + 'PrimaryKey')) {
      fields[property.name] = {...common,primaryKey:true,databaseName:stringArg(property.attrs.get(ENTITY_PREFIX+'PrimaryKey'),0,'dbFieldName')};
    } else if (property.attrs.has(ENTITY_PREFIX + 'Field')) {
      fields[property.name] = {...common,primaryKey:false,databaseName:stringArg(property.attrs.get(ENTITY_PREFIX+'Field'),3,'dbFieldName')};
    }
    for (const [name,cardinality] of [['ToOne','one'],['ToMany','many']]) {
      const rel = property.attrs.get(ENTITY_PREFIX + name);
      if (rel) relations[property.name] = {...common,cardinality,manager:classArg(rel,0,'toManager',symbol.resolve)};
    }
  }
}

function projectFiles(root) {
  const stack = [root];
  const files = [];
  while (stack.length && files.length < MAX_FILES) {
    const dir = stack.pop();
    for (const item of fs.readdirSync(dir,{withFileTypes:true})) {
      if (item.isSymbolicLink()) continue;
      const full = path.join(dir,item.name);
      if (item.isDirectory()) { if (!OMIT.has(item.name)) stack.push(full); }
      else if (item.isFile() && item.name.endsWith('.php')) {
        if (fs.statSync(full).size <= MAX_FILE_BYTES) files.push(full);
        if (files.length === MAX_FILES) break;
      }
    }
  }
  return files.sort();
}

function indexProject(root) {
  const absolute = fs.realpathSync(root);
  if (!fs.statSync(absolute).isDirectory()) throw new Error('Workspace root is not a directory.');
  const diagnostics = [];
  const symbols = new Map();
  for (const full of projectFiles(absolute)) {
    const relative = path.relative(absolute,full).split(path.sep).join('/');
    try {
      for (const symbol of scanSource(fs.readFileSync(full,'utf8'),relative)) symbols.set(symbol.class,symbol);
    } catch (error) {
      diagnostics.push({code:'parse-error',file:relative,message:error.message});
    }
  }
  const entities = {};
  const managers = {};
  for (const symbol of symbols.values()) {
    if (symbol.kind !== 'class') continue;
    if (symbol.attrs.has(ENTITY_ATTRIBUTE)) {
      const fields = {};
      const relations = {};
      collectProperties(symbol,symbols,fields,relations,new Set());
      entities[symbol.class] = {class:symbol.class,file:symbol.file,line:symbol.line,
        fields:Object.fromEntries(Object.entries(fields).sort(([a],[b])=>a.localeCompare(b))),
        relations:Object.fromEntries(Object.entries(relations).sort(([a],[b])=>a.localeCompare(b)))};
    }
    const managerAttr = symbol.attrs.get(MANAGER_ATTRIBUTE);
    if (managerAttr) {
      const entity=classArg(managerAttr,0,'entityClass',symbol.resolve);
      if (!entity) {diagnostics.push({code:'unresolved-manager-entity',class:symbol.class,file:symbol.file});continue;}
      managers[symbol.class] = {class:symbol.class,entity,file:symbol.file,line:symbol.line,
        connection:stringArg(symbol.attrs.get(CONNECTION_ATTRIBUTE),0,'connectionName')};
    }
  }
  for (const manager of Object.values(managers)) {
    if (!Object.hasOwn(entities,manager.entity)) diagnostics.push({code:'entity-not-indexed',class:manager.class,file:manager.file,entity:manager.entity});
  }
  return {schemaVersion:1,engine:'small/entity-manager-ide-core',
    entities:Object.fromEntries(Object.entries(entities).sort(([a],[b])=>a.localeCompare(b))),
    managers:Object.fromEntries(Object.entries(managers).sort(([a],[b])=>a.localeCompare(b))),
    api:{},diagnostics};
}
module.exports = { indexProject, scanSource };
