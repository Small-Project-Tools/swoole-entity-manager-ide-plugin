<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

namespace Small\EntityManagerIde\Release;

/**
 * Validate SemVer tags published by the IDE core release pipeline.
 *
 * A release candidate is a prerelease rather than a stable major release.
 */
final class ReleaseTag
{
    public static function isSupported(string $tag): bool
    {
        return preg_match(
            '/^(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-dev-[1-9][0-9]*|-RC[1-9][0-9]*)?$/D',
            $tag,
        ) === 1;
    }
}
