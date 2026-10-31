<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

namespace Small\EntityManagerIde\Metadata;

/** Versioned descriptions of semantic arguments, independent of the host IDE. */
final class ApiCatalog
{
    /** @return array<string, mixed> */
    public static function definition(): array
    {
        return [
            'factory' => [
                'Small\\SwooleEntityManager\\Factory\\EntityManagerFactory::get' => [
                    'managerClassArgument' => 0,
                    'returns' => 'class-string-argument:0',
                    'documentation' => 'Get a manager; instances are cached unless new=true.',
                ],
                'Small\\SwooleEntityManagerBundle\\Contract\\EntityManagerFactoryInterface::get' => [
                    'managerClassArgument' => 0,
                    'returns' => 'class-string-argument:0',
                    'documentation' => 'Resolve a manager through the Symfony bundle factory.',
                ],
                'Small\\SwooleEntityManagerBundle\\Factory\\EntityManagerFactory::get' => [
                    'managerClassArgument' => 0,
                    'returns' => 'class-string-argument:0',
                    'documentation' => 'Resolve a manager through the Symfony bundle factory.',
                ],
            ],
            'query' => [
                'createQueryBuilder' => ['returns' => 'select', 'aliasArgument' => 0],
                'createScallarQueryBuilder' => ['returns' => 'scalar', 'aliasArgument' => 0],
                'createStatsQueryBuilder' => ['returns' => 'stats', 'aliasArgument' => 0],
                'createUpdateBuilder' => ['returns' => 'update', 'aliasArgument' => 0],
                'createDeleteBuilder' => ['returns' => 'delete'],
                'getFieldForCondition' => ['fieldArgument' => 0, 'aliasArgument' => 1],
                'addOrderBy' => ['fieldArgument' => 0, 'aliasArgument' => 1],
                'addFieldToUpdate' => ['fieldArgument' => 0],
                'join' => ['fromAliasArgument' => 0, 'relationArgument' => 1, 'aliasArgument' => 2],
                'innerJoin' => ['fromAliasArgument' => 0, 'relationArgument' => 1, 'aliasArgument' => 2],
                'leftJoin' => ['fromAliasArgument' => 0, 'relationArgument' => 1, 'aliasArgument' => 2],
                'rightJoin' => ['fromAliasArgument' => 0, 'relationArgument' => 1, 'aliasArgument' => 2],
                'select' => ['projectionArgument' => 0, 'aliasArgument' => 1],
                'addSelect' => ['projectionArgument' => 0, 'aliasArgument' => 1],
                'count' => ['aggregateFieldArgument' => 0],
                'sum' => ['aggregateFieldArgument' => 0],
                'avg' => ['aggregateFieldArgument' => 0],
                'min' => ['aggregateFieldArgument' => 0],
                'max' => ['aggregateFieldArgument' => 0],
                'stringOperation' => ['aggregateExpressionArgument' => 0],
                'setParameter' => ['parameterArgument' => 0],
            ],
        ];
    }
}
