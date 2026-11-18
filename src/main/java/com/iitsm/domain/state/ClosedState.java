package com.iitsm.domain.state;

import com.iitsm.domain.enums.IncidentStatus;
import java.util.EnumSet;
import java.util.Set;

/** Terminal closed state rejecting all further lifecycle transitions. */
public class ClosedState extends AbstractIncidentState {

    @Override
    public IncidentStatus getStatus() {
        return IncidentStatus.CLOSED;
    }

    @Override
    public Set<IncidentStatus> getAllowedTransitions() {
        return EnumSet.noneOf(IncidentStatus.class);
    }
}
