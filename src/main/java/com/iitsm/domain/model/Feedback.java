package com.iitsm.domain.model;

import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** Employee post-resolution feedback embedded within an Incident. */
@Embeddable
public class Feedback {

    @Column(name = "feedback_rating")
    private Integer rating;

    @Column(name = "feedback_comment", length = 1000)
    private String comment;

    @Column(name = "feedback_submitted_at")
    private Instant submittedAt;

    /** Required by JPA. */
    protected Feedback() {
    }

    /** Constructs validated employee feedback with rating 1..5. */
    public Feedback(int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new InvalidInputException("Feedback rating must be between 1 and 5.");
        }
        this.rating = rating;
        this.comment = comment == null ? "" : comment.trim();
        this.submittedAt = Instant.now();
    }

    public Integer getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
