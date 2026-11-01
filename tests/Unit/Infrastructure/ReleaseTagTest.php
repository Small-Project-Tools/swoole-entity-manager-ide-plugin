<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

use Small\EntityManagerIde\Release\ReleaseTag;

test('accepts supported release tags', static function (string $tag): void {
    expect(ReleaseTag::isSupported($tag))->toBeTrue();
})->with([
    'stable 0.1.0' => '0.1.0',
    'development 0.2.5' => '0.2.5-dev-1',
    'stable 1.0.0' => '1.0.0',
    'candidate 1' => '1.0.0-RC1',
    'candidate 2' => '1.0.0-RC2',
    'candidate 15' => '1.0.0-RC15',
    'large stable version' => '12.35.678',
    'large development version' => '12.35.678-dev-123',
]);

test('rejects invalid release tags', static function (string $tag): void {
    expect(ReleaseTag::isSupported($tag))->toBeFalse();
})->with([
    'empty version' => '',
    'prefix v' => 'v1.0.0',
    'leading zero major' => '01.0.0',
    'leading zero minor' => '1.01.0',
    'leading zero patch' => '1.0.00',
    'missing patch' => '1.0',
    'zero candidate' => '1.0.0-RC0',
    'candidate leading zero' => '1.0.0-RC01',
    'missing candidate number' => '1.0.0-RC',
    'lowercase candidate' => '1.0.0-rc1',
    'zero development number' => '1.0.0-dev-0',
    'development leading zero' => '1.0.0-dev-01',
    'extra suffix' => '1.0.0-RC1-extra',
    'build metadata' => '1.0.0+metadata',
    'path traversal' => '1.0.0-RC1/../../bad',
    'shell metacharacters' => '1.0.0;rm -rf /',
    'trailing newline' => "1.0.0-RC1\n",
]);
