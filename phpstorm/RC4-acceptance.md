# PhpStorm 1.0.0-RC4 acceptance

RC4 fixes native completion when a query builder is created through a
controller manager accessor, such as the synthetic
`$this->itemManager()->createQueryBuilder('item')`.

The Kotlin regression suite covers nested field completion, automatic popup
context, condition expressions, inline builders, resolution by unique manager
basename, non-Small receivers, ambiguous managers, and method scope.

For manual Linux/Windows acceptance, install the RC4 plugin ZIP from the
successful tag build and restart PhpStorm. In a synthetic controller, type
`$query->where()->firstCondition($query->getFieldForCondition(`
after the query was created via a method accessor. The popup should display
mapped fields without Ctrl+Space. Also verify
Tools > Small Entity Manager: Diagnose Completion shows a resolved manager and
nonzero candidates. No local PHP interpreter is required.
