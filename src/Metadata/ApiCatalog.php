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
                'createQueryBuilder' => ['returns' => 'QueryBuilder', 'aliasArgument' => 0, 'signature' => 'createQueryBuilder(?string $alias = null): QueryBuilder'],
                'createScallarQueryBuilder' => ['returns' => 'scalar', 'aliasArgument' => 0],
                'createStatsQueryBuilder' => ['returns' => 'stats', 'aliasArgument' => 0],
                'createUpdateBuilder' => ['returns' => 'update', 'aliasArgument' => 0],
                'createDeleteBuilder' => ['returns' => 'delete'],
                'getFieldForCondition' => ['fieldArgument' => 0, 'aliasArgument' => 1, 'signature' => 'getFieldForCondition(string $fieldName, ?string $entityAlias = null): ConditionField'],
                'addOrderBy' => ['fieldArgument' => 0, 'aliasArgument' => 1],
                'addFieldToUpdate' => ['fieldArgument' => 0],
                'join' => ['fromAliasArgument' => 0, 'relationArgument' => 1, 'aliasArgument' => 2],
                'innerJoin' => ['fromAliasArgument' => 0, 'relationArgument' => 1, 'aliasArgument' => 2],
                'leftJoin' => ['fromAliasArgument' => 0, 'relationArgument' => 1, 'aliasArgument' => 2, 'returns' => 'LeftJoinBuilder', 'signature' => 'leftJoin(string $fromAlias, string $relationAlias, ?string $alias = null): LeftJoinBuilder'],
                'rightJoin' => ['fromAliasArgument' => 0, 'relationArgument' => 1, 'aliasArgument' => 2],
                'select' => ['projectionArgument' => 0, 'aliasArgument' => 1, 'signature' => 'select(string $fields, ?string $entityAlias = null): self'],
                'addSelect' => ['projectionArgument' => 0, 'aliasArgument' => 1, 'signature' => 'addSelect(string $fields, ?string $entityAlias = null): self'],
                'count' => ['aggregateFieldArgument' => 0],
                'sum' => ['aggregateFieldArgument' => 0],
                'avg' => ['aggregateFieldArgument' => 0],
                'min' => ['aggregateFieldArgument' => 0],
                'max' => ['aggregateFieldArgument' => 0],
                'stringOperation' => ['aggregateExpressionArgument' => 0],
                'setParameter' => ['parameterArgument' => 0],
                'where' => ['returns' => 'Bracket', 'signature' => 'where(): Bracket'],
                'endJoin' => ['returns' => 'QueryBuilder', 'signature' => 'endJoin(): QueryBuilder'],
                'joinCondition' => ['returns' => 'Bracket', 'signature' => 'joinCondition(): Bracket'],
                'endWhere' => ['returns' => 'QueryBuilder', 'signature' => 'endWhere(): QueryBuilder'],
                'firstCondition' => ['operandArgument' => 0, 'operatorArgument' => 1, 'valueArgument' => 2, 'returns' => 'Bracket', 'signature' => 'firstCondition(string|int|QueryBuilder|ConditionField $var1, ConditionOperatorType $operator, string|int|array|QueryBuilder|ConditionField|null $var2 = null): Bracket'],
                'andCondition' => ['operandArgument' => 0, 'operatorArgument' => 1, 'valueArgument' => 2, 'returns' => 'Bracket'],
                'orCondition' => ['operandArgument' => 0, 'operatorArgument' => 1, 'valueArgument' => 2, 'returns' => 'Bracket'],
                'xorCondition' => ['operandArgument' => 0, 'operatorArgument' => 1, 'valueArgument' => 2, 'returns' => 'Bracket'],
            ],
        ];
    }
}
