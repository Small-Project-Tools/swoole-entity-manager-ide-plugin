<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

use Small\EntityManagerIde\Completion\CompletionService;

test('offers mapped fields for select projections across aliases', function (): void {
    $index = [
        'schemaVersion' => 1,
        'engine' => 'small/entity-manager-ide-core',
        'entities' => [
            'Sample\\Entity\\Person' => [
                'class' => 'Sample\\Entity\\Person', 'file' => 'Person.php', 'line' => 1,
                'fields' => ['id' => ['name' => 'id', 'primaryKey' => true, 'databaseName' => null, 'file' => 'Person.php', 'line' => 1]],
                'relations' => ['owner' => ['name' => 'owner', 'cardinality' => 'one', 'manager' => 'Sample\\Manager\\CompanyManager', 'file' => 'Person.php', 'line' => 2]],
            ],
            'Sample\\Entity\\Company' => [
                'class' => 'Sample\\Entity\\Company', 'file' => 'Company.php', 'line' => 1,
                'fields' => ['name' => ['name' => 'name', 'primaryKey' => false, 'databaseName' => null, 'file' => 'Company.php', 'line' => 1]],
                'relations' => [],
            ],
        ],
        'managers' => [
            'Sample\\Manager\\PersonManager' => ['class' => 'Sample\\Manager\\PersonManager', 'entity' => 'Sample\\Entity\\Person', 'connection' => null, 'file' => 'PersonManager.php', 'line' => 1],
            'Sample\\Manager\\CompanyManager' => ['class' => 'Sample\\Manager\\CompanyManager', 'entity' => 'Sample\\Entity\\Company', 'connection' => null, 'file' => 'CompanyManager.php', 'line' => 1],
        ],
        'api' => [], 'diagnostics' => [],
    ];
    $context = [
        'kind' => 'projection',
        'manager' => 'Sample\\Manager\\PersonManager',
        'rootAlias' => 'person',
        'joins' => [['fromAlias' => 'person', 'relation' => 'owner', 'alias' => 'company']],
    ];
    expect((new CompletionService())->complete($index, $context))->toBe([
        ['label' => 'company.name', 'kind' => 'projection'],
        ['label' => 'person.id', 'kind' => 'projection'],
    ]);
});
