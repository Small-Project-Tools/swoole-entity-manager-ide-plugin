<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

use Small\EntityManagerIde\Release\ReleaseTag;

foreach ([
    '0.1.0',
    '0.2.5-dev-1',
    '1.0.0',
    '1.0.0-RC1',
    '1.0.0-RC2',
    '1.0.0-RC15',
    '12.35.678',
    '12.35.678-dev-123',
] as $tag) {
    it("accepts release tag {$tag}", function () use ($tag): void {
        expect(ReleaseTag::isSupported($tag))->toBeTrue();
    });
}

foreach ([
    '',
    'v1.0.0',
    '01.0.0',
    '1.01.0',
    '1.0.00',
    '1.0',
    '1.0.0-RC0',
    '1.0.0-RC01',
    '1.0.0-RC',
    '1.0.0-rc1',
    '1.0.0-dev-0',
    '1.0.0-dev-01',
    '1.0.0-RC1-extra',
    '1.0.0+metadata',
    '1.0.0-RC1/../../bad',
    '1.0.0;rm -rf /',
    "1.0.0-RC1\n",
] as $tag) {
    it('rejects unsupported release tag '.bin2hex($tag), function () use ($tag): void {
        expect(ReleaseTag::isSupported($tag))->toBeFalse();
    });
}
