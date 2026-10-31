<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

namespace Small\EntityManagerIde\Analysis;

/**
 * Conservative tokenizer-based extraction of constant ORM attribute metadata.
 * @phpstan-type OrmAttribute array{name:string,args:string}
 * @phpstan-type OrmProperty array{name:string,attributes:list<OrmAttribute>,line:int}
 * @phpstan-type OrmSymbol array{name:string,kind:string,namespace:string,imports:array<string,string>,file:string,line:int,attributes:list<OrmAttribute>,properties:list<OrmProperty>,traits:list<string>,parent:string|null}
 */
final class PhpFileParser
{
    /** @return list<OrmSymbol> */
    public function parse(string $content, string $file): array
    {
        $tokens = token_get_all($content);
        $namespace = '';
        /** @var array<string, string> $imports */
        $imports = [];
        /** @var list<OrmSymbol> $classes */
        $classes = [];
        /** @var list<array{name:string,args:string}> $pending */
        $pending = [];
        /** @var OrmSymbol|null $current */
        $current = null;
        $depth = 0;
        $classDepth = -1;
        $expectBody = false;
        $length = count($tokens);
        for ($i = 0; $i < $length; ++$i) {
            $token = $tokens[$i];
            $value = TokenUtility::value($token);
            if ($value === '{') {
                ++$depth;
                if ($expectBody && $current !== null) {
                    $classDepth = $depth;
                    $expectBody = false;
                }
                continue;
            }
            if ($value === '}') {
                if ($current !== null && $depth === $classDepth) {
                    $classes[] = $current;
                    $current = null;
                    $classDepth = -1;
                }
                --$depth;
                continue;
            }
            if (TokenUtility::is($token, T_NAMESPACE) && $current === null) {
                [$ns, $end] = TokenUtility::until($tokens, $i + 1, ';');
                $namespace = trim($ns);
                $imports = [];
                $i = $end;
                continue;
            }
            if (TokenUtility::is($token, T_USE) && $current === null && !$expectBody) {
                [$source, $end] = TokenUtility::until($tokens, $i + 1, ';');
                $this->addImports($imports, $source);
                $i = $end;
                continue;
            }
            if (TokenUtility::is($token, T_ATTRIBUTE)) {
                [$attr, $end] = $this->readAttribute($tokens, $i);
                foreach ($this->parseAttributeList($attr, $namespace, $imports) as $attribute) {
                    $pending[] = $attribute;
                }
                $i = $end;
                continue;
            }
            if ((TokenUtility::is($token, T_CLASS) || TokenUtility::is($token, T_TRAIT)) && $current === null) {
                $prev = TokenUtility::previousSignificant($tokens, $i - 1);
                if ($prev >= 0 && (TokenUtility::is($tokens[$prev], T_NEW) || TokenUtility::is($tokens[$prev], T_DOUBLE_COLON))) {
                    $pending = [];
                    continue;
                }
                $nameIndex = TokenUtility::nextSignificant($tokens, $i + 1);
                if (!isset($tokens[$nameIndex]) || !TokenUtility::is($tokens[$nameIndex], T_STRING)) {
                    $pending = [];
                    continue;
                }
                $className = TokenUtility::value($tokens[$nameIndex]);
                $current = [
                    'name' => ltrim(($namespace === '' ? '' : $namespace . '\\') . $className, '\\'),
                    'kind' => TokenUtility::is($token, T_TRAIT) ? 'trait' : 'class',
                    'namespace' => $namespace,
                    'imports' => $imports,
                    'file' => $file,
                    'line' => is_array($token) ? $token[2] : 0,
                    'attributes' => $pending,
                    'properties' => [],
                    'traits' => [],
                    'parent' => null,
                ];
                $pending = [];
                $expectBody = true;
                [$declaration] = TokenUtility::until($tokens, $nameIndex + 1, '{');
                if (preg_match('/\bextends\s+([\\\\A-Za-z_][\\\\A-Za-z0-9_]*)/', $declaration, $match) === 1) {
                    $current['parent'] = $this->resolveName($match[1], $namespace, $imports);
                }
                continue;
            }
            if ($current !== null && $depth === $classDepth && $classDepth >= 0) {
                if (TokenUtility::is($token, T_USE)) {
                    [$traitSource, $end] = TokenUtility::until($tokens, $i + 1, ';');
                    if (!str_contains($traitSource, '{')) {
                        foreach (TokenUtility::splitTopLevel($traitSource) as $trait) {
                            $current['traits'][] = $this->resolveName(trim($trait), $namespace, $imports);
                        }
                        $i = $end;
                    }
                    $pending = [];
                    continue;
                }
                if (TokenUtility::is($token, T_FUNCTION) || TokenUtility::is($token, T_CONST)) {
                    $pending = [];
                    continue;
                }
                if (is_array($token) && in_array($token[0], [T_PUBLIC, T_PROTECTED, T_PRIVATE, T_VAR], true)) {
                    // A visibility modifier may introduce a method, not a property. Do not skip method braces.
                    $lookAhead = TokenUtility::nextSignificant($tokens, $i + 1);
                    while (isset($tokens[$lookAhead]) && is_array($tokens[$lookAhead]) && in_array($tokens[$lookAhead][0], [T_STATIC, T_FINAL, T_ABSTRACT, T_READONLY], true)) {
                        $lookAhead = TokenUtility::nextSignificant($tokens, $lookAhead + 1);
                    }
                    if (isset($tokens[$lookAhead]) && TokenUtility::is($tokens[$lookAhead], T_FUNCTION)) {
                        $pending = [];
                        continue;
                    }
                    [$declaration, $end] = TokenUtility::until($tokens, $i + 1, ';');
                    if (!str_contains($declaration, 'function ') && preg_match('/\$([A-Za-z_][A-Za-z0-9_]*)/', $declaration, $found) === 1) {
                        /** @var list<OrmProperty> $properties */
                        $properties = $current['properties'];
                        $properties[] = [
                            'name' => $found[1],
                            'attributes' => $pending,
                            'line' => $token[2],
                        ];
                        $current['properties'] = $properties;
                        $i = $end;
                    }
                    $pending = [];
                    continue;
                }
                if (TokenUtility::is($token, T_DOC_COMMENT)) {
                    continue;
                }
            }
        }
        return $classes;
    }

    /** @param array<string, string> $imports */
    private function addImports(array &$imports, string $source): void
    {
        $source = trim($source);
        if (preg_match('/^(function|const)\s/i', $source) === 1) {
            return;
        }
        if (preg_match('/^([^{}]+)\{([^{}]+)\}$/', $source, $group) === 1) {
            $prefix = rtrim(trim($group[1]), '\\');
            $source = implode(',', array_map(static fn (string $entry): string => $prefix . '\\' . trim($entry), TokenUtility::splitTopLevel($group[2])));
        }
        foreach (TokenUtility::splitTopLevel($source) as $entry) {
            if (preg_match('/^\s*([\\\\A-Za-z_][\\\\A-Za-z0-9_]*)(?:\s+as\s+([A-Za-z_][A-Za-z0-9_]*))?\s*$/i', $entry, $match) !== 1) {
                continue;
            }
            $fqcn = ltrim($match[1], '\\');
            $segments = explode('\\', $fqcn);
            $alias = $match[2] ?? end($segments);
            $imports[strtolower((string) $alias)] = $fqcn;
        }
    }

    /** @param array<int, mixed> $tokens
     * @return array{string, int} */
    private function readAttribute(array $tokens, int $from): array
    {
        $text = TokenUtility::value($tokens[$from]);
        $level = 1;
        for ($i = $from + 1, $count = count($tokens); $i < $count; ++$i) {
            $value = TokenUtility::value($tokens[$i]);
            if ($value === '[') {
                ++$level;
            } elseif ($value === ']') {
                --$level;
            }
            $text .= $value;
            if ($level === 0) {
                return [$text, $i];
            }
        }
        return [$text, count($tokens) - 1];
    }

    /** @param array<string, string> $imports
     * @return list<array{name:string,args:string}> */
    private function parseAttributeList(string $text, string $namespace, array $imports): array
    {
        $attributes = [];
        foreach (TokenUtility::splitTopLevel(substr($text, 2, -1)) as $entry) {
            if (preg_match('/^\s*([\\\\A-Za-z_][\\\\A-Za-z0-9_]*)(?:\s*\((.*)\))?\s*$/s', $entry, $match) !== 1) {
                continue;
            }
            $attributes[] = ['name' => $this->resolveName($match[1], $namespace, $imports), 'args' => $match[2] ?? ''];
        }
        return $attributes;
    }

    /** @param array<string, string> $imports */
    private function resolveName(string $name, string $namespace, array $imports): string
    {
        if (str_starts_with($name, '\\')) {
            return ltrim($name, '\\');
        }
        $parts = explode('\\', $name);
        $first = strtolower($parts[0]);
        if (isset($imports[$first])) {
            array_shift($parts);
            return $imports[$first] . ($parts === [] ? '' : '\\' . implode('\\', $parts));
        }
        return ($namespace === '' ? '' : $namespace . '\\') . $name;
    }

    /** @param array<string, string> $imports */
    public function resolveClassExpression(string $expression, string $namespace, array $imports): ?string
    {
        if (preg_match('/^\s*([\\\\A-Za-z_][\\\\A-Za-z0-9_]*)::class\s*$/', $expression, $match) === 1) {
            return $this->resolveName($match[1], $namespace, $imports);
        }
        return null;
    }
}
