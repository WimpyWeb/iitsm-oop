package com.iitsm.repository;

import com.iitsm.domain.enums.ViolationType;
import com.iitsm.domain.model.SlaViolation;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for persisted SLA response and resolution breaches. */
public interface SlaViolationRepository extends JpaRepository<SlaViolation, Long> {

    boolean existsByIncidentIdAndType(String incidentId, ViolationType type);

    List<SlaViolation> findByIncidentIdOrderByDetectedAtDesc(String incidentId);

    Page<SlaViolation> findAllByOrderByDetectedAtDesc(Pageable pageable);
}
