<?php
/* SMALL-IDE-COPYRIGHT
 * Copyright 2026 - Sébastien Kus
 * Under MIT license
 */
namespace Demo\Entity;
use Small\SwooleEntityManager\Entity\Attribute\{OrmEntity, Field, PrimaryKey, ToOne};
use Small\SwooleEntityManager\Entity\Enum\FieldValueType;
use Demo\EntityManager\CompanyManager;
use Demo\Entity\Traits\HasTimestamps;
#[OrmEntity]
class User {
    use HasTimestamps;
    #[PrimaryKey(dbFieldName: 'user_id')]
    private ?int $id = null;
    #[Field(type: FieldValueType::string, dbFieldName: 'email_address')]
    private string $email;
    #[ToOne(toManager: CompanyManager::class, keysMapping: ['companyId' => 'id'])]
    private ?Company $company = null;
    public function setEmail(string $email): void {
        $this->email = $email;
    }
    #[Field(FieldValueType::int)]
    private int $companyId;
}
