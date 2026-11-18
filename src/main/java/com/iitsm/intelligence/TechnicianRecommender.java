package com.iitsm.intelligence;

import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.Technician;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Scores and ranks technicians for an incident using skills, workload, experience, and availability. */
@Component
public class TechnicianRecommender {

    /** Ranked recommendation entry surfaced in technician/admin assignment UIs. */
    public record TechnicianRecommendation(
            String userCode,
            String fullName,
            double score,
            int skillMatches,
            int serviceExperience,
            int experienceYears,
            int currentLoad,
            int maxLoad,
            boolean eligible,
            String rationale
    ) {
    }

    /** Computes descending ranked technician recommendations with transparent scoring details. */
    public List<TechnicianRecommendation> recommend(Incident incident, List<Technician> candidates) {
        if (incident == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = extractIncidentTokens(incident);
        String serviceName = incident.getAffectedService() != null ? incident.getAffectedService().getName() : "";

        List<TechnicianRecommendation> ranked = new ArrayList<>();
        for (Technician tech : candidates) {
            if (tech == null) {
                continue;
            }
            int skillMatches = 0;
            for (String token : tokens) {
                if (tech.hasSkill(token)) {
                    skillMatches++;
                }
            }
            int svcExp = tech.getExperienceForService(serviceName);
            boolean declined = incident.getDeclinedTechnicianCodes().contains(tech.getUserCode());
            boolean eligible = tech.canAcceptLoad() && !declined;

            double loadHeadroom = tech.getMaxLoad() > 0
                    ? (double) (tech.getMaxLoad() - tech.getCurrentLoad()) / tech.getMaxLoad()
                    : 0.0;
            double score = (skillMatches * 25.0)
                    + (svcExp * 10.0)
                    + (tech.getExperienceYears() * 2.5)
                    + (loadHeadroom * 20.0)
                    + (eligible ? 30.0 : -100.0);

            String rationale = String.format("skills=%d, svcExp=%d, exp=%dy, load=%d/%d%s",
                    skillMatches, svcExp, tech.getExperienceYears(), tech.getCurrentLoad(), tech.getMaxLoad(),
                    declined ? " [DECLINED]" : (!tech.isAvailable() ? " [UNAVAILABLE]"
                            : (tech.getCurrentLoad() >= tech.getMaxLoad() ? " [AT MAX LOAD]" : "")));

            ranked.add(new TechnicianRecommendation(
                    tech.getUserCode(), tech.getFullName(), Math.round(score * 10.0) / 10.0,
                    skillMatches, svcExp, tech.getExperienceYears(),
                    tech.getCurrentLoad(), tech.getMaxLoad(), eligible, rationale));
        }

        ranked.sort(Comparator.comparingDouble(TechnicianRecommendation::score).reversed()
                .thenComparing(TechnicianRecommendation::userCode));
        return ranked;
    }

    private Set<String> extractIncidentTokens(Incident incident) {
        Set<String> tokens = new LinkedHashSet<>();
        if (incident.getAffectedService() != null) {
            tokens.add(incident.getAffectedService().getName().toLowerCase(Locale.ROOT));
            tokens.addAll(incident.getAffectedService().getKeywords());
        }
        if (incident.getCategory() != null) {
            tokens.add(incident.getCategory().getName().toLowerCase(Locale.ROOT));
            tokens.addAll(incident.getCategory().getKeywords());
        }
        if (incident.getDescription() != null) {
            for (String word : incident.getDescription().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (word.length() >= 3) {
                    tokens.add(word);
                }
            }
        }
        return tokens;
    }
}
