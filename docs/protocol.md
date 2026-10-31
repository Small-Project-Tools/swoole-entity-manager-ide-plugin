# IDE protocol v1

The core service emits a single JSON document on stdout and writes errors only on stderr. The output is UTF-8 JSON; no project source is evaluated or included verbatim.

## `index` response

```json
{
  "schemaVersion": 1,
  "engine": "small/entity-manager-ide-core",
  "entities": {
    "App\\Entity\\User": {
      "class": "App\\Entity\\User", "file": "src/Entity/User.php", "line": 9,
      "fields": {"id": {"name": "id", "primaryKey": true, "databaseName": null, "file": "src/Entity/User.php", "line": 15}},
      "relations": {"company": {"name": "company", "cardinality": "one", "manager": "App\\EntityManager\\CompanyManager", "file": "src/Entity/User.php", "line": 21}}
    }
  },
  "managers": {
    "App\\EntityManager\\UserManager": {"class": "App\\EntityManager\\UserManager", "entity": "App\\Entity\\User", "connection": "app", "file": "src/EntityManager/UserManager.php", "line": 10}
  },
  "api": {"factory": {}, "query": {}},
  "diagnostics": []
}
```

`fields` include primary keys. Attribute names and entity property names (not SQL column identifiers) are completion candidates. A relation's `manager` refers to another indexed manager class. `databaseName = null` means implicit/default mapping; the engine does not guess SQL identifiers. `api.factory.*.returns = "class-string-argument:0"` signals an editor type provider to return the manager class supplied in the factory's first argument. Query argument roles such as `fieldArgument`, `aliasArgument` and `relationArgument` are zero-based.

## `complete` request

The second CLI argument is JSON with these optional keys (except `kind`):

```json
{
  "kind": "field",
  "manager": "App\\EntityManager\\UserManager",
  "rootAlias": "user",
  "joins": [
    {"fromAlias": "user", "relation": "company", "alias": "owner"}
  ],
  "alias": "owner"
}
```

When `manager` is absent, pass `entity` directly. For `parameter`, pass `parameters` as string array; the syntax analysis running in each editor identifies the actual `setParameter()` placeholders. `alias` uses the `rootAlias` or the entity short name with lowercased first letter as fallback. Ordered `joins` must follow the order in which the source query creates them.

## `complete` response

```json
[{"label": "id", "kind": "field"}, {"label": "name", "kind": "field"}]
```

`kind` is one of `manager`, `field`, `relation`, `alias`, `parameter`. Unresolved manager/entity, relation or alias results in an empty completion array rather than speculative candidates. Each IDE can add navigation links using the index's relative source locations. See README known limitations for static boundaries.

## Compatibility

When the core protocol changes incompatibly, increment `schemaVersion`; add optional fields compatibly. Plugins must key caches by workspace root and project source changes, not by a global engine cache. Do not share indexes across unrelated workspaces.
