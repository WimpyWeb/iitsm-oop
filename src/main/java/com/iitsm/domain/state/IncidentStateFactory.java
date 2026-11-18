package com.iitsm.domain.state;

import com.iitsm.domain.enums.IncidentStatus;
import com.iitsm.exception.InvalidInputException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Factory resolving singleton State instances from persisted IncidentStatus enum values using an EnumMap. */
@Component
public class IncidentStateFactory {

    private static final Map<IncidentStatus, IncidentState> STATES = new EnumMap<>(IncidentStatus.class);

    static {
        STATES.put(IncidentStatus.NEW, new NewState());
        STATES.put(IncidentStatus.CLASSIFIED, new ClassifiedState());
        STATES.put(IncidentStatus.ASSIGNED, new AssignedState());
        STATES.put(IncidentStatus.ACKNOWLEDGED, new AcknowledgedState());
        STATES.put(IncidentStatus.IN_PROGRESS, new InProgressState());
        STATES.put(IncidentStatus.PENDING_INFO, new PendingInfoState());
        STATES.put(IncidentStatus.RESOLVED, new ResolvedState());
        STATES.put(IncidentStatus.VERIFIED, new VerifiedState());
        STATES.put(IncidentStatus.CLOSED, new ClosedState());
    }

    /** Resolves the IncidentState handler for a given status enum. */
    public static IncidentState forStatus(IncidentStatus status) {
        if (status == null) {
            throw new InvalidInputException("Incident status cannot be null.");
        }
        IncidentState state = STATES.get(status);
        if (state == null) {
            throw new InvalidInputException("Unsupported incident status: " + status);
        }
        return state;
    }

    /** Instance delegation method for Spring-injected callers. */
    public IncidentState getState(IncidentStatus status) {
        return forStatus(status);
    }
}
