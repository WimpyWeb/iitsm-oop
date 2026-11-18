package com.iitsm.domain.factory;

import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.ServiceEntity;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Factory generating sequential INC1001+ identifiers and creating validated Incident instances. */
@Component
public class IncidentFactory {

    private final AtomicLong sequence = new AtomicLong(1001);

    /** Synchronizes the ID sequence above any existing persisted incident numeric suffix. */
    public void syncSequenceAbove(long maxNumericId) {
        sequence.accumulateAndGet(maxNumericId + 1, Math::max);
    }

    /** Creates an Incident using the next generated INC-prefixed identifier. */
    public Incident createIncident(Employee reporter, ServiceEntity affectedService, String description,
                                   int impact, int urgency, int affectedUsersCount) {
        String id = "INC" + sequence.getAndIncrement();
        return new Incident(id, reporter, affectedService, description, impact, urgency, affectedUsersCount);
    }

    /** Creates an Incident with an explicit identifier (primarily for deterministic tests/seeds). */
    public Incident createIncidentWithId(String id, Employee reporter, ServiceEntity affectedService,
                                         String description, int impact, int urgency, int affectedUsersCount) {
        return new Incident(id, reporter, affectedService, description, impact, urgency, affectedUsersCount);
    }
}
