/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { getCompletions } = require('../src/completion');

const index = {
  entities: {
    'Demo\\User': { fields: {id:{}, email:{}}, relations: {company:{manager:'Demo\\CompanyManager'}} },
    'Demo\\Company': { fields: {id:{}, title:{}}, relations: {} }
  },
  managers: {
    'Demo\\UserManager': {entity:'Demo\\User'},
    'Demo\\CompanyManager': {entity:'Demo\\Company'}
  },
  managerMethods: { 'Demo\\UserManager': ['findMatching','getCustomer'] }
};

function at(lines) {
  const raw = Array.isArray(lines) ? lines.join('\n') : lines;
  const caret = raw.indexOf('<caret>');
  assert.notEqual(caret, -1, 'missing caret marker');
  const code = raw.replace('<caret>','');
  return getCompletions(index,code,caret);
}
const labels = lines => at(lines).map(item => item.label);

test('query builder created from typed controller accessor resolves entity fields', () => {
  const code = [
    '<?php',
    'use Demo\\UserManager;',
    'class Handler {',
    '  public function __construct(private UserManager $manager) {}',
    '  private function userManager(): UserManager { return $this->manager; }',
    '  public function handle() {',
    "    $query = $this->userManager()->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ];
  assert.deepEqual(labels(code), ['email','id']);
});

test('query builder from promoted typed property resolves entity', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'class Handler {',
    '  public function __construct(private readonly UserManager $manager) {}',
    '  public function run() {',
    "    $query = $this->manager->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), ['email','id']);
});

test('local accessor returning typed manager can be assigned to a variable', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'class Handler {',
    '  private function userManager(): UserManager { return $this->manager; }',
    '  public function run() {',
    '    $manager = $this->userManager();',
    "    $query = $manager->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), ['email','id']);
});

test('accessor with abstract return type resolves typed property returned by body', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'class Handler {',
    '  public function __construct(private UserManager $manager) {}',
    '  private function userManager(): AbstractRelationnalManager { return $this->manager; }',
    '  public function run() {',
    "    $query = $this->userManager()->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), ['email','id']);
});

test('unrelated factory cannot invent Small manager query completions', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'function run(OtherFactory $cache) {',
    '  $manager = $cache->get(UserManager::class);',
    "  $query = $manager->createQueryBuilder('u');",
    "  $query->getFieldForCondition('<caret>');",
    '}'
  ]), []);
});

test('class-typed local factory continues providing expected completions', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'function run(\\Small\\SwooleEntityManager\\Factory\\EntityManagerFactory $factory) {',
    '  $manager = $factory->get(UserManager::class);',
    "  $query = $manager->createQueryBuilder('u');",
    "  $query->getFieldForCondition('<caret>');",
    '}'
  ]), ['email','id']);
});

test('query builder without root alias uses the mapped entity name', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'function run(UserManager $manager) {',
    '  $query = $manager->createQueryBuilder();',
    "  $query->getFieldForCondition('<caret>');",
    '}'
  ]), ['email','id']);
});

test('fluent join inside builder assignment is visible to later completions', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'function run(UserManager $manager) {',
    "  $query = $manager->createQueryBuilder('u')->leftJoin('u', 'company', 'c')->endJoin();",
    "  $query->getFieldForCondition('title', '<caret>');",
    '}'
  ]), ['c','u']);
});

test('resolves controller accessor without leaking unrelated method declarations', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'class Handler {',
    '  public function run() {',
    "    $query = $this->manager()->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}',
    'class Other {',
    '  public function manager(): UserManager {}',
    '}'
  ]), []);
});
