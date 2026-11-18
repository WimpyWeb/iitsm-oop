package com.iitsm.intelligence;

import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Incident;
import java.util.List;
import java.util.Optional;

/** Abstraction for intelligent incident text analysis (category and resolution suggestions). */
public interface AiTextService {

    /** Suggests the most appropriate Category for an incident description. */
    Optional<Category> suggestCategory(String description, List<Category> categories);

    /** Suggests probable resolution steps based on past resolved incidents or AI inference. */
    Optional<String> suggestResolution(Incident incident, List<Incident> resolvedHistory);
}
