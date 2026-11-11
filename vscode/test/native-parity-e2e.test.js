/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { readIndex } = require('../src/core');
const { getCompletions } = require('../src/completion');
const { definitionAt } = require('../src/navigation');

function syntheticWorkspace() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'sem-native-parity-'));
  const write = (name, source) => {
    const full = path.join(root, name);
    fs.mkdirSync(path.dirname(full), { recursive: true });
    fs.writeFileSync(full, source, 'utf8');
  };
  write('src/Entity/User.php', `<?php
namespace Demo\\Entity;
use Small\\SwooleEntityManager\\Entity\\Attribute\\{OrmEntity, Field, ToOne};
use Demo\\Manager\\CompanyManager;
#[OrmEntity]
class User {
  #[Field] private string $email;
  #[ToOne(toManager: CompanyManager::class)] private ?Company $company;
}`);
  write('src/Entity/Company.php', `<?php
namespace Demo\\Entity;
use Small\\SwooleEntityManager\\Entity\\Attribute\\Field;
class Company {
  #[Field] private string $title;
}`);
  write('src/Manager/UserManager.php', `<?php
namespace Demo\\Manager;
use Demo\\Entity\\User;
use Small\\SwooleEntityManager\\EntityManager\\Attribute\\Entity;
#[Entity(User::class)]
class UserManager {
  public function findVerified(): void {}
}`);
  write('src/Manager/CompanyManager.php', `<?php
namespace Demo\\Manager;
use Demo\\Entity\\Company;
use Small\\SwooleEntityManager\\EntityManager\\Attribute\\Entity;
#[Entity(Company::class)]
class CompanyManager {}`);
  return root;
}

function caretIn(lines) {
  const input = lines.join('\n');
  const offset = input.indexOf('<caret>');
  assert.ok(offset >= 0);
  return { source: input.replace('<caret>', ''), caret: offset };
}

test('real native AST snapshot drives joined projections and F12 without PHP CLI', async () => {
  const root = syntheticWorkspace();
  try {
    const index = await readIndex({
      workspacePath: root,
      phpExecutable: '/not-installed/php',
      mode: 'native'
    });
    assert.deepEqual(Object.keys(index.entities), ['Demo\\Entity\\Company','Demo\\Entity\\User']);
    assert.deepEqual(Object.keys(index.entities['Demo\\Entity\\Company'].fields), ['title']);
    assert.ok(index.managerMethods['Demo\\Manager\\UserManager'].includes('findVerified'));
    assert.deepEqual(index.diagnostics, []);

    const prefix = [
      '<?php use Demo\\Manager\\UserManager;',
      'function run(UserManager $manager) {',
      "  $query = $manager->createQueryBuilder('u');",
      "  $query->leftJoin('u', 'company', 'c')->endJoin();"
    ];
    const projection = caretIn([...prefix, "  $query->select('c.<caret>');", '}']);
    const items = getCompletions(index, projection.source, projection.caret);
    assert.deepEqual(items.map(item => item.label), ['c.title']);
    assert.equal(items[0].insertText, 'c.title');

    const mappedField = caretIn([...prefix, "  $query->getFieldForCondition('tit<caret>le', 'c');", '}']);
    const target = definitionAt(index, mappedField.source, mappedField.caret);
    assert.equal(target?.name, 'title');
    assert.equal(target?.entry?.file, 'src/Entity/Company.php');
    assert.ok(Number.isInteger(target.entry.line));

    const declaration = caretIn([
      '<?php',
      'use Demo\\Manager\\UserManager;',
      'function run(\\Small\\SwooleEntityManager\\Factory\\EntityManagerFactory $factory) {',
      '  $factory->get(UserMan<caret>ager::class);',
      '}'
    ]);
    assert.equal(definitionAt(index, declaration.source, declaration.caret)?.entry?.file,
      'src/Manager/UserManager.php');
  } finally {
    fs.rmSync(root,{recursive:true,force:true});
  }
});

test('native metadata remains scoped to the active function during completion', async () => {
  const root = syntheticWorkspace();
  try {
    const index = await readIndex({workspacePath:root,mode:'native'});
    const priorTyped = caretIn([
      '<?php use Demo\\Manager\\UserManager;',
      'function first(UserManager $manager) {}',
      'function second() {',
      "  $query = $manager->createQueryBuilder('u');",
      "  $query->getFieldForCondition('<caret>');",
      '}'
    ]);
    assert.deepEqual(getCompletions(index,priorTyped.source,priorTyped.caret),[]);
    const typed = caretIn([
      '<?php use Demo\\Manager\\UserManager;',
      'function first(UserManager $manager) {}',
      'function second(UserManager $manager) {',
      "  $query = $manager->createQueryBuilder('u');",
      "  $query->getFieldForCondition('<caret>');",
      '}'
    ]);
    assert.deepEqual(getCompletions(index,typed.source,typed.caret).map(item=>item.label), ['email']);
  } finally {
    fs.rmSync(root,{recursive:true,force:true});
  }
});
