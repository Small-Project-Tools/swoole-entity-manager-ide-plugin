#!/usr/bin/env php
<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */

declare(strict_types=1);

require_once __DIR__ . '/../src/Autoload.php';

use Small\EntityManagerIde\Release\ReleaseTag;

/** Publish or repair a GitLab release using the job token and the Releases API. */
function requiredEnvironment(string $name): string
{
    $value = getenv($name);
    if ($value === false || $value === '') {
        throw new RuntimeException("Missing environment variable: {$name}");
    }

    return $value;
}

/** @return array{0: int, 1: array<string, mixed>} */
function gitlabRequest(string $method, string $url, ?array $payload = null): array
{
    $args = [
        'curl', '--silent', '--show-error', '--max-time', '45',
        '--request', $method,
        '--header', 'JOB-TOKEN: '.requiredEnvironment('CI_JOB_TOKEN'),
        '--header', 'Content-Type: application/json',
        '--write-out', "\n%{http_code}",
    ];

    if ($payload !== null) {
        $args[] = '--data-binary';
        $args[] = json_encode($payload, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);
    }

    $args[] = $url;
    $process = proc_open($args, [
        0 => ['pipe', 'r'],
        1 => ['pipe', 'w'],
        2 => ['pipe', 'w'],
    ], $pipes);

    if (!is_resource($process)) {
        throw new RuntimeException('Could not start curl');
    }

    fclose($pipes[0]);
    $output = stream_get_contents($pipes[1]);
    $error = stream_get_contents($pipes[2]);
    fclose($pipes[1]);
    fclose($pipes[2]);
    $exit = proc_close($process);

    if ($exit !== 0 || $output === false) {
        throw new RuntimeException('GitLab request failed: '.trim((string) $error));
    }

    $split = strrpos($output, "\n");
    if ($split === false) {
        throw new RuntimeException('GitLab returned an invalid HTTP response');
    }

    $status = (int) substr($output, $split + 1);
    $data = json_decode(substr($output, 0, $split), true, 512, JSON_THROW_ON_ERROR);
    if (!is_array($data)) {
        throw new RuntimeException('GitLab returned an invalid JSON response');
    }

    return [$status, $data];
}

/** @param array{0: int, 1: array<string, mixed>} $response
 *  @return array<string, mixed> */
function requireStatus(array $response, int $expected): array
{
    [$status, $body] = $response;
    if ($status !== $expected) {
        $message = is_string($body['message'] ?? null) ? $body['message'] : json_encode($body, JSON_THROW_ON_ERROR);
        throw new RuntimeException("GitLab HTTP {$status}: {$message}");
    }

    return $body;
}

/** @return list<array{name:string,url:string,link_type:string}> */
function releaseLinks(string $api, string $projectId, string $tag): array
{
    return [
        [
            'name' => 'PhpStorm plugin (.zip)',
            'url' => "{$api}/projects/{$projectId}/packages/generic/small-entity-manager-phpstorm/{$tag}/small-entity-manager-phpstorm-{$tag}.zip",
            'link_type' => 'package',
        ],
        [
            'name' => 'IDE Core sources (.tar.gz)',
            'url' => "{$api}/projects/{$projectId}/packages/generic/small-entity-manager-ide-core/{$tag}/small-entity-manager-ide-core-{$tag}.tar.gz",
            'link_type' => 'package',
        ],
        [
            'name' => 'VS Code extension (.vsix)',
            'url' => "{$api}/projects/{$projectId}/packages/generic/small-entity-manager-vscode/{$tag}/small-entity-manager-vscode-{$tag}.vsix",
            'link_type' => 'package',
        ],
    ];
}

/** @param array<string, mixed> $release
 *  @param list<array{name:string,url:string,link_type:string}> $links */
function ensureReleaseLinks(string $url, array $release, array $links): void
{
    $known = $release['assets']['links'] ?? [];
    if (!is_array($known)) {
        throw new RuntimeException('GitLab release links are invalid');
    }

    foreach ($links as $link) {
        $current = null;
        foreach ($known as $candidate) {
            if (is_array($candidate) && ($candidate['name'] ?? null) === $link['name']) {
                $current = $candidate;
                break;
            }
        }

        if ($current === null) {
            requireStatus(gitlabRequest('POST', "{$url}/assets/links", $link), 201);
            continue;
        }

        if (($current['url'] ?? null) !== $link['url'] || ($current['link_type'] ?? null) !== $link['link_type']) {
            $id = $current['id'] ?? null;
            if (!is_int($id) || $id <= 0) {
                throw new RuntimeException('Existing release asset has no valid ID');
            }
            requireStatus(gitlabRequest('PUT', "{$url}/assets/links/{$id}", $link), 200);
        }
    }
}

/** @param list<array{name:string,url:string,link_type:string}> $links */
function publishRelease(string $url, string $tag, array $links): void
{
    $name = "Small Entity Manager IDE {$tag}";
    $description = "Automatic release for {$tag}. Assets are stored in the GitLab Generic Package Registry.";
    [$status, $existing] = gitlabRequest('GET', "{$url}/{$tag}");

    if ($status === 404) {
        requireStatus(gitlabRequest('POST', $url, [
            'tag_name' => $tag,
            'name' => $name,
            'description' => $description,
            'assets' => ['links' => $links],
        ]), 201);
    } elseif ($status === 200) {
        requireStatus(gitlabRequest('PUT', "{$url}/{$tag}", [
            'name' => $name,
            'description' => $description,
        ]), 200);
        ensureReleaseLinks("{$url}/{$tag}", $existing, $links);
    } else {
        requireStatus([$status, $existing], 200);
    }

    $release = requireStatus(gitlabRequest('GET', "{$url}/{$tag}"), 200);
    if (($release['tag_name'] ?? null) !== $tag) {
        throw new RuntimeException('GitLab returned a release for an unexpected tag');
    }

    $published = $release['assets']['links'] ?? [];
    if (!is_array($published)) {
        throw new RuntimeException('GitLab returned invalid release assets');
    }

    foreach ($links as $link) {
        $found = false;
        foreach ($published as $candidate) {
            if (is_array($candidate) && ($candidate['name'] ?? null) === $link['name'] && ($candidate['url'] ?? null) === $link['url']) {
                $found = true;
                break;
            }
        }
        if (!$found) {
            throw new RuntimeException("Release is missing an asset: {$link['name']}");
        }
    }
}

try {
    $tag = getenv('CI_COMMIT_TAG') ?: requiredEnvironment('RELEASE_TAG');
    if (!ReleaseTag::isSupported($tag)) {
        throw new RuntimeException('Release tag must follow project SemVer conventions');
    }

    $projectId = requiredEnvironment('CI_PROJECT_ID');
    if (preg_match('/^[1-9][0-9]*$/D', $projectId) !== 1) {
        throw new RuntimeException('CI_PROJECT_ID must be numeric');
    }

    $api = rtrim(requiredEnvironment('CI_API_V4_URL'), '/');
    $links = releaseLinks($api, $projectId, $tag);
    publishRelease("{$api}/projects/{$projectId}/releases", $tag, $links);
    echo "Release {$tag} published with ".count($links)." assets.\n";
} catch (Throwable $error) {
    fwrite(STDERR, $error->getMessage()."\n");
    exit(1);
}
