<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

namespace Small\EntityManagerIde\Analysis;

use RecursiveDirectoryIterator;
use RecursiveIteratorIterator;
use Small\EntityManagerIde\Metadata\ApiCatalog;
use SplFileInfo;

/** An offline, read-only indexer: it never requires or evaluates project PHP files. */
final class ProjectIndexer
{
    private const ENTITY = 'Small\\SwooleEntityManager\\EntityManager\\Attribute\\Entity';
    private const CONNECTION = 'Small\\SwooleEntityManager\\EntityManager\\Attribute\\Connection';
    private const ORM_ENTITY = 'Small\\SwooleEntityManager\\Entity\\Attribute\\OrmEntity';
    private const FIELD = 'Small\\SwooleEntityManager\\Entity\\Attribute\\Field';
    private const PRIMARY_KEY = 'Small\\SwooleEntityManager\\Entity\\Attribute\\PrimaryKey';
    private const TO_ONE = 'Small\\SwooleEntityManager\\Entity\\Attribute\\ToOne';
    private const TO_MANY = 'Small\\SwooleEntityManager\\Entity\\Attribute\\ToMany';

    public function __construct(private readonly PhpFileParser $parser = new PhpFileParser())
    {
    }

    /** @return array<string, mixed> */
    public function index(string $root): array
    {
        $realRoot = realpath($root);
        if ($realRoot === false || !is_dir($realRoot)) {
            throw new \InvalidArgumentException('Project root does not exist: ' . $root);
        }
        /** @var array<string, array<string, mixed>> $symbols */
        $symbols = [];
        /** @var list<array<string, mixed>> $diagnostics */
        $diagnostics = [];
        $paths = [];
        $iterator = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($realRoot, RecursiveDirectoryIterator::SKIP_DOTS));
        foreach ($iterator as $file) {
            if (!$file instanceof SplFileInfo || !$file->isFile() || strtolower($file->getExtension()) !== 'php') {
                continue;
            }
            $path = $file->getPathname();
            $relative = ltrim(substr($path, strlen($realRoot)), DIRECTORY_SEPARATOR);
            $parts = explode(DIRECTORY_SEPARATOR, $relative);
            if (array_intersect($parts, ['vendor', '.git', 'node_modules', '.idea', '.vscode', 'cache', 'var']) !== []) {
                continue;
            }
            $paths[$relative] = $path;
        }
        ksort($paths, SORT_STRING);
        foreach ($paths as $relative => $path) {
            $source = file_get_contents($path);
            if ($source === false) {
                $diagnostics[] = ['code' => 'unreadable-file', 'file' => $relative];
                continue;
            }
            try {
                foreach ($this->parser->parse($source, $relative) as $symbol) {
                    $symbols[(string) $symbol['name']] = $symbol;
                }
            } catch (\Throwable $error) {
                $diagnostics[] = ['code' => 'parse-error', 'file' => $relative, 'message' => $error->getMessage()];
            }
        }
        $entities = [];
        $managers = [];
        foreach ($symbols as $name => $symbol) {
            if ($symbol['kind'] !== 'class') {
                continue;
            }
            if ($this->attribute($symbol['attributes'], self::ORM_ENTITY) !== null) {
                $fields = [];
                $relations = [];
                $visited = [];
                $this->collectProperties($symbol, $symbols, $fields, $relations, $visited);
                ksort($fields);
                ksort($relations);
                $entities[$name] = [
                    'class' => $name,
                    'file' => $symbol['file'],
                    'line' => $symbol['line'],
                    'fields' => $fields,
                    'relations' => $relations,
                ];
            }
            $entityAttribute = $this->attribute($symbol['attributes'], self::ENTITY);
            if ($entityAttribute !== null) {
                $entity = $this->classArgument($entityAttribute, $symbol, 0, 'entityClass');
                if ($entity === null) {
                    $diagnostics[] = ['code' => 'unresolved-manager-entity', 'file' => $symbol['file'], 'class' => $name];
                    continue;
                }
                $connectionAttribute = $this->attribute($symbol['attributes'], self::CONNECTION);
                $connection = $connectionAttribute === null ? null : $this->stringArgument($connectionAttribute, 0, 'connectionName');
                $managers[$name] = [
                    'class' => $name,
                    'entity' => $entity,
                    'connection' => $connection,
                    'file' => $symbol['file'],
                    'line' => $symbol['line'],
                ];
            }
        }
        ksort($entities);
        ksort($managers);
        foreach ($managers as $manager) {
            if (!isset($entities[$manager['entity']])) {
                $diagnostics[] = [
                    'code' => 'entity-not-indexed',
                    'file' => $manager['file'],
                    'class' => $manager['class'],
                    'entity' => $manager['entity'],
                ];
            }
        }
        return [
            'schemaVersion' => 1,
            'engine' => 'small/entity-manager-ide-core',
            'entities' => $entities,
            'managers' => $managers,
            'api' => ApiCatalog::definition(),
            'diagnostics' => $diagnostics,
        ];
    }

    /** @param list<array{name:string,args:string}> $attributes
     * @return array{name:string,args:string}|null */
    private function attribute(array $attributes, string $name): ?array
    {
        foreach ($attributes as $attribute) {
            if (strcasecmp($attribute['name'], $name) === 0) {
                return $attribute;
            }
        }
        return null;
    }

    /** @param array<string, mixed> $symbol
     * @param array<string, array<string, mixed>> $symbols
     * @param array<string, array<string, mixed>> $fields
     * @param array<string, array<string, mixed>> $relations
     * @param array<string, bool> $visited */
    private function collectProperties(array $symbol, array $symbols, array &$fields, array &$relations, array &$visited): void
    {
        $name = (string) $symbol['name'];
        if (isset($visited[$name])) {
            return;
        }
        $visited[$name] = true;
        $ancestors = [];
        if (is_string($symbol['parent'])) {
            $ancestors[] = $symbol['parent'];
        }
        foreach ($symbol['traits'] as $trait) {
            $ancestors[] = $trait;
        }
        foreach ($ancestors as $ancestor) {
            if (isset($symbols[$ancestor])) {
                $this->collectProperties($symbols[$ancestor], $symbols, $fields, $relations, $visited);
            }
        }
        foreach ($symbol['properties'] as $property) {
            $propertyName = (string) $property['name'];
            foreach ($property['attributes'] as $attribute) {
                $attributeName = $attribute['name'];
                if (in_array($attributeName, [self::FIELD, self::PRIMARY_KEY], true)) {
                    $fields[$propertyName] = [
                        'name' => $propertyName,
                        'primaryKey' => $attributeName === self::PRIMARY_KEY,
                        'databaseName' => $this->stringArgument($attribute, $attributeName === self::FIELD ? 3 : 0, 'dbFieldName'),
                        'file' => $symbol['file'],
                        'line' => $property['line'],
                    ];
                }
                if (in_array($attributeName, [self::TO_ONE, self::TO_MANY], true)) {
                    $relations[$propertyName] = [
                        'name' => $propertyName,
                        'cardinality' => $attributeName === self::TO_ONE ? 'one' : 'many',
                        'manager' => $this->classArgument($attribute, $symbol, 0, 'toManager'),
                        'file' => $symbol['file'],
                        'line' => $property['line'],
                    ];
                }
            }
        }
    }

    /** @param array{name:string,args:string} $attribute
     * @param array<string, mixed> $symbol */
    private function classArgument(array $attribute, array $symbol, int $position, string $key): ?string
    {
        $expression = $this->argument($attribute['args'], $position, $key);
        if ($expression === null) {
            return null;
        }
        return $this->parser->resolveClassExpression($expression, (string) $symbol['namespace'], $symbol['imports']);
    }

    /** @param array{name:string,args:string} $attribute */
    private function stringArgument(array $attribute, int $position, string $key): ?string
    {
        $expression = $this->argument($attribute['args'], $position, $key);
        if ($expression !== null && preg_match('/^(["\'])(.*?)\1$/s', $expression, $matches) === 1) {
            return stripcslashes($matches[2]);
        }
        return null;
    }

    private function argument(string $source, int $position, string $key): ?string
    {
        $positional = [];
        foreach (TokenUtility::splitTopLevel($source) as $piece) {
            if (preg_match('/^([a-zA-Z_][a-zA-Z_0-9]*)\s*:(?!:)\s*(.+)$/s', $piece, $matches) === 1) {
                if ($matches[1] === $key) {
                    return trim($matches[2]);
                }
            } else {
                $positional[] = $piece;
            }
        }
        return $positional[$position] ?? null;
    }
}
