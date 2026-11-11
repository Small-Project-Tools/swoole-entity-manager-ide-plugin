/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { readIndex } = require('../src/core');

function fixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'sem-native-index-'));
  const write = (file,content) => {
    const abs = path.join(root,file);
    fs.mkdirSync(path.dirname(abs),{recursive:true});
    fs.writeFileSync(abs,content);
  };
  write('src/Entity/HasTimestamps.php', `<?php
namespace Demo\\Entity;
use Small\\SwooleEntityManager\\Entity\\Attribute\\Field;
trait HasTimestamps { #[Field] protected ?\\DateTimeImmutable $createdAt = null; }`);
  write('src/Entity/User.php', `<?php
namespace Demo\\Entity;
use Small\\SwooleEntityManager\\Entity\\Attribute\\{OrmEntity, Field, PrimaryKey, ToOne};
use Demo\\Manager\\CompanyManager;
#[OrmEntity]
class User {
 use HasTimestamps;
 #[PrimaryKey(dbFieldName:'user_id')]
 private ?int $id = null;
 #[Field(dbFieldName: 'user_email')]
 private string $email;
 #[ToOne(toManager:CompanyManager::class)]
 private ?Company $company = null;
}`);
  write('src/Entity/Company.php', `<?php
namespace Demo\\Entity;
use Small\\SwooleEntityManager\\Entity\\Attribute\\{OrmEntity, Field, PrimaryKey};
#[OrmEntity]
class Company {
 #[PrimaryKey] private int $id;
 #[Field] private string $title;
}`);
  write('src/Manager/UserManager.php', `<?php
namespace Demo\\Manager;
use Demo\\Entity\\User as Account;
use Small\\SwooleEntityManager\\EntityManager\\Attribute\\{Entity,Connection};
#[Entity(entityClass: Account::class)]
#[Connection('app')]
class UserManager {public function findVerified(): array { return []; }}`);
  write('src/Manager/CompanyManager.php', `<?php
namespace Demo\\Manager;
use Demo\\Entity\\Company;
use Small\\SwooleEntityManager\\EntityManager\\Attribute\\Entity;
#[Entity(Company::class)] class CompanyManager {}`);
  write('vendor/Invisible.php', `<?php #[OrmEntity] class Invisible {}`);
  return {root,write};
}

test('native AST indexing maps managers, fields, imports, traits and relations without PHP', async () => {
  const {root}=fixture();
  try {
    const result=await readIndex({workspacePath:root,mode:'native'});
    assert.equal(result.schemaVersion,1);
    assert.equal(result.engine,'small/entity-manager-ide-core');
    assert.deepEqual(Object.keys(result.entities),['Demo\\Entity\\Company','Demo\\Entity\\User']);
    assert.equal(result.managers['Demo\\Manager\\UserManager'].entity,'Demo\\Entity\\User');
    assert.equal(result.managers['Demo\\Manager\\UserManager'].connection,'app');
    assert.deepEqual(Object.keys(result.entities['Demo\\Entity\\User'].fields),['createdAt','email','id']);
    assert.equal(result.entities['Demo\\Entity\\User'].fields.id.databaseName,'user_id');
    assert.equal(result.entities['Demo\\Entity\\User'].fields.email.databaseName,'user_email');
    assert.equal(result.entities['Demo\\Entity\\User'].relations.company.manager,'Demo\\Manager\\CompanyManager');
    assert.deepEqual(result.diagnostics,[]);
  } finally {fs.rmSync(root,{recursive:true,force:true});}
});
test('native AST scanner ignores vendor files, symlinks and malformed unrelated PHP', async () => {
  const {root,write}=fixture();
  const outside=fs.mkdtempSync(path.join(os.tmpdir(),'sem-external-'));
  try {
    write('src/unknown.php','<?php function incomplete( {{{');
    fs.writeFileSync(path.join(outside,'External.php'),'<?php #[OrmEntity] class External {}');
    fs.symlinkSync(outside,path.join(root,'linked'));
    const result=await readIndex({workspacePath:root,mode:'native'});
    assert.equal(result.entities['External'],undefined);
    assert.equal(result.entities['Invisible'],undefined);
    assert.ok(result.entities['Demo\\Entity\\User']);
  } finally {fs.rmSync(root,{recursive:true,force:true});fs.rmSync(outside,{recursive:true,force:true});}
});

test('native index includes mapped legacy entity without explicit OrmEntity marker', async () => {
  const {root,write} = fixture();
  try {
    write('src/Entity/Legacy.php', `<?php
namespace Demo\\Entity;
use Small\\SwooleEntityManager\\Entity\\Attribute\\Field;
class Legacy {
    use HasTimestamps;
    #[Field] private string $legacyCode;
}`);
    write('src/Manager/LegacyManager.php', `<?php
namespace Demo\\Manager;
use Demo\\Entity\\Legacy;
use Small\\SwooleEntityManager\\EntityManager\\Attribute\\Entity;
#[Entity(entityClass: Legacy::class)]
class LegacyManager {}`);
    const result = await readIndex({workspacePath:root,mode:'native'});
    assert.ok(result.entities['Demo\\Entity\\Legacy']);
    assert.ok(result.entities['Demo\\Entity\\Legacy'].fields.legacyCode);
    assert.ok(result.entities['Demo\\Entity\\Legacy'].fields.createdAt);
    assert.equal(result.managers['Demo\\Manager\\LegacyManager'].entity,'Demo\\Entity\\Legacy');
    assert.equal(result.diagnostics.some(x => x.code === 'entity-not-indexed' && x.class === 'Demo\\Manager\\LegacyManager'),false);
  } finally {fs.rmSync(root,{recursive:true,force:true});}
});

test('unmapped legacy class is not indexed as an ORM entity', async () => {
  const {root,write} = fixture();
  try {
    write('src/Entity/Unmapped.php', `<?php
namespace Demo\\Entity;
use Small\\SwooleEntityManager\\Entity\\Attribute\\Field;
class Unmapped { #[Field] private int $secret; }`);
    const result = await readIndex({workspacePath:root,mode:'native'});
    assert.equal(result.entities['Demo\\Entity\\Unmapped'],undefined);
  } finally {fs.rmSync(root,{recursive:true,force:true});}
});

test('missing mapped class remains an unresolved entity diagnostic', async () => {
  const {root,write} = fixture();
  try {
    write('src/Manager/MissingManager.php', `<?php
namespace Demo\\Manager;
use Demo\\Entity\\Missing;
use Small\\SwooleEntityManager\\EntityManager\\Attribute\\Entity;
#[Entity(entityClass: Missing::class)]
class MissingManager {}`);
    const result=await readIndex({workspacePath:root,mode:'native'});
    assert.equal(result.entities['Demo\\Entity\\Missing'],undefined);
    assert.ok(result.diagnostics.some(x => x.code === 'entity-not-indexed' && x.class === 'Demo\\Manager\\MissingManager'));
  } finally {fs.rmSync(root,{recursive:true,force:true});}
});
