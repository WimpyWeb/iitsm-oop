package com.iitsm.domain.model;

import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Database-driven incident classification category with matching keywords. */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "category_keywords", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "keyword", nullable = false, length = 60)
    private Set<String> keywords = new LinkedHashSet<>();

    /** Required by JPA. */
    protected Category() {
    }

    /** Constructs a Category with name and auto-classification keywords. */
    public Category(String name, Collection<String> keywords) {
        setName(name);
        setKeywords(keywords);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public final void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Category name cannot be blank.");
        }
        this.name = name.trim();
    }

    /** Returns an unmodifiable view of category matching keywords. */
    public Set<String> getKeywords() {
        return Collections.unmodifiableSet(keywords);
    }

    /** Replaces the normalized lowercase keyword set. */
    public final void setKeywords(Collection<String> keywords) {
        this.keywords.clear();
        if (keywords != null) {
            for (String kw : keywords) {
                if (kw != null && !kw.isBlank()) {
                    this.keywords.add(kw.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
    }
}
