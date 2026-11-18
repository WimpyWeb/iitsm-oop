package com.iitsm.repository;

import com.iitsm.domain.enums.AdminLevel;
import com.iitsm.domain.model.Administrator;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for Administrator accounts filtered by AdminLevel. */
public interface AdministratorRepository extends JpaRepository<Administrator, Long> {

    List<Administrator> findByActiveTrueOrderByUserCodeAsc();

    List<Administrator> findByAdminLevelAndActiveTrueOrderByUserCodeAsc(AdminLevel adminLevel);
}
