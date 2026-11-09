/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const { getCompletions } = require('../src/completion');
const { activeCall, maskComments, resolveClass, splitArguments } = require('../src/context');

const index = {
  schemaVersion: 1, engine: 'small/entity-manager-ide-core', diagnostics: [],
  managers: {
    'Demo\\EntityManager\\UserManager': { entity: 'Demo\\Entity\\User' },
    'Demo\\EntityManager\\CompanyManager': { entity: 'Demo\\Entity\\Company' }
  },
  entities: {
    'Demo\\Entity\\User': {
      fields: { id: {}, email: {}, companyId: {} },
      relations: { company: { manager: 'Demo\\EntityManager\\CompanyManager' } }
    },
    'Demo\\Entity\\Company': { fields: { id: {}, name: {} }, relations: {} }
  }
};

const head = `<?php
use Demo\\EntityManager\\UserManager;
function run(\\Small\\SwooleEntityManager\\Factory\\EntityManagerFactory $factory): void {
  $manager = $factory->get(UserManager::class);
  $query = $manager->createQueryBuilder('user');
`;
function testAt(body, base = head) {
  const caretMarker = '<caret>';
  const raw = base + body;
  const caret = raw.indexOf(caretMarker);
  assert.notEqual(caret, -1, 'test must provide caret');
  const source = raw.replace(caretMarker, '');
  return { source, caret, proposals: getCompletions(index, source, caret) };
}
function labels(testCase) { return testCase.proposals.map(x => x.label); }

// Field and factory completions
test('mapped fields in a quoted getFieldForCondition argument', () => {
  const result = testAt("  $query->getFieldForCondition('<caret>');\n}");
  assert.deepEqual(labels(result), ['companyId', 'email', 'id']);
  assert.equal(result.proposals[0].insertText, 'companyId');
});
test('unquoted argument inserts a quoted PHP field', () => {
  const result = testAt('  $query->getFieldForCondition(<caret>);\n}');
  assert.equal(result.proposals.find(x => x.label === 'email')?.insertText, "'email'");
});
test('replaces existing quoted content, preserving the closing quote', () => {
  const result = testAt("  $query->getFieldForCondition('ema<caret>il');\n}");
  const item = result.proposals.find(x => x.label === 'email');
  assert.equal(result.source.slice(item.start, item.end), 'email');
});
test('supports double quoted strings', () => {
  const result = testAt('  $query->addOrderBy("<caret>");\n}');
  assert.ok(labels(result).includes('email'));
  assert.equal(result.proposals[0].insertText, 'companyId');
});
test('select first argument offers mapped fields', () => {
  assert.ok(labels(testAt("  $query->select('<caret>');\n}")).includes('companyId'));
});
test('typed Small factory offers fully qualified class references', () => {
  const result = testAt('  $factory->get(Us<caret>);\n}');
  assert.deepEqual(labels(result), ['CompanyManager', 'UserManager']);
  assert.equal(result.proposals.find(x => x.label === 'UserManager').insertText, '\\Demo\\EntityManager\\UserManager::class');
  assert.equal(result.source.slice(result.proposals[0].start, result.proposals[0].end), 'Us');
});
test('unrelated get calls never receive Small completion', () => {
  const result = testAt('  $cache->get(<caret>);\n}');
  assert.deepEqual(result.proposals, []);
});
test('manager factory named argument second position is ignored', () => {
  const result = testAt('  $factory->get(UserManager::class, <caret>);\n}');
  assert.deepEqual(result.proposals, []);
});

// Joins and aliases
test('join relation completion uses the root entity', () => {
  const result = testAt("  $query->leftJoin('user', '<caret>', 'owner');\n}");
  assert.deepEqual(labels(result), ['company']);
});
test('join first argument lists available aliases', () => {
  const result = testAt("  $query->leftJoin('<caret>', 'company', 'owner');\n}");
  assert.deepEqual(labels(result), ['user']);
});
test('known join aliases are available as second field argument', () => {
  const result = testAt("  $query->leftJoin('user', 'company', 'owner')->endJoin();\n  $query->getFieldForCondition('name', '<caret>');\n}");
  assert.deepEqual(labels(result), ['owner', 'user']);
});
test('join relations are resolved from target entity', () => {
  const result = testAt("  $query->leftJoin('user', 'company', 'owner')->endJoin();\n  $query->innerJoin('owner', '<caret>', 'other');\n}");
  assert.deepEqual(labels(result), []);
});
test('reassignment clears old query joins', () => {
  const result = testAt("  $query->leftJoin('user', 'company', 'owner')->endJoin();\n  $query = $manager->createQueryBuilder('user');\n  $query->getFieldForCondition('id', '<caret>');\n}");
  assert.deepEqual(labels(result), ['user']);
});

// Conservative scoping / class inference
test('aliased imported manager is correctly resolved', () => {
  const base = `<?php\nuse Demo\\EntityManager\\UserManager as Stock;\nfunction run(EntityManagerFactory $factory) {\n  $manager = $factory->get(Stock::class);\n  $query = $manager->createQueryBuilder('user');\n`;
  assert.ok(labels(testAt("  $query->getFieldForCondition('<caret>');\n}", base)).includes('email'));
});
test('ambiguous manager basenames do not guess', () => {
  const duplicate = structuredClone(index);
  duplicate.managers['Other\\UserManager'] = {entity:'Demo\\Entity\\Company'};
  const result = testAt("  $query->getFieldForCondition('<caret>');\n}", `<?php\nfunction f($factory) { $manager = $factory->get(UserManager::class); $query = $manager->createQueryBuilder('user');\n`);
  assert.ok(labels(result).includes('email'));
  assert.deepEqual(getCompletions(duplicate, result.source, result.caret), []);
});
test('unresolved manager does not invent fields', () => {
  assert.deepEqual(labels(testAt('  $unknown->getFieldForCondition(<caret>);\n}')), []);
});
test('commented factory and query assignments are ignored', () => {
  const base = `<?php\nfunction f(EntityManagerFactory $factory) {\n  // $manager = $factory->get(UserManager::class);\n  // $query = $manager->createQueryBuilder('user');\n`;
  assert.deepEqual(labels(testAt("  $query->getFieldForCondition('<caret>');\n}", base)), []);
});
test('returns zero completions inside completed unrelated calls', () => {
  const result = testAt(" $query->getFieldForCondition('email');  echo <caret>;\n}");
  assert.deepEqual(result.proposals, []);
});
test('malformed arguments do not produce string completions', () => {
  assert.deepEqual(labels(testAt('  $query->getFieldForCondition($dynamic<caret>);\n}')), []);
});
test('detects metadata class names with namespace and unique short forms', () => {
  assert.equal(resolveClass(index, 'use Demo\\EntityManager\\UserManager;', 'UserManager'), 'Demo\\EntityManager\\UserManager');
  assert.equal(resolveClass(index, '', 'NoSuchManager'), null);
});
test('does not mask PHP attributes while excluding comments', () => {
  assert.match(maskComments("#[Entity] // ghost\n$x = '\\/\\/';"), /^#\[Entity\] +\n/);
});
test('parser preserves nested argument commas', () => {
  assert.deepEqual(splitArguments("call('one', 'two'), 'alias'").map(x => x.text.trim()), ["call('one', 'two')", "'alias'"]);
});
test('active method detection handles nested call', () => {
  const src = '$query->where()->firstCondition($query->getFieldForCondition(\'\');';
  const caret = src.indexOf("getFieldForCondition('") + "getFieldForCondition('".length;
  assert.equal(activeCall(src, caret)?.method, 'getFieldForCondition');
});
test('joined field shorthand is rewritten into two valid PHP arguments', () => {
  const result = testAt("  $query->leftJoin('user', 'company', 'owner')->endJoin();\n  $query->getFieldForCondition('owner.<caret>');\n}");
  assert.deepEqual(labels(result), ['id', 'name']);
  const item = result.proposals.find(x => x.label === 'name');
  assert.equal(item.insertText, "name', 'owner");
  const resolved = result.source.slice(0, item.start) + item.insertText + result.source.slice(item.end);
  assert.match(resolved, /getFieldForCondition\('name', 'owner'\)/);
});
test('unknown dotted alias does not return misleading root fields', () => {
  const result = testAt("  $query->getFieldForCondition('missing.<caret>');\n}");
  assert.deepEqual(labels(result), []);
});
test('supports Symfony bundle named manager class factory signatures', () => {
  const base = `<?php\nuse Demo\\EntityManager\\UserManager;\nfunction f(\\Small\\SwooleEntityManagerBundle\\Contract\\EntityManagerFactoryInterface $factory) {\n  $manager = $factory->get(managerClass: UserManager::class);\n  $query = $manager->createQueryBuilder('u');\n`;
  assert.ok(labels(testAt("  $query->getFieldForCondition('<caret>');\n}", base)).includes('email'));
});
test('no proposals within a string containing apparent query code', () => {
  const result = testAt('  $text = "$query->getFieldForCondition(<caret>";\n}');
  assert.deepEqual(labels(result), []);
});
test('no proposals in commented-out queries', () => {
  const result = testAt('  // $query->getFieldForCondition(<caret>\n}');
  assert.deepEqual(labels(result), []);
});
