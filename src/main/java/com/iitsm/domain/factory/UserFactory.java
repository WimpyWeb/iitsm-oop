package com.iitsm.domain.factory;

import com.iitsm.domain.enums.AdminLevel;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.TeamLead;
import com.iitsm.domain.model.Technician;
import java.util.Collection;
import org.springframework.stereotype.Component;

/** Factory encapsulating construction of concrete User subtype instances. */
@Component
public class UserFactory {

    /** Creates an Employee user account. */
    public Employee createEmployee(String userCode, String username, String email, String fullName,
                                   String passwordHash, String phone, String department, String jobTitle) {
        return new Employee(userCode, username, email, fullName, passwordHash, phone, department, jobTitle);
    }

    /** Creates a Technician user account. */
    public Technician createTechnician(String userCode, String username, String email, String fullName,
                                       String passwordHash, String phone, Collection<String> skills,
                                       int experienceYears, int maxLoad) {
        return new Technician(userCode, username, email, fullName, passwordHash, phone,
                skills, experienceYears, maxLoad);
    }

    /** Creates a TeamLead user account extending Technician. */
    public TeamLead createTeamLead(String userCode, String username, String email, String fullName,
                                   String passwordHash, String phone, Collection<String> skills,
                                   int experienceYears, int maxLoad) {
        return new TeamLead(userCode, username, email, fullName, passwordHash, phone,
                skills, experienceYears, maxLoad);
    }

    /** Creates an Administrator user account with an explicit AdminLevel. */
    public Administrator createAdministrator(String userCode, String username, String email, String fullName,
                                             String passwordHash, String phone, AdminLevel adminLevel) {
        return new Administrator(userCode, username, email, fullName, passwordHash, phone, adminLevel);
    }

    /** Formats a prefixed display user code such as E101, T201, or A001. */
    public static String formatUserCode(String prefix, long number, int minDigits) {
        String pattern = "%s%0" + minDigits + "d";
        return String.format(pattern, prefix, number);
    }
}
