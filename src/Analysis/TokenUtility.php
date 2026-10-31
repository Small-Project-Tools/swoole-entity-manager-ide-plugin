<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

namespace Small\EntityManagerIde\Analysis;

/** Token operations; no autoloading or execution of code being indexed. */
final class TokenUtility
{
    /** @param array<int, mixed> $tokens */
    public static function contents(array $tokens): string
    {
        return implode('', array_map(static fn (mixed $token): string => is_array($token) ? $token[1] : (string) $token, $tokens));
    }

    public static function value(mixed $token): string
    {
        return is_array($token) ? $token[1] : (string) $token;
    }

    public static function is(mixed $token, int $id): bool
    {
        return is_array($token) && $token[0] === $id;
    }

    /** @param array<int, mixed> $tokens */
    public static function nextSignificant(array $tokens, int $from): int
    {
        $max = count($tokens);
        while ($from < $max && is_array($tokens[$from]) && in_array($tokens[$from][0], [T_WHITESPACE, T_COMMENT, T_DOC_COMMENT], true)) {
            ++$from;
        }
        return $from;
    }

    /** @param array<int, mixed> $tokens */
    public static function previousSignificant(array $tokens, int $from): int
    {
        while ($from >= 0 && is_array($tokens[$from]) && in_array($tokens[$from][0], [T_WHITESPACE, T_COMMENT, T_DOC_COMMENT], true)) {
            --$from;
        }
        return $from;
    }

    /** @return list<string> */
    public static function splitTopLevel(string $source, string $separator = ','): array
    {
        $pieces = [];
        $current = '';
        $depth = 0;
        $quote = '';
        $escaped = false;
        for ($i = 0, $len = strlen($source); $i < $len; ++$i) {
            $char = $source[$i];
            if ($quote !== '') {
                $current .= $char;
                if ($escaped) {
                    $escaped = false;
                } elseif ($char === '\\') {
                    $escaped = true;
                } elseif ($char === $quote) {
                    $quote = '';
                }
                continue;
            }
            if ($char === '"' || $char === "'") {
                $quote = $char;
                $current .= $char;
                continue;
            }
            if (str_contains('([{', $char)) {
                ++$depth;
            } elseif (str_contains(')]}', $char)) {
                --$depth;
            }
            if ($char === $separator && $depth === 0) {
                $pieces[] = trim($current);
                $current = '';
            } else {
                $current .= $char;
            }
        }
        if (trim($current) !== '') {
            $pieces[] = trim($current);
        }
        return $pieces;
    }

    /** @param array<int, mixed> $tokens
     * @return array{string, int} */
    public static function until(array $tokens, int $from, string $delimiter): array
    {
        $items = [];
        $count = count($tokens);
        for ($i = $from; $i < $count; ++$i) {
            if (self::value($tokens[$i]) === $delimiter) {
                return [self::contents($items), $i];
            }
            $items[] = $tokens[$i];
        }
        return [self::contents($items), $count];
    }
}
