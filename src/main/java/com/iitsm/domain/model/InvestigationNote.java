package com.iitsm.domain.model;

import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** Timestamped technician or stakeholder investigation note composed within an Incident. */
@Embeddable
public class InvestigationNote {

    @Column(name = "note_author_code", nullable = false, length = 20)
    private String authorCode;

    @Column(name = "note_author_name", nullable = false, length = 120)
    private String authorName;

    @Column(name = "note_created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "note_text", nullable = false, length = 2000)
    private String text;

    /** Required by JPA. */
    protected InvestigationNote() {
    }

    /** Constructs a validated investigation note entry. */
    public InvestigationNote(String authorCode, String authorName, Instant createdAt, String text) {
        if (authorCode == null || authorCode.isBlank()) {
            throw new InvalidInputException("Note author code cannot be blank.");
        }
        if (text == null || text.isBlank()) {
            throw new InvalidInputException("Investigation note text cannot be blank.");
        }
        this.authorCode = authorCode.trim();
        this.authorName = authorName == null ? authorCode.trim() : authorName.trim();
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.text = text.trim();
    }

    public String getAuthorCode() {
        return authorCode;
    }

    public String getAuthorName() {
        return authorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getText() {
        return text;
    }
}
