package com.linenexus.asset.audit;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, String> {

    @Query(
            """
            SELECT a FROM AuditLogEntity a
            WHERE a.tenantId = :tenantId
              AND (:category IS NULL OR :category = '' OR a.category = :category)
              AND (:action IS NULL OR :action = '' OR a.action = :action)
              AND (:result IS NULL OR :result = '' OR a.result = :result)
              AND (:actor IS NULL OR :actor = '' OR LOWER(a.actorName) LIKE LOWER(CONCAT('%', :actor, '%')))
              AND (:keyword IS NULL OR :keyword = '' OR LOWER(a.summary) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(a.target, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:fromTs IS NULL OR a.createdAt >= :fromTs)
              AND (:toTs IS NULL OR a.createdAt <= :toTs)
            """)
    Page<AuditLogEntity> search(
            @Param("tenantId") String tenantId,
            @Param("category") String category,
            @Param("action") String action,
            @Param("result") String result,
            @Param("actor") String actor,
            @Param("keyword") String keyword,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs,
            Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuditLogEntity a WHERE a.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
