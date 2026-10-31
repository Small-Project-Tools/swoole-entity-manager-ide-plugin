<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */
namespace Demo\EntityManager;
use Demo\Entity\User;
use Small\SwooleEntityManager\EntityManager\Attribute\Entity;
use Small\SwooleEntityManager\EntityManager\Attribute\Connection;
#[Entity(User::class)]
#[Connection('app')]
final class UserManager extends \Small\SwooleEntityManager\EntityManager\AbstractRelationnalManager {}
