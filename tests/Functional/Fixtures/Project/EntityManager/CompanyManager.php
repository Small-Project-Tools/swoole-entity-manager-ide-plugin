<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */
namespace Demo\EntityManager;
use Demo\Entity\Company;
use Small\SwooleEntityManager\EntityManager\Attribute\Entity;
#[Entity(entityClass: Company::class)]
final class CompanyManager {}
