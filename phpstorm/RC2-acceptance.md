# PhpStorm 1.0.0-RC2 acceptance

This is a release **candidate**. Automated tests exercise the native PHP indexer, query context, inference and insertion. A successful Linux CI job does not establish Windows editor correctness.

## Automated quality gates

- [ ] GitLab `quality` (PHP lint, self-test, PHPStan at max, copyright) passes.
- [ ] GitLab `tests` (Pest Unit and Functional) passes.
- [ ] GitLab `phpstorm_build` (Gradle tests, official PhpStorm SDK, `verifyPluginStructure`) passes.
- [ ] Plugin ZIP embeds version `1.0.0-RC2` and contains all plugin classes.

## Manual Windows 11 smoke test (requires real PhpStorm)

Use PhpStorm 2025.1.3 or later. In **Settings > Plugins > Install Plugin from Disk**, install the `small-entity-manager-phpstorm-1.0.0-RC2.zip` from a successful `phpstorm_build` job and restart PhpStorm. Verify that the installed version is `1.0.0-RC2`. Do not configure or install a PHP runtime just for these features.

With an ordinary PHP project containing a valid Small Core 3 entity and manager mapping:

1. Ensure the project index completes without freezes or IDE exceptions. In **Tools > Small Entity Manager: Diagnose Completion**, the manager and entity counts should be nonzero.
2. Type `$factory->get(` and confirm a popup of **manager classes**, not unrelated PHP symbols. Choose a manager and verify the resulting `Manager::class` reference.
3. Type `$manager = $factory->get(UserManager::class);` then `$query = $manager->createQueryBuilder('user');` (with a real `UserManager` import). Verify IDE type inference for `$manager` and `$query`.
4. Verify the popup also appears automatically after typing `(`, including on `getFieldForCondition(...)`, `firstCondition(...)` and factory `get(...)`. Then use `$query->getFieldForCondition(` with an empty, unquoted first argument: confirm a dropdown and that selecting a field inserts a valid quoted string. Repeat with an open quote and with `Ctrl+Space`.
5. Add a mapped `leftJoin(...)` and select a field qualified by the join alias. Verify that typing `owner.` alone offers the joined fields, then confirm that `getFieldForCondition('owner.name')` is inserted as `getFieldForCondition('name', 'owner')` rather than a dotted field literal.
6. In `firstCondition(...)` and `andCondition(...)`, check that the first argument proposes `getFieldForCondition(...)` expressions and the second argument offers `ConditionOperatorType` cases. Verify a completed expression has valid PHP syntax.
7. Repeat on a manager obtained through `EntityManagerFactoryInterface`, a constructor-promoted manager, and a manager imported through a grouped alias without the suffix `Manager`.
8. Check that `$cache->get(...)` and unrelated factory calls do not receive Small-specific type inference.
9. Repeat completion after editing an entity field; check the index refresh. Verify project reopening does not crash PhpStorm.

Record the PhpStorm build, installed plugin version, the diagnostic panel output, and any incorrect/missing candidates. Do not copy third-party proprietary project source or fixtures into this repository.

**Release gate:** do not call 1.0.0 stable until the real-editor smoke test passes. Creating a version tag is a separate action from committing the candidate version.
