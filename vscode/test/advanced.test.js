/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { getCompletions } = require('../src/completion');
const { withManagerMethods } = require('../src/manager-methods');
const { OPERATORS } = require('../src/advanced');

const index = {
  entities: {
    'Demo\\User': { fields: { id: {}, email: {} }, relations: {
      company: { manager: 'Demo\\CompanyManager' }
    } },
    'Demo\\Company': { fields: { id: {}, title: {} }, relations: {} }
  },
  managers: {
    'Demo\\UserManager': { entity: 'Demo\\User' },
    'Demo\\CompanyManager': { entity: 'Demo\\Company' }
  }
};
const header = `<?php
use Demo\\UserManager;
function run(\\Small\\SwooleEntityManager\\Factory\\EntityManagerFactory $factory) {
  $manager = $factory->get(UserManager::class);
  $query = $manager->createQueryBuilder('u');
`;
function at(body, prefix = header, metadata = index) {
  const original = prefix + body;
  const caret = original.indexOf('<caret>');
  assert.notEqual(caret, -1);
  const source = original.replace('<caret>', '');
  return { source, items: getCompletions(metadata, source, caret) };
}
const labels = result => result.items.map(item => item.label);

test('condition first operand proposes valid ConditionField expressions', () => {
  const result = at("  $query->where()->firstCondition(<caret>);\n}");
  assert.deepEqual(labels(result), ['email', 'id']);
  assert.equal(result.items.find(item => item.label === 'email').insertText, "$query->getFieldForCondition('email')");
});
test('condition operator suggestions use all actual enum cases', () => {
  const result = at("  $query->where()->firstCondition($query->getFieldForCondition('email'), <caret>);\n}");
  assert.deepEqual(labels(result), OPERATORS);
  assert.equal(result.items.find(x => x.label === 'notEqual').insertText,
    '\\Small\\SwooleEntityManager\\QueryBuilder\\RelationalQueryBuilder\\Enum\\ConditionOperatorType::notEqual');
});
test('condition joins propose related entity ConditionField using alias', () => {
  const result = at("  $query->leftJoin('u', 'company', 'c')->endJoin();\n  $query->where()->firstCondition(<caret>);\n}");
  assert.ok(labels(result).includes('c.title'));
  assert.equal(result.items.find(item => item.label === 'c.title').insertText, "$query->getFieldForCondition('title', 'c')");
});
test('second condition call can locate where variable', () => {
  const result = at("  $where = $query->where();\n  $where->andCondition(<caret>);\n}");
  assert.ok(labels(result).includes('email'));
});
test('unknown conditions do not suggest misleading ORM fields', () => {
  const result = at('  $unknown->firstCondition(<caret>);\n}');
  assert.deepEqual(labels(result), []);
});
test('named placeholders are proposed for parameters', () => {
  const result = at("  $query->where()->firstCondition($query->getFieldForCondition('email'), ConditionOperatorType::equal, ':email');\n  $query->setParameter('<caret>', 1);\n}");
  assert.deepEqual(labels(result), ['email']);
  assert.equal(result.items[0].insertText, 'email');
});
test('explicit getFieldForCondition alias still resolves fields', () => {
  const result = at("  $query->leftJoin('u', 'company', 'c')->endJoin();\n  $query->getFieldForCondition('<caret>', 'c');\n}");
  assert.ok(labels(result).includes('title'));
});
test('typed manager member completion includes derived local methods', () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'sem-methods-'));
  try {
    fs.mkdirSync(path.join(temp, 'src'));
    fs.writeFileSync(path.join(temp, 'src', 'UserManager.php'), '<?php class UserManager { public function customLookup(): void {} protected function internal(): void {} }');
    const metadata = structuredClone(index);
    metadata.managers['Demo\\UserManager'].file = 'src/UserManager.php';
    const updated = withManagerMethods(metadata, temp);
    const result = at('  $manager-><caret>\n}', header, updated);
    assert.ok(labels(result).includes('customLookup'));
    assert.ok(labels(result).includes('createQueryBuilder'));
    assert.ok(!labels(result).includes('internal'));
  } finally { fs.rmSync(temp, { recursive: true, force: true }); }
});
test('traversing outside workspace is not allowed during manager discovery', () => {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sem-methods-sec-'));
  try {
    const metadata = structuredClone(index);
    metadata.managers['Demo\\UserManager'].file = '../private.php';
    assert.deepEqual(withManagerMethods(metadata, tmp).managerMethods, {});
  } finally { fs.rmSync(tmp, { recursive: true, force: true }); }
});
test('named fieldName argument and entityAlias resolve joined fields', () => {
  const result = at("  $query->leftJoin('u', 'company', 'c')->endJoin();\n  $query->getFieldForCondition(fieldName: '<caret>', entityAlias: 'c');\n}");
  assert.ok(labels(result).includes('title'));
});
test('named factory argument completes concrete managers', () => {
  const result = at('  $factory->get(managerClass: Us<caret>);\n}');
  assert.ok(labels(result).includes('UserManager'));
});
test('grouped PHP imports resolve referenced manager', () => {
  const prefix = `<?php\nuse Demo\\{UserManager as Custom, CompanyManager};\nfunction f(EntityManagerFactory $factory) {\n  $manager = $factory->get(Custom::class);\n  $query = $manager->createQueryBuilder('u');\n`;
  const result = at("  $query->getFieldForCondition('<caret>');\n}", prefix);
  assert.ok(labels(result).includes('email'));
});
test('typed promoted manager property resolves member methods', () => {
  const prefix = `<?php\nuse Demo\\UserManager;\nclass Handler { public function __construct(private readonly UserManager $manager) {}\n  public function handle(): void {\n`;
  const updated = { ...index, managerMethods: { 'Demo\\UserManager': ['customLookup'] } };
  const result = at('    $this->manager-><caret>\n  }\n}', prefix, updated);
  assert.ok(labels(result).includes('customLookup'));
});
test('operator completion after qualified enum prefix inserts only missing case', () => {
  const result = at("  $query->where()->firstCondition($query->getFieldForCondition('email'), ConditionOperatorType::<caret>);\n}");
  assert.equal(result.items.find(x => x.label === 'equal')?.insertText, 'equal');
});
test('manager method completion never triggers inside strings or comments', () => {
  const result = at('  // $manager-><caret>\n}', header);
  assert.deepEqual(labels(result), []);
  const inside = at('  $line = "$manager-><caret>";\n}', header);
  assert.deepEqual(labels(inside), []);
});
test('realpath traversal through a symlink cannot read external manager code', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'sem-method-link-'));
  const other = fs.mkdtempSync(path.join(os.tmpdir(), 'sem-method-secret-'));
  try {
    fs.writeFileSync(path.join(other, 'Manager.php'), '<?php public function secret(): void {}');
    fs.symlinkSync(other, path.join(root, 'linked'));
    const metadata = structuredClone(index);
    metadata.managers['Demo\\UserManager'].file = 'linked/Manager.php';
    assert.deepEqual(withManagerMethods(metadata, root).managerMethods, {});
  } finally {
    fs.rmSync(root, {recursive:true,force:true});
    fs.rmSync(other, {recursive:true,force:true});
  }
});

test('parameter completion remains in the current PHP function', () => {
  const prefix = [
    '<?php use Demo\\UserManager;',
    'function old(UserManager $manager) {',
    "  $query = $manager->createQueryBuilder('u');",
    "  $query->where()->firstCondition($query->getFieldForCondition('email'), ConditionOperatorType::equal, ':oldName');",
    '}',
    'function run(UserManager $manager) {',
    "  $query = $manager->createQueryBuilder('u');",
    "  $query->where()->firstCondition($query->getFieldForCondition('email'), ConditionOperatorType::equal, ':currentName');",
    ''].join('\n');
  const result = at("  $query->setParameter('<caret>', 1);\n}", prefix);
  assert.deepEqual(labels(result), ['currentName']);
});

test('parameter completion ignores commented placeholders', () => {
  const prefix = [
    '<?php use Demo\\UserManager;',
    'function run(UserManager $manager) {',
    "  $query = $manager->createQueryBuilder('u');",
    "  // $query->setParameter('commentedOut', 'ignored');",
    "  /* $query->where()->firstCondition(':hidden'); */",
    "  $query->where()->firstCondition($query->getFieldForCondition('email'), ConditionOperatorType::equal, ':visible');",
    ''].join('\n');
  const result = at("  $query->setParameter('<caret>', 1);\n}", prefix);
  assert.deepEqual(labels(result), ['visible']);
});
