<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */
namespace Demo\Entity;
use Small\SwooleEntityManager\Entity\Attribute\OrmEntity;
use Small\SwooleEntityManager\Entity\Attribute\PrimaryKey;
use Small\SwooleEntityManager\Entity\Attribute\Field;
use Small\SwooleEntityManager\Entity\Enum\FieldValueType;
#[OrmEntity]
class Company {
    #[PrimaryKey] private int $id;
    #[Field(FieldValueType::string)] private string $name;
}
