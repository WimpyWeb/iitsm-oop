package com.iitsm.repository;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.domain.model.Incident;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for Incident entities supporting dynamic Specifications and eager graph queries. */
public interface IncidentRepository extends JpaRepository<Incident, String>, JpaSpecificationExecutor<Incident> {

    @EntityGraph(attributePaths = {"reporter", "category", "affectedService", "assignedTechnician"},
            type = EntityGraphType.LOAD)
    @Query("SELECT i FROM Incident i WHERE i.id = :id")
    Optional<Incident> findByIdWithAssociations(@Param("id") String id);

    @Override
    @EntityGraph(attributePaths = {"reporter", "category", "affectedService", "assignedTechnician"},
            type = EntityGraphType.LOAD)
    Page<Incident> findAll(Specification<Incident> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "category", "affectedService", "assignedTechnician"},
            type = EntityGraphType.LOAD)
    Page<Incident> findByReporterUserCodeIgnoreCaseOrderByCreatedAtDesc(String userCode, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "category", "affectedService", "assignedTechnician"},
            type = EntityGraphType.LOAD)
    Page<Incident> findByAssignedTechnicianUserCodeIgnoreCaseOrderByCreatedAtDesc(String userCode, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "category", "affectedService", "assignedTechnician"},
            type = EntityGraphType.LOAD)
    List<Incident> findByStatusAndAssignedTechnicianIsNullOrderByCreatedAtAsc(IncidentStatus status);

    @EntityGraph(attributePaths = {"reporter", "category", "affectedService", "assignedTechnician"},
            type = EntityGraphType.LOAD)
    List<Incident> findByAffectedServiceIdAndCreatedAtAfterOrderByCreatedAtDesc(Long serviceId, Instant since);

    @EntityGraph(attributePaths = {
            "reporter", "category", "affectedService",
            "assignedTechnician", "assignedTechnician.team", "assignedTechnician.team.teamLead"
    }, type = EntityGraphType.LOAD)
    List<Incident> findByStatusIn(Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = {"reporter", "category", "affectedService", "assignedTechnician"},
            type = EntityGraphType.LOAD)
    @Query("SELECT i FROM Incident i WHERE i.resolution IS NOT NULL ORDER BY i.resolvedAt DESC")
    List<Incident> findAllResolvedWithResolution();

    @EntityGraph(attributePaths = {"reporter", "category", "affectedService", "assignedTechnician"},
            type = EntityGraphType.LOAD)
    List<Incident> findByCreatedAtAfterOrderByCreatedAtDesc(Instant since);

    @Query("SELECT i.id FROM Incident i")
    List<String> findAllIds();
}
