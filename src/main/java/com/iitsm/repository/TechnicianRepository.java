package com.iitsm.repository;

import com.iitsm.domain.model.Technician;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository for Technician accounts (including TeamLead subtype). */
public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    Optional<Technician> findByUsernameIgnoreCase(String username);

    Optional<Technician> findByUserCodeIgnoreCase(String userCode);

    @EntityGraph(attributePaths = {"team", "team.teamLead"}, type = EntityGraphType.LOAD)
    @Query("SELECT t FROM Technician t WHERE t.active = true ORDER BY t.userCode ASC")
    List<Technician> findAllActiveWithTeam();

    @EntityGraph(attributePaths = {"team", "team.teamLead"}, type = EntityGraphType.LOAD)
    Page<Technician> findAllByOrderByUserCodeAsc(Pageable pageable);
}
