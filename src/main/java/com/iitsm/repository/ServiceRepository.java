package com.iitsm.repository;

import com.iitsm.domain.model.ServiceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for IT catalog Services and their dependency graph. */
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {

    Optional<ServiceEntity> findByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = {"dependsOn"}, type = EntityGraphType.LOAD)
    @Query("SELECT s FROM ServiceEntity s ORDER BY s.name ASC")
    List<ServiceEntity> findAllWithDependencies();

    @EntityGraph(attributePaths = {"dependsOn"}, type = EntityGraphType.LOAD)
    @Query("SELECT s FROM ServiceEntity s WHERE s.id = :id")
    Optional<ServiceEntity> findByIdWithDependencies(@Param("id") Long id);

    @EntityGraph(attributePaths = {"dependsOn"}, type = EntityGraphType.LOAD)
    Page<ServiceEntity> findAllByOrderByNameAsc(Pageable pageable);
}
