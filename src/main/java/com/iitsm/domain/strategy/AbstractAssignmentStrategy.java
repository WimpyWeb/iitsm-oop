package com.iitsm.domain.strategy;

import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared helper methods for filtering eligible technicians and scoring skill alignment. */
public abstract class AbstractAssignmentStrategy implements AssignmentStrategy {

    /** Filters active, available technicians below maxLoad who have not declined this incident. */
    protected List<Technician> filterEligible(Incident incident, List<Technician> candidates) {
        if (incident == null || candidates == null) {
            return List.of();
        }
        Set<String> declined = incident.getDeclinedTechnicianCodes();
        return candidates.stream()
                .filter(t -> t != null && t.canAcceptLoad() && !declined.contains(t.getUserCode()))
                .toList();
    }

    /** Counts matching skills against the incident's service, category, keywords, and description. */
    protected int countSkillMatches(Incident incident, Technician technician) {
        Set<String> incidentTokens = new LinkedHashSet<>();
        if (incident.getAffectedService() != null) {
            incidentTokens.add(incident.getAffectedService().getName().toLowerCase(Locale.ROOT));
            incidentTokens.addAll(incident.getAffectedService().getKeywords());
        }
        if (incident.getCategory() != null) {
            incidentTokens.add(incident.getCategory().getName().toLowerCase(Locale.ROOT));
            incidentTokens.addAll(incident.getCategory().getKeywords());
        }
        if (incident.getDescription() != null) {
            for (String word : incident.getDescription().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (word.length() >= 3) {
                    incidentTokens.add(word);
                }
            }
        }
        int matches = 0;
        for (String token : incidentTokens) {
            if (technician.hasSkill(token)) {
                matches++;
            }
        }
        return matches;
    }

    /** Returns service-specific resolution history count for the incident's affected service. */
    protected int serviceExperience(Incident incident, Technician technician) {
        if (incident.getAffectedService() == null) {
            return 0;
        }
        return technician.getExperienceForService(incident.getAffectedService().getName());
    }
}
