package com.iitsm.service;

import com.iitsm.domain.enums.AdminLevel;
import com.iitsm.domain.enums.Role;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.TeamLead;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.InvalidLoginException;
import com.iitsm.exception.PersistenceFailureException;
import com.iitsm.exception.UnauthorizedException;
import com.iitsm.repository.AdministratorRepository;
import com.iitsm.repository.TechnicianRepository;
import com.iitsm.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service managing user registration, admin account provisioning, and technician availability. */
@Service
@Transactional
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final TechnicianRepository technicianRepository;
    private final AdministratorRepository administratorRepository;
    private final UserFactory userFactory;
    private final PasswordEncoder passwordEncoder;

    /** Constructs UserService with repositories, UserFactory, and BCrypt encoder. */
    public UserService(UserRepository userRepository,
                       TechnicianRepository technicianRepository,
                       AdministratorRepository administratorRepository,
                       UserFactory userFactory,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.technicianRepository = technicianRepository;
        this.administratorRepository = administratorRepository;
        this.userFactory = userFactory;
        this.passwordEncoder = passwordEncoder;
    }

    /** Self-registers a new Employee account (only Employee role allowed for self-registration). */
    public Employee registerEmployee(String username, String email, String fullName,
                                     String rawPassword, String phone, String department, String jobTitle) {
        validateNewUserCredentials(username, email, rawPassword);
        String code = nextCodeForPrefix("E", 101, 3);
        Employee employee = userFactory.createEmployee(
                code, username, email, fullName, passwordEncoder.encode(rawPassword), phone, department, jobTitle);
        return saveUser(employee);
    }

    /** Admin-creates a Technician account with sequential T-prefixed userCode. */
    public Technician createTechnician(String explicitCode, String username, String email, String fullName,
                                       String rawPassword, String phone, Collection<String> skills,
                                       int experienceYears, int maxLoad) {
        validateNewUserCredentials(username, email, rawPassword);
        String code = resolveCode(explicitCode, "T", 101, 3);
        Technician tech = userFactory.createTechnician(
                code, username, email, fullName, passwordEncoder.encode(rawPassword),
                phone, skills, experienceYears, maxLoad);
        return saveUser(tech);
    }

    /** Admin-creates a TeamLead account extending Technician. */
    public TeamLead createTeamLead(String explicitCode, String username, String email, String fullName,
                                   String rawPassword, String phone, Collection<String> skills,
                                   int experienceYears, int maxLoad) {
        validateNewUserCredentials(username, email, rawPassword);
        String code = resolveCode(explicitCode, "T", 201, 3);
        TeamLead lead = userFactory.createTeamLead(
                code, username, email, fullName, passwordEncoder.encode(rawPassword),
                phone, skills, experienceYears, maxLoad);
        return saveUser(lead);
    }

    /** Admin-creates an Administrator account with explicit AdminLevel. */
    public Administrator createAdministrator(String explicitCode, String username, String email, String fullName,
                                             String rawPassword, String phone, AdminLevel adminLevel) {
        validateNewUserCredentials(username, email, rawPassword);
        String code = resolveCode(explicitCode, "A", 1, 3);
        Administrator admin = userFactory.createAdministrator(
                code, username, email, fullName, passwordEncoder.encode(rawPassword), phone, adminLevel);
        return saveUser(admin);
    }

    /** Authenticates username and raw password against BCrypt hash and active status. */
    @Transactional(readOnly = true)
    public User authenticate(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null) {
            throw new InvalidLoginException("Username and password are required.");
        }
        User user = userRepository.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new InvalidLoginException("Invalid username or password."));
        if (!user.isActive() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidLoginException("Invalid username or password.");
        }
        return user;
    }

    /** Toggles availability flag for a technician (allowed by the technician themselves or admin). */
    public Technician toggleAvailability(String technicianCode, User actor) {
        Technician tech = technicianRepository.findByUserCodeIgnoreCase(technicianCode)
                .orElseThrow(() -> new InvalidInputException("Technician not found: " + technicianCode));
        boolean isAdmin = actor != null && actor.getRole() == Role.ADMIN;
        boolean isSelf = actor != null && actor.getUserCode().equalsIgnoreCase(technicianCode);
        if (!isAdmin && !isSelf) {
            throw new UnauthorizedException("You may only update your own availability.");
        }
        tech.setAvailable(!tech.isAvailable());
        return saveUser(tech);
    }

    /** Activates or deactivates any user account. */
    public User setUserActive(Long userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidInputException("User not found: " + userId));
        user.setActive(active);
        return saveUser(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username);
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new InvalidLoginException("User account not found: " + username));
    }

    @Transactional(readOnly = true)
    public User getByUserCode(String userCode) {
        return userRepository.findByUserCodeIgnoreCase(userCode)
                .orElseThrow(() -> new InvalidInputException("User code not found: " + userCode));
    }

    @Transactional(readOnly = true)
    public Page<User> listAllUsers(Pageable pageable) {
        return userRepository.findAllByOrderByUserCodeAsc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Technician> listTechnicians(Pageable pageable) {
        return technicianRepository.findAllByOrderByUserCodeAsc(pageable);
    }

    @Transactional(readOnly = true)
    public List<Technician> listActiveTechnicians() {
        return technicianRepository.findAllActiveWithTeam();
    }

    @Transactional(readOnly = true)
    public List<TeamLead> listActiveTeamLeads() {
        return technicianRepository.findAllActiveWithTeam().stream()
                .filter(TeamLead.class::isInstance)
                .map(TeamLead.class::cast)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Administrator> listActiveAdminsByLevel(AdminLevel level) {
        return administratorRepository.findByAdminLevelAndActiveTrueOrderByUserCodeAsc(level);
    }

    private void validateNewUserCredentials(String username, String email, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidInputException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (username == null || username.isBlank() || userRepository.existsByUsernameIgnoreCase(username.trim())) {
            throw new InvalidInputException("Username already exists or is blank: " + username);
        }
        if (email == null || email.isBlank() || userRepository.existsByEmailIgnoreCase(email.trim())) {
            throw new InvalidInputException("Email already registered or is invalid: " + email);
        }
    }

    private String resolveCode(String explicitCode, String prefix, long startNum, int digits) {
        if (explicitCode != null && !explicitCode.isBlank()) {
            String normalized = explicitCode.trim().toUpperCase();
            if (userRepository.existsByUserCodeIgnoreCase(normalized)) {
                throw new InvalidInputException("User code already exists: " + normalized);
            }
            return normalized;
        }
        return nextCodeForPrefix(prefix, startNum, digits);
    }

    private String nextCodeForPrefix(String prefix, long startNum, int digits) {
        long max = startNum - 1;
        for (User u : userRepository.findAll()) {
            String code = u.getUserCode();
            if (code != null && code.startsWith(prefix)) {
                try {
                    long parsed = Long.parseLong(code.substring(prefix.length()));
                    if (parsed > max) {
                        max = parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // Non-numeric suffix ignored when computing next sequential display code.
                }
            }
        }
        return UserFactory.formatUserCode(prefix, max + 1, digits);
    }

    private <T extends User> T saveUser(T user) {
        try {
            return userRepository.save(user);
        } catch (DataAccessException ex) {
            throw new PersistenceFailureException("Failed to persist user " + user.getUsername(), ex);
        }
    }
}
