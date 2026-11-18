package com.iitsm.repository;

import com.iitsm.domain.model.Incident;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic JPA Specification builder supporting combinable incident search filters. */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    /** Builds a combined Specification from non-null criteria fields. */
    public static Specification<Incident> withCriteria(IncidentSearchCriteria criteria) {
        return (root, query, cb) -> {
            if (criteria == null) {
                return cb.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(criteria.incidentId())) {
                predicates.add(cb.like(cb.lower(root.get("id")),
                        "%" + criteria.incidentId().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (criteria.fromDate() != null) {
                Instant start = criteria.fromDate().atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (criteria.toDate() != null) {
                Instant end = criteria.toDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), end));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), criteria.priority()));
            }
            if (criteria.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), criteria.categoryId()));
            }
            if (criteria.serviceId() != null) {
                predicates.add(cb.equal(root.get("affectedService").get("id"), criteria.serviceId()));
            }
            if (hasText(criteria.technicianCode())) {
                var techJoin = root.join("assignedTechnician", JoinType.LEFT);
                predicates.add(cb.equal(cb.upper(techJoin.get("userCode")),
                        criteria.technicianCode().trim().toUpperCase(Locale.ROOT)));
            }
            if (hasText(criteria.reporterCode())) {
                predicates.add(cb.equal(cb.upper(root.get("reporter").get("userCode")),
                        criteria.reporterCode().trim().toUpperCase(Locale.ROOT)));
            }
            if (criteria.slaStatus() != null) {
                predicates.add(cb.equal(root.get("sla").get("slaStatus"), criteria.slaStatus()));
            }
            if (hasText(criteria.freeText())) {
                String pattern = "%" + criteria.freeText().trim().toLowerCase(Locale.ROOT) + "%";
                var serviceJoin = root.join("affectedService", JoinType.LEFT);
                var categoryJoin = root.join("category", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("id")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(serviceJoin.get("name")), pattern),
                        cb.like(cb.lower(categoryJoin.get("name")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
