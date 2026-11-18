package com.iitsm.repository;

import com.iitsm.domain.model.Team;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository for support Teams with aggregated members and TeamLead. */
public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = {"teamLead", "members"}, type = EntityGraphType.LOAD)
    @Query("SELECT t FROM Team t ORDER BY t.name ASC")
    List<Team> findAllWithMembers();

    @EntityGraph(attributePaths = {"teamLead"}, type = EntityGraphType.LOAD)
    Page<Team> findAllByOrderByNameAsc(Pageable pageable);
}
