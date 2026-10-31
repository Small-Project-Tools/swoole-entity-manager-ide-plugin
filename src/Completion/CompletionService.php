<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

namespace Small\EntityManagerIde\Completion;

/** IDE-agnostic request: kind, manager/entity, optional query alias and ordered joins. */
final class CompletionService
{
    /**
     * @param array<string, mixed> $index
     * @param array<string, mixed> $context
     * @return list<array<string, mixed>>
     */
    public function complete(array $index, array $context): array
    {
        $kind = $context['kind'] ?? '';
        if ($kind === 'manager') {
            return $this->items(array_keys($index['managers'] ?? []), 'manager');
        }
        if ($kind === 'parameter') {
            return $this->items($context['parameters'] ?? [], 'parameter');
        }
        $manager = $context['manager'] ?? null;
        $entity = $context['entity'] ?? ($index['managers'][$manager]['entity'] ?? null);
        if (!is_string($entity) || !isset($index['entities'][$entity])) {
            return [];
        }
        $lastSeparator = strrpos($entity, '\\');
        $mainAlias = $context['rootAlias'] ?? lcfirst($lastSeparator === false ? $entity : substr($entity, $lastSeparator + 1));
        $aliases = [$mainAlias => $entity];
        foreach ($context['joins'] ?? [] as $join) {
            if (!is_array($join)) {
                continue;
            }
            $fromAlias = $join['fromAlias'] ?? null;
            $relation = $join['relation'] ?? null;
            $alias = $join['alias'] ?? $relation;
            if (!is_string($fromAlias) || !is_string($relation) || !is_string($alias) || !isset($aliases[$fromAlias])) {
                continue;
            }
            $fromEntity = $aliases[$fromAlias];
            $targetManager = $index['entities'][$fromEntity]['relations'][$relation]['manager'] ?? null;
            $target = $index['managers'][$targetManager]['entity'] ?? null;
            if (is_string($target) && isset($index['entities'][$target])) {
                $aliases[$alias] = $target;
            }
        }
        if ($kind === 'alias') {
            return $this->items(array_keys($aliases), 'alias');
        }
        $alias = $context['alias'] ?? $mainAlias;
        if (!is_string($alias) || !isset($aliases[$alias])) {
            return [];
        }
        $relatedEntity = $index['entities'][$aliases[$alias]];
        if ($kind === 'field') {
            return $this->items(array_keys($relatedEntity['fields']), 'field');
        }
        if ($kind === 'relation') {
            return $this->items(array_keys($relatedEntity['relations']), 'relation');
        }
        return [];
    }

    /** @param list<string> $names
     * @return list<array{label:string,kind:string}> */
    private function items(array $names, string $kind): array
    {
        sort($names, SORT_STRING);
        return array_map(static fn (string $name): array => ['label' => $name, 'kind' => $kind], $names);
    }
}
