<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

use Small\EntityManagerIde\Analysis\TokenUtility;

test('splits nested attribute arguments without breaking arrays or quoted commas', function (): void {
    expect(TokenUtility::splitTopLevel("target: Owner::class, keysMapping: ['x' => 'y', 'z' => 'w'], note: 'a,b'"))
        ->toBe(["target: Owner::class", "keysMapping: ['x' => 'y', 'z' => 'w']", "note: 'a,b'"]);
});

test('handles strings with escaped quotes', function (): void {
    expect(TokenUtility::splitTopLevel('message: "a,\\"b", other: 123'))
        ->toHaveCount(2);
});
