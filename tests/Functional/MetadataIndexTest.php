<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

use Small\EntityManagerIde\Analysis\ProjectIndexer;
use Small\EntityManagerIde\Completion\CompletionService;

function fixtureIndex(): array
{
    return (new ProjectIndexer())->index(__DIR__ . '/Fixtures/Project');
}

test('indexes local managers, traits and ORM attributes without executing project code', function (): void {
    $index = fixtureIndex();
    $user = $index['entities']['Demo\\Entity\\User'];
    expect($index['schemaVersion'])->toBe(1)
        ->and($index['diagnostics'])->toBe([])
        ->and(array_keys($user['fields']))->toBe(['companyId', 'createdAt', 'email', 'id'])
        ->and($user['fields']['id']['primaryKey'])->toBeTrue()
        ->and($user['fields']['email']['databaseName'])->toBe('email_address')
        ->and($user['relations']['company']['manager'])->toBe('Demo\\EntityManager\\CompanyManager')
        ->and($index['managers']['Demo\\EntityManager\\UserManager']['connection'])->toBe('app');
});

test('offers field, relation and joined alias suggestions', function (): void {
    $index = fixtureIndex();
    $completer = new CompletionService();
    $base = ['manager' => 'Demo\\EntityManager\\UserManager', 'rootAlias' => 'user'];
    expect(array_column($completer->complete($index, $base + ['kind' => 'field']), 'label'))
        ->toBe(['companyId', 'createdAt', 'email', 'id']);
    expect(array_column($completer->complete($index, $base + ['kind' => 'relation']), 'label'))
        ->toBe(['company']);
    $withJoin = $base + ['kind' => 'field', 'alias' => 'owner', 'joins' => [['fromAlias' => 'user', 'relation' => 'company', 'alias' => 'owner']]];
    expect(array_column($completer->complete($index, $withJoin), 'label'))->toBe(['id', 'name']);
    expect($completer->complete($index, $base + ['kind' => 'field', 'alias' => 'unknown']))->toBe([]);
});

test('provides a stable query and factory semantic contract', function (): void {
    $index = fixtureIndex();
    expect($index['api']['query']['getFieldForCondition']['fieldArgument'])->toBe(0)
        ->and($index['api']['query']['join']['relationArgument'])->toBe(1)
        ->and($index['api']['factory']['Small\\SwooleEntityManagerBundle\\Contract\\EntityManagerFactoryInterface::get']['returns'])
        ->toBe('class-string-argument:0');
});

test('rejects missing directories rather than silently producing an empty index', function (): void {
    expect(fn () => (new ProjectIndexer())->index(__DIR__ . '/missing'))
        ->toThrow(InvalidArgumentException::class);
});
