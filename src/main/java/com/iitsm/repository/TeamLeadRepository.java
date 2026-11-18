package com.iitsm.repository;

import com.iitsm.domain.model.TeamLead;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for TeamLead accounts. */
public interface TeamLeadRepository extends JpaRepository<TeamLead, Long> {

    Optional<TeamLead> findByUserCodeIgnoreCase(String userCode);

    List<TeamLead> findByActiveTrueOrderByUserCodeAsc();
}
