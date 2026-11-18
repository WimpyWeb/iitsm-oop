package com.iitsm.repository;

import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.model.SlaPolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for default SLA policies keyed by Priority. */
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    Optional<SlaPolicy> findByPriority(Priority priority);

    List<SlaPolicy> findAllByOrderByPriorityAsc();

    Page<SlaPolicy> findAllByOrderByPriorityAsc(Pageable pageable);
}
