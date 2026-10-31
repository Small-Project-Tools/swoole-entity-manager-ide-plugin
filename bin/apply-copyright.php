<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

$root = dirname(__DIR__);
$check = in_array('--check', $argv, true);
$header = "/* SMALL-IDE-COPYRIGHT\n * Copyright 2026 - Sébastien Kus\n * Under MIT license\n */";
$files = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS));
$changed = [];
foreach ($files as $file) {
    if (!$file instanceof SplFileInfo || !$file->isFile()) {
        continue;
    }
    $path = $file->getPathname();
    $relative = substr($path, strlen($root) + 1);
    if (!str_ends_with($path, '.php') && !in_array($relative, ['bin/sem-ide', 'bin/self-test'], true)) {
        continue;
    }
    if (preg_match('~(^|/)(vendor|\.git|var|dist)/~', $relative)) {
        continue;
    }
    $source = file_get_contents($path);
    if ($source === false) {
        continue;
    }
    $actual = preg_replace('~/\* SMALL-IDE-COPYRIGHT.*?\*/~s', $header, $source, 1, $replacements);
    if (!$replacements) {
        $actual = preg_replace('/(<\?php\s*)/', "$1$header\n", $source, 1);
    }
    if ($actual !== $source && $actual !== null) {
        $changed[] = $relative;
        if (!$check) {
            file_put_contents($path, $actual);
        }
    }
}
if ($changed !== []) {
    fwrite(STDERR, implode("\n", $changed) . "\n");
    exit($check ? 1 : 0);
}
echo "Copyright headers OK\n";
