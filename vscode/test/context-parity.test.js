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

test('PHPDoc @var manager resolves query fields without an interpreter', () => {
  assert.deepEqual(labels([
    '<?php',
    'use Demo\\UserManager;',
    'function run() {',
    '  /** @var UserManager $manager */',
    '  $query = $manager->createQueryBuilder("u");',
    '  $query->getFieldForCondition("<caret>");',
    '}'
  ]), ['email', 'id']);
});

test('PHPDoc @var does not leak into a different function', () => {
  assert.deepEqual(labels([
    '<?php',
    'use Demo\\UserManager;',
    'function before() {',
    '  /** @var UserManager $manager */',
    '}',
    'function run() {',
    '  $query = $manager->createQueryBuilder("u");',
    '  $query->getFieldForCondition("<caret>");',
    '}'
  ]), []);
});

test('PHPDoc @var does not resolve a different manager variable', () => {
  assert.deepEqual(labels([
    '<?php',
    'use Demo\\UserManager;',
    'function run() {',
    '  /** @var UserManager $other */',
    '  $query = $manager->createQueryBuilder("u");',
    '  $query->getFieldForCondition("<caret>");',
    '}'
  ]), []);
});

test('delete builder completes mapped fields when assigned or inline', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'function run(UserManager $manager) {',
    "  $query = $manager->createDeleteBuilder('u');",
    "  $query->getFieldForCondition('<caret>');",
    '}'
  ]), ['email', 'id']);
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'function run(UserManager $manager) {',
    "  $manager->createDeleteBuilder('u')->getFieldForCondition('<caret>');",
    '}'
  ]), ['email', 'id']);
});

test('untyped controller accessor delegates to another local accessor', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'class Handler {',
    '  public function __construct(private UserManager $manager) {}',
    '  private function baseManager(): UserManager { return $this->manager; }',
    '  private function userManager(): AbstractManager { return $this->baseManager(); }',
    '  public function run() {',
    "    $query = $this->userManager()->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), ['email', 'id']);
});

test('accessor with conflicting return paths cannot choose one manager', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\{UserManager, CompanyManager};',
    'class Handler {',
    '  public function __construct(private UserManager $userManager, private CompanyManager $companyManager) {}',
    '  private function resolveManager(): AbstractManager {',
    '    if ($this->useCompany) { return $this->companyManager; }',
    '    return $this->userManager;',
    '  }',
    '  public function run() {',
    "    $query = $this->resolveManager()->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), []);
});

test('same-manager conditional returns resolve without choosing a branch', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'class Handler {',
    '  public function __construct(private UserManager $manager) {}',
    '  private function resolveManager(): AbstractManager {',
    '    if ($this->condition) { return $this->manager; }',
    '    return $this->manager;',
    '  }',
    '  public function run() {',
    "    $query = $this->resolveManager()->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), ['email', 'id']);
});

test('a recursive accessor does not offer speculative completions', () => {
  assert.deepEqual(labels([
    '<?php',
    'class Handler {',
    '  private function manager(): AbstractManager { return $this->other(); }',
    '  private function other(): AbstractManager { return $this->manager(); }',
    '  public function run() {',
    "    $query = $this->manager()->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), []);
});

test('comment and string @var lookalikes do not define a manager', () => {
  for (const declaration of [
    '  // @var UserManager $manager',
    '  $help = "@var UserManager $manager";',
    '  # @var UserManager $manager'
  ]) {
    assert.deepEqual(labels([
      '<?php use Demo\\UserManager;',
      'function run() {',
      declaration,
      "  $query = $manager->createQueryBuilder('u');",
      "  $query->getFieldForCondition('<caret>');",
      '}'
    ]), []);
  }
});

test('optional PHPDoc type resolves local mapped manager', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'function run() {',
    '  /** @var ?UserManager $manager */',
    "  $query = $manager->createQueryBuilder('u');",
    "  $query->getFieldForCondition('<caret>');",
    '}'
  ]), ['email','id']);
});

test('typed manager fields do not bleed into a subsequent class', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'class OldHandler {',
    '  private UserManager $manager;',
    '}',
    'class Handler {',
    '  public function run() {',
    "    $query = $this->manager->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), []);
});

test('self manager query builder resolves the containing mapped manager', () => {
  assert.deepEqual(labels([
    '<?php namespace Demo;',
    'class UserManager {',
    '  public function run() {',
    "    $query = $this->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), ['email', 'id']);
});

test('inline self manager query builder resolves fields', () => {
  assert.deepEqual(labels([
    '<?php namespace Demo;',
    'class UserManager {',
    '  public function run() {',
    "    $this->createQueryBuilder('u')->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), ['email', 'id']);
});

test('self manager never resolves the same basename from an unrelated namespace', () => {
  assert.deepEqual(labels([
    '<?php namespace Other;',
    'class UserManager {',
    '  public function run() {',
    "    $query = $this->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), []);
});

test('accessor factory result is accepted only from a Small factory', () => {
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'use Small\\SwooleEntityManager\\Factory\\EntityManagerFactory;',
    'class Handler {',
    '  public function __construct(private EntityManagerFactory $factory) {}',
    '  private function userManager(): AbstractManager { return $this->factory->get(UserManager::class); }',
    '  public function run() {',
    "    $query = $this->userManager()->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), ['email', 'id']);
  assert.deepEqual(labels([
    '<?php use Demo\\UserManager;',
    'class Handler {',
    '  public function __construct(private OtherFactory $factory) {}',
    '  private function userManager(): AbstractManager { return $this->factory->get(UserManager::class); }',
    '  public function run() {',
    "    $query = $this->userManager()->createQueryBuilder('u');",
    "    $query->getFieldForCondition('<caret>');",
    '  }',
    '}'
  ]), []);
});

test('factory from an unrelated namespace is never considered a Small factory', () => {
  assert.deepEqual(labels([
    '<?php',
    'use Demo\\UserManager;',
    'use Other\\EntityManagerFactory;',
    'function run(EntityManagerFactory $factory) {',
    '  $factory->get(<caret>);',
    '}'
  ]), []);
  assert.deepEqual(labels([
    '<?php',
    'use Demo\\UserManager;',
    'use Other\\EntityManagerFactory;',
    'function run(EntityManagerFactory $factory) {',
    '  $manager = $factory->get(UserManager::class);',
    "  $query = $manager->createQueryBuilder('u');",
    "  $query->getFieldForCondition('<caret>');",
    '}'
  ]), []);
});

test('aliased Small factory import continues to resolve managers', () => {
  assert.deepEqual(labels([
    '<?php',
    'use Demo\\UserManager;',
    'use Small\\SwooleEntityManager\\Factory\\EntityManagerFactory as SmallFactory;',
    'function run(SmallFactory $factory) {',
    '  $factory->get(<caret>);',
    '}'
  ]), ['CompanyManager', 'UserManager']);
});
