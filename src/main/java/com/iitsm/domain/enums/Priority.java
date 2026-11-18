package com.iitsm.domain.enums;

/** Priority levels with business-impact labels and numeric urgency ranks. */
public enum Priority {
    P1("Critical", 1),
    P2("High", 2),
    P3("Medium", 3),
    P4("Low", 4);

    private final String label;
    private final int rank;

    Priority(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    /** Returns the business-impact label (Critical, High, Medium, Low). */
    public String getLabel() {
        return label;
    }

    /** Returns the combined display name such as "P1 Critical". */
    public String getDisplayName() {
        return name() + " " + label;
    }

    /** Returns numeric rank where 1 is highest priority. */
    public int getRank() {
        return rank;
    }

    /** Returns true if this priority is equal to or higher than the given threshold. */
    public boolean isAtLeast(Priority threshold) {
        return threshold != null && this.rank <= threshold.rank;
    }
}
