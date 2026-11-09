/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { getCompletions } = require('../src/completion');

const index = {
  managers: {'Demo\\UserManager': {entity:'Demo\\User'}},
  entities: {'Demo\\User': {fields:{id:{},email:{}},relations:{}}},
  managerMethods: {'Demo\\UserManager': ['findActive']}
};
function suggestions(lines) {
  const input=lines.join('\n');
  const caret=input.indexOf('<caret>');
  assert.ok(caret>=0);
  return getCompletions(index,input.replace('<caret>',''),caret);
}
test('completes inline createQueryBuilder field for a typed manager', () => {
  const items=suggestions([
    '<?php use Demo\\UserManager;',
    'function run(UserManager $manager) {',
    "  $manager->createQueryBuilder('u')->getFieldForCondition('<caret>');",
    '}'
  ]);
  assert.deepEqual(items.map(x=>x.label),['email','id']);
});
test('completes inline builder accessed through typed controller method', () => {
  const items=suggestions([
    '<?php use Demo\\UserManager;',
    'class Handler {',
    '  public function __construct(private UserManager $manager) {}',
    '  private function userManager(): UserManager { return $this->manager; }',
    '  public function run() {',
    "    $this->userManager()->createQueryBuilder('u')->getFieldForCondition('<caret>');",
    '  }','}'
  ]);
  assert.deepEqual(items.map(x=>x.label),['email','id']);
});
test('completes public manager methods through typed controller accessor', () => {
  const items=suggestions([
    '<?php use Demo\\UserManager;',
    'class Handler {',
    '  private function userManager(): UserManager { return $this->manager; }',
    '  public function run() {',
    '    $this->userManager()-><caret>',
    '  }','}'
  ]);
  assert.ok(items.some(x=>x.label==='findActive'));
});
