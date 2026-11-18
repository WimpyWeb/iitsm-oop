package com.iitsm.repository;

import com.iitsm.domain.enums.Role;
import com.iitsm.domain.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for all User subtypes using JOINED inheritance. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUserCodeIgnoreCase(String userCode);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUserCodeIgnoreCase(String userCode);

    List<User> findByRoleAndActiveTrue(Role role);

    Page<User> findAllByOrderByUserCodeAsc(Pageable pageable);
}
