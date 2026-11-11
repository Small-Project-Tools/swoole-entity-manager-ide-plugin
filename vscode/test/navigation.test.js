/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { definitionAt } = require('../src/navigation');

const index = {
  managers: {
    'Demo\\Manager\\UserManager': {class:'Demo\\Manager\\UserManager',entity:'Demo\\Entity\\User',file:'src/Manager/UserManager.php',line:11},
    'Demo\\Manager\\CompanyManager': {class:'Demo\\Manager\\CompanyManager',entity:'Demo\\Entity\\Company',file:'src/Manager/CompanyManager.php',line:8}
  },
  entities: {
    'Demo\\Entity\\User': {
      class:'Demo\\Entity\\User',
      fields:{ email:{file:'src/Entity/User.php',line:19}, id:{file:'src/Entity/User.php',line:15} },
      relations:{ company:{manager:'Demo\\Manager\\CompanyManager',file:'src/Entity/User.php',line:21} }
    },
    'Demo\\Entity\\Company': {
      class:'Demo\\Entity\\Company',
      fields:{title:{file:'src/Entity/Company.php',line:22}},
      relations:{}
    }
  }
};
const head = [
  '<?php',
  'use Demo\\Manager\\UserManager;',
  'function run(\\Small\\SwooleEntityManager\\Factory\\EntityManagerFactory $factory) {',
  '  $manager = $factory->get(UserManager::class);',
  "  $query = $manager->createQueryBuilder('u');"
];
function select(lines) {
  const text = [...head, ...lines].join('\n');
  const caret = text.indexOf('<caret>');
  assert.ok(caret >= 0);
  const source = text.replace('<caret>','');
  return definitionAt(index,source,caret);
}
test('navigate from typed factory argument to manager definition', () => {
  const found = select(['  $factory->get(UserMan<caret>ager::class);','}']);
  assert.deepEqual(found,{kind:'manager',name:'Demo\\Manager\\UserManager',entry:index.managers['Demo\\Manager\\UserManager']});
});
test('navigate from mapped field argument to entity property', () => {
  const found = select(["  $query->getFieldForCondition('ema<caret>il');",'}']);
  assert.deepEqual(found,{kind:'field',name:'email',entity:'Demo\\Entity\\User',entry:index.entities['Demo\\Entity\\User'].fields.email});
});
test('navigate from join relation to mapped relation property', () => {
  const found = select(["  $query->leftJoin('u', 'comp<caret>any', 'c');",'}']);
  assert.equal(found?.name,'company');
  assert.equal(found.entry.file,'src/Entity/User.php');
  assert.equal(found.entry.line,21);
});
test('navigate from joined field with explicit alias to target entity', () => {
  const found = select([
    "  $query->leftJoin('u', 'company', 'c')->endJoin();",
    "  $query->getFieldForCondition('tit<caret>le', 'c');", '}'
  ]);
  assert.equal(found?.name,'title');
  assert.equal(found.entry.file,'src/Entity/Company.php');
});
test('does not navigate for unknown mapped property', () => {
  assert.equal(select(["  $query->getFieldForCondition('unk<caret>nown');",'}']),null);
});
test('does not navigate for unrelated get call', () => {
  assert.equal(select(["  $cache->get(UserMan<caret>ager::class);",'}']),null);
});
test('does not navigate for invalid relation from alias', () => {
  assert.equal(select(["  $query->leftJoin('unknown', 'comp<caret>any', 'c');",'}']),null);
});

test('F12 resolves named field arguments irrespective of argument order', () => {
  const found = select([
    "  $query->leftJoin('u', 'company', 'c')->endJoin();",
    "  $query->getFieldForCondition(entityAlias: 'c', fieldName: 'tit<caret>le');", '}'
  ]);
  assert.equal(found?.name, 'title');
  assert.equal(found?.entry?.file, 'src/Entity/Company.php');
});

test('F12 resolves joined projections in select and addSelect', () => {
  const joined = "  $query->leftJoin('u', 'company', 'c')->endJoin();";
  const projected = select([joined, "  $query->select('c.tit<caret>le');", '}']);
  assert.equal(projected?.name, 'title');
  const named = select([joined, "  $query->addSelect(entityAlias: 'c', fields: 'tit<caret>le');", '}']);
  assert.equal(named?.name, 'title');
});

test('F12 resolves relation from named join arguments even when reordered', () => {
  const found = select(["  $query->innerJoin(relationAlias: 'comp<caret>any', fromAlias: 'u');", '}']);
  assert.equal(found?.name, 'company');
  assert.equal(found?.entry?.line, 21);
});

test('F12 resolves reordered named class-string in Small factory get', () => {
  const found = select(["  $factory->get(new: true, manager: UserMan<caret>ager::class);", '}']);
  assert.equal(found?.name, 'Demo\\Manager\\UserManager');
  assert.equal(found?.entry?.line, 11);
});
