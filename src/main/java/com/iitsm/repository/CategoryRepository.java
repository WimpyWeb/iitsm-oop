package com.iitsm.repository;

import com.iitsm.domain.model.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for DB-driven incident Categories. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);

    List<Category> findAllByOrderByNameAsc();

    Page<Category> findAllByOrderByNameAsc(Pageable pageable);
}
