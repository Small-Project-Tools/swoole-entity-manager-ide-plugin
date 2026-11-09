/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { readIndex } = require('../src/core');
const { withManagerMethods } = require('../src/manager-methods');

test('native AST scans public manager methods across local inheritance and traits', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'sem-method-ast-'));
  const write = (name, lines) => {
    const full = path.join(root, name);
    fs.mkdirSync(path.dirname(full), {recursive:true});
    fs.writeFileSync(full, lines.join('\n'));
  };
  try {
    write('src/User.php', [
      '<?php',
      'namespace Demo;',
      'use Small\\SwooleEntityManager\\Entity\\Attribute\\OrmEntity;',
      '#[OrmEntity] class User {}'
    ]);
    write('src/LocalTrait.php', [
      '<?php namespace Demo;',
      'trait LocalTrait {',
      '  public function traitLookup(): void {}',
      '  private function secretTrait(): void {}',
      '}'
    ]);
    write('src/ParentManager.php', [
      '<?php namespace Demo;',
      'class ParentManager {',
      '  public function inherited(): void {}',
      '  protected function internal(): void {}',
      '  public static function factory(): void {}',
      '}'
    ]);
    write('src/UserManager.php', [
      '<?php',
      'namespace Demo;',
      'use Small\\SwooleEntityManager\\EntityManager\\Attribute\\Entity;',
      '#[Entity(User::class)]',
      'class UserManager extends ParentManager {',
      '  use LocalTrait;',
      '  // public function misleading() {}',
      '  private function secret(): void {}',
      '  public function findVerified(): void {}',
      '}'
    ]);
    const index = await readIndex({workspacePath:root, mode:'native'});
    assert.deepEqual(index.managerMethods['Demo\\UserManager'], ['findVerified','inherited','traitLookup']);
    assert.equal(withManagerMethods(index,root),index,'native AST snapshot must not be overwritten by a text regex');
  } finally { fs.rmSync(root,{recursive:true,force:true}); }
});
