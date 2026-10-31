<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */
namespace Demo\Entity\Traits;
use Small\SwooleEntityManager\Entity\Attribute\Field;
use Small\SwooleEntityManager\Entity\Enum\FieldValueType;
trait HasTimestamps {
    #[Field(FieldValueType::dateTime)]
    private ?\DateTimeImmutable $createdAt = null;
}
