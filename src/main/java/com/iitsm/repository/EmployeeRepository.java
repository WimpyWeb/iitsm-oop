package com.iitsm.repository;

import com.iitsm.domain.model.Employee;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for Employee user accounts. */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsernameIgnoreCase(String username);

    Optional<Employee> findByUserCodeIgnoreCase(String userCode);

    Page<Employee> findAllByOrderByUserCodeAsc(Pageable pageable);
}
