package com.linenexus.asset.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IamUserRepository extends JpaRepository<IamUserEntity, String> {
    Optional<IamUserEntity> findByTenantIdAndUsername(String tenantId, String username);
}
