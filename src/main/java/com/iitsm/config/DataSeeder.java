package com.iitsm.config;

import com.iitsm.domain.enums.AdminLevel;
import com.iitsm.domain.enums.Priority;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Incident;
import com.iitsm.domain.model.ServiceEntity;
import com.iitsm.domain.model.TeamLead;
import com.iitsm.domain.model.Technician;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.UserRepository;
import com.iitsm.service.AdminCatalogService;
import com.iitsm.service.IncidentService;
import com.iitsm.service.UserService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Seeds initial catalog, SLA policies, service dependencies, demo accounts, and 30 incidents if DB is empty. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;
    private final UserService userService;
    private final AdminCatalogService catalogService;
    private final IncidentService incidentService;
    private final boolean seedEnabled;

    /** Constructs DataSeeder wired to services for full domain/audit/notification seeding. */
    public DataSeeder(UserRepository userRepository,
                      IncidentRepository incidentRepository,
                      UserService userService,
                      AdminCatalogService catalogService,
                      IncidentService incidentService,
                      @Value("${app.seed.enabled:true}") boolean seedEnabled) {
        this.userRepository = userRepository;
        this.incidentRepository = incidentRepository;
        this.userService = userService;
        this.catalogService = catalogService;
        this.incidentService = incidentService;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(String... args) {
        if (seedEnabled) {
            seedIfEmpty();
        }
    }

    /** Populates seed data only when the database has no existing users or incidents. */
    public void seedIfEmpty() {
        if (userRepository.count() > 0 || incidentRepository.count() > 0) {
            return;
        }
        log.info("Database is empty; seeding I-ITSM catalog, demo users, and 30 incidents...");

        seedSlaPolicies();
        List<Category> categories = seedCategories();
        List<ServiceEntity> services = seedServices();
        seedUsersAndTeams();
        seedIncidents(categories, services);
        log.info("I-ITSM seeding complete.");
    }

    private void seedSlaPolicies() {
        catalogService.saveOrUpdateSlaPolicy(Priority.P1, 5, 30);
        catalogService.saveOrUpdateSlaPolicy(Priority.P2, 15, 120);
        catalogService.saveOrUpdateSlaPolicy(Priority.P3, 60, 480);
        catalogService.saveOrUpdateSlaPolicy(Priority.P4, 240, 1440);
    }

    private List<Category> seedCategories() {
        Category hardware = catalogService.createCategory("Hardware",
                List.of("laptop", "disk", "memory", "server", "cpu", "printer"));
        Category software = catalogService.createCategory("Software",
                List.of("install", "license", "crash", "bug", "patch", "client"));
        Category network = catalogService.createCategory("Network",
                List.of("network", "vpn", "dns", "latency", "packet", "wifi", "firewall"));
        Category database = catalogService.createCategory("Database",
                List.of("database", "sql", "deadlock", "query", "replica", "postgres"));
        Category cloud = catalogService.createCategory("Cloud",
                List.of("cloud", "kubernetes", "container", "vm", "autoscaling"));
        Category security = catalogService.createCategory("Security",
                List.of("security", "breach", "unauthorized", "certificate", "tls", "phishing"));
        Category auth = catalogService.createCategory("Authentication",
                List.of("login", "sso", "oauth", "saml", "mfa", "password", "token"));
        Category application = catalogService.createCategory("Application",
                List.of("payment", "checkout", "transaction", "api", "erp", "portal", "unavailable"));
        Category storage = catalogService.createCategory("Storage",
                List.of("storage", "bucket", "s3", "backup", "quota", "snapshot"));
        return List.of(hardware, software, network, database, cloud, security, auth, application, storage);
    }

    private List<ServiceEntity> seedServices() {
        ServiceEntity database = catalogService.createService(
                "Database", "Primary transactional SQL cluster", 5,
                List.of("database", "sql", "query", "deadlock"), null, null, List.of());
        ServiceEntity network = catalogService.createService(
                "Network", "Core campus and datacenter routing", 5,
                List.of("network", "vpn", "dns", "routing"), null, null, List.of());
        ServiceEntity authentication = catalogService.createService(
                "Authentication", "Central SSO, OAuth2, and MFA provider", 5,
                List.of("sso", "login", "oauth", "mfa", "saml"), null, null, List.of(network.getId()));
        ServiceEntity email = catalogService.createService(
                "Email", "Corporate SMTP relay and webmail", 3,
                List.of("email", "smtp", "inbox", "mail"), null, null, List.of(network.getId()));
        ServiceEntity cloudStorage = catalogService.createService(
                "Cloud Storage", "Object storage buckets and backups", 4,
                List.of("storage", "bucket", "s3", "backup"), null, null,
                List.of(network.getId(), authentication.getId()));
        ServiceEntity payment = catalogService.createService(
                "Payment", "Customer payment and checkout gateway", 5,
                List.of("payment", "checkout", "transaction", "refund"), null, null,
                List.of(database.getId(), network.getId(), authentication.getId()));
        ServiceEntity hrPortal = catalogService.createService(
                "HR Portal", "Employee payroll and leave management portal", 3,
                List.of("payroll", "leave", "hr", "timesheet"), null, null,
                List.of(database.getId(), authentication.getId(), email.getId()));
        ServiceEntity erp = catalogService.createService(
                "ERP", "Enterprise resource planning and finance ledger", 4,
                List.of("erp", "invoice", "ledger", "inventory"), null, null,
                List.of(database.getId(), authentication.getId()));
        return List.of(payment, database, network, authentication, cloudStorage, email, hrPortal, erp);
    }

    private void seedUsersAndTeams() {
        userService.createAdministrator("A001", "sysadmin", "sysadmin@iitsm.local",
                "Avery System Admin", DEMO_PASSWORD, "555-0001", AdminLevel.SYSTEM_ADMIN);
        userService.createAdministrator("A002", "itmanager", "itmanager@iitsm.local",
                "Morgan IT Manager", DEMO_PASSWORD, "555-0002", AdminLevel.IT_MANAGER);

        TeamLead lead1 = userService.createTeamLead("T201", "lead1", "lead1@iitsm.local",
                "Jordan App Lead", DEMO_PASSWORD, "555-0200",
                List.of("payment", "application", "erp", "hr", "database", "sql"), 9, 6);
        TeamLead lead2 = userService.createTeamLead("T202", "lead2", "lead2@iitsm.local",
                "Taylor Infra Lead", DEMO_PASSWORD, "555-0299",
                List.of("network", "security", "cloud", "storage", "sso", "email"), 10, 6);

        Technician t101 = userService.createTechnician("T101", "tech1", "tech1@iitsm.local",
                "Liam Payment Specialist", DEMO_PASSWORD, "555-0201",
                List.of("payment", "checkout", "application", "api"), 5, 6);
        Technician t102 = userService.createTechnician("T102", "tech2", "tech2@iitsm.local",
                "Maya Data Engineer", DEMO_PASSWORD, "555-0202",
                List.of("database", "sql", "erp", "hr", "payroll"), 6, 6);
        Technician t103 = userService.createTechnician("T103", "tech3", "tech3@iitsm.local",
                "Noah Network Security", DEMO_PASSWORD, "555-0203",
                List.of("network", "vpn", "security", "sso", "authentication"), 4, 6);
        Technician t104 = userService.createTechnician("T104", "tech4", "tech4@iitsm.local",
                "Olivia Cloud Storage", DEMO_PASSWORD, "555-0204",
                List.of("cloud", "storage", "backup", "email", "smtp"), 3, 6);

        catalogService.createTeam("Core Applications Team", "Application",
                lead1.getUserCode(), List.of(t101.getUserCode(), t102.getUserCode()));
        catalogService.createTeam("Infrastructure & Security Team", "Infrastructure",
                lead2.getUserCode(), List.of(t103.getUserCode(), t104.getUserCode()));

        userService.registerEmployee("emp1", "emp1@iitsm.local", "Alice Reporter",
                DEMO_PASSWORD, "555-0101", "Finance", "Senior Analyst");
        userService.registerEmployee("emp2", "emp2@iitsm.local", "Bob Coordinator",
                DEMO_PASSWORD, "555-0102", "Operations", "Operations Lead");
        userService.registerEmployee("emp3", "emp3@iitsm.local", "Chloe Specialist",
                DEMO_PASSWORD, "555-0103", "Human Resources", "HR Generalist");
        userService.registerEmployee("emp4", "emp4@iitsm.local", "Daniel Associate",
                DEMO_PASSWORD, "555-0104", "Engineering", "Product Associate");
    }

    private void seedIncidents(List<Category> cats, List<ServiceEntity> svcs) {
        Employee e1 = (Employee) userService.getByUserCode("E101");
        Employee e2 = (Employee) userService.getByUserCode("E102");
        Employee e3 = (Employee) userService.getByUserCode("E103");
        Employee e4 = (Employee) userService.getByUserCode("E104");

        // Categories: 0=Hardware,1=Software,2=Network,3=Database,4=Cloud,5=Security,6=Auth,7=Application,8=Storage
        // Services: 0=Payment,1=Database,2=Network,3=Auth,4=CloudStorage,5=Email,6=HRPortal,7=ERP
        record SeedSpec(Employee rep, int svcIdx, Integer catIdx, String desc,
                        int impact, int urgency, int users, String outcome,
                        String resSummary, String resSteps, int rating) {
        }

        List<SeedSpec> specs = List.of(
                // INC1001: Official demo incident (kept active ASSIGNED for live walkthrough)
                new SeedSpec(e1, 0, 7,
                        "The payment application is unavailable and users are unable to complete transactions.",
                        5, 5, 150, "ASSIGNED", null, null, 0),

                // 2..11: CLOSED with feedback
                new SeedSpec(e2, 0, 7, "Refund reconciliation batch failing with gateway HTTP 502 error",
                        4, 4, 40, "CLOSED", "Restarted payment worker pool",
                        "1. Cleared stuck Redis locks\n2. Restarted worker pods", 5),
                new SeedSpec(e3, 1, 3, "Primary SQL cluster experiencing row-level deadlock on ledger table",
                        5, 4, 80, "CLOSED", "Tuned lock timeout and added index",
                        "1. Added composite index\n2. Terminated blocking session", 5),
                new SeedSpec(e4, 2, 2, "Corporate VPN concentrator dropping remote sessions every 10 minutes",
                        4, 4, 120, "CLOSED", "Updated VPN keepalive interval",
                        "1. Adjusted DPD timer\n2. Rebalanced tunnel nodes", 4),
                new SeedSpec(e1, 3, 6, "SAML single sign-on login rejecting valid employee tokens",
                        5, 5, 200, "CLOSED", "Synchronized IdP NTP clock drift",
                        "1. Resynced NTP servers\n2. Rotated SAML signing cert", 5),
                new SeedSpec(e2, 4, 8, "Object storage bucket upload returning quota exceeded despite free space",
                        3, 3, 15, "CLOSED", "Purged orphaned multipart upload parts",
                        "1. Listed incomplete uploads\n2. Executed lifecycle cleanup", 4),
                new SeedSpec(e3, 5, 1, "Outbound SMTP relay queuing external client receipts for over an hour",
                        3, 4, 60, "CLOSED", "Cleared deferred relay queue",
                        "1. Restarted relay daemon\n2. Flushed deferred mail", 5),
                new SeedSpec(e4, 6, 7, "Employee timesheet submission throwing null validation error on HR Portal",
                        2, 3, 25, "CLOSED", "Deployed hotfix for empty overtime field",
                        "1. Patched controller validation\n2. Verified submission", 4),
                new SeedSpec(e1, 7, 7, "Monthly invoice PDF generator crashing on ERP billing module",
                        4, 3, 30, "CLOSED", "Increased ERP PDF renderer heap",
                        "1. Updated JVM Xmx to 4G\n2. Reprocessed failed invoices", 5),
                new SeedSpec(e2, 1, 3, "Read replica replication lag exceeding 45 seconds during analytics sync",
                        4, 4, 50, "CLOSED", "Throttled bulk analytics ETL job",
                        "1. Added read-pool rate limit\n2. Verified lag < 1s", 4),
                new SeedSpec(e3, 3, 5, "Suspicious repeated failed MFA prompts detected on executive accounts",
                        5, 4, 5, "CLOSED", "Blocked offending IP subnet and reset sessions",
                        "1. Added WAF rate rule\n2. Revoked active refresh tokens", 5),

                // 12..14: VERIFIED
                new SeedSpec(e4, 4, 4, "Automated nightly snapshot backup missing checksum verification tag",
                        3, 2, 4, "VERIFIED", "Updated snapshot manifest script",
                        "1. Fixed tagger permissions\n2. Ran manual verify", 0),
                new SeedSpec(e1, 5, 2, "Inbound DKIM validation rejecting partner domain invoices",
                        3, 3, 12, "VERIFIED", "Whitelisted partner SPF record",
                        "1. Verified DNS TXT keys\n2. Updated mail policy", 0),
                new SeedSpec(e2, 6, 3, "Payroll bonus lookup query timing out for finance reviewers",
                        3, 4, 18, "VERIFIED", "Refactored payroll join query",
                        "1. Replaced subquery with join\n2. Verified <200ms response", 0),

                // 15..21: RESOLVED
                new SeedSpec(e3, 7, 1, "ERP inventory barcode scanner client fails to launch after OS patch",
                        3, 3, 20, "RESOLVED", "Rebuilt scanner driver installer",
                        "1. Signed USB serial driver\n2. Pushed client update", 0),
                new SeedSpec(e4, 2, 2, "Building B WiFi access points failing DHCP lease renewal",
                        4, 3, 75, "RESOLVED", "Expanded DHCP scope pool",
                        "1. Reduced lease duration\n2. Added secondary subnet", 0),
                new SeedSpec(e1, 4, 8, "Archive storage cold-tier retrieval job stalled at 65 percent",
                        2, 2, 3, "RESOLVED", "Restarted storage hydration worker",
                        "1. Reset hydration checkpoint\n2. Completed restore", 0),
                new SeedSpec(e2, 5, 6, "Shared engineering mailbox password rotation locked automated alerts",
                        3, 3, 8, "RESOLVED", "Updated service secret in vault",
                        "1. Rotated app password\n2. Tested alert delivery", 0),
                new SeedSpec(e3, 6, 7, "Leave balance calendar widget rendering previous fiscal year dates",
                        2, 2, 35, "RESOLVED", "Fixed fiscal calendar seed config",
                        "1. Updated fiscal year table\n2. Cleared browser cache", 0),
                new SeedSpec(e4, 7, 3, "ERP supplier ledger export duplicating tax lines on CSV download",
                        3, 3, 12, "RESOLVED", "Fixed DISTINCT projection in CSV exporter",
                        "1. Updated export query\n2. Re-tested sample supplier", 0),
                new SeedSpec(e1, 1, 0, "Database backup disk array reporting degraded SMART sector warning",
                        4, 4, 10, "RESOLVED", "Replaced failing NVMe drive in RAID",
                        "1. Swapped slot 4 drive\n2. Confirmed RAID rebuild healthy", 0),

                // 22..24: PENDING_INFO
                new SeedSpec(e2, 3, 6, "OAuth mobile app prompt loops endlessly on biometric confirmation",
                        3, 4, 22, "PENDING_INFO", null, null, 0),
                new SeedSpec(e3, 6, 1, "HR onboarding checklist page blank for newly hired contractors",
                        2, 3, 6, "PENDING_INFO", null, null, 0),
                new SeedSpec(e4, 4, 8, "Cloud bucket pre-signed download link expires immediately on Safari",
                        2, 2, 4, "PENDING_INFO", null, null, 0),

                // 25..27: IN_PROGRESS
                new SeedSpec(e1, 7, 7, "ERP general ledger nightly close job taking 3 hours longer than usual",
                        4, 4, 45, "IN_PROGRESS", null, null, 0),
                new SeedSpec(e2, 2, 5, "Firewall rule audit flagged unexpected outbound port 8443 traffic",
                        4, 5, 15, "IN_PROGRESS", null, null, 0),
                new SeedSpec(e3, 5, 1, "Desktop Outlook client freezing when opening calendar invites",
                        2, 3, 9, "IN_PROGRESS", null, null, 0),

                // 28..29: ACKNOWLEDGED
                new SeedSpec(e4, 1, 3, "Database staging clone snapshot restore failed on schema migration",
                        3, 3, 7, "ACKNOWLEDGED", null, null, 0),
                new SeedSpec(e1, 3, 6, "Passwordless magic link emails delayed over 5 minutes for contractors",
                        3, 4, 16, "ACKNOWLEDGED", null, null, 0),

                // 30: ASSIGNED
                new SeedSpec(e2, 4, 4, "Kubernetes container registry pull rate limit warnings on build cluster",
                        3, 3, 14, "ASSIGNED", null, null, 0)
        );

        for (SeedSpec s : specs) {
            Long catId = s.catIdx() != null ? cats.get(s.catIdx()).getId() : null;
            Incident inc = incidentService.createIncident(
                    s.rep().getUserCode(), svcs.get(s.svcIdx()).getId(), catId,
                    s.desc(), s.impact(), s.urgency(), s.users());

            Technician tech = inc.getAssignedTechnician();
            if (tech == null) {
                continue;
            }
            switch (s.outcome()) {
                case "ACKNOWLEDGED" -> incidentService.acknowledge(inc.getId(), tech);
                case "IN_PROGRESS" -> {
                    incidentService.acknowledge(inc.getId(), tech);
                    incidentService.startProgress(inc.getId(), tech);
                }
                case "PENDING_INFO" -> {
                    incidentService.acknowledge(inc.getId(), tech);
                    incidentService.startProgress(inc.getId(), tech);
                    incidentService.requestInfo(inc.getId(), tech,
                            "Please confirm browser version and timestamp of the latest occurrence.");
                }
                case "RESOLVED" -> {
                    incidentService.acknowledge(inc.getId(), tech);
                    incidentService.startProgress(inc.getId(), tech);
                    incidentService.resolve(inc.getId(), tech, s.resSummary(), s.resSteps());
                }
                case "VERIFIED" -> {
                    incidentService.acknowledge(inc.getId(), tech);
                    incidentService.startProgress(inc.getId(), tech);
                    incidentService.resolve(inc.getId(), tech, s.resSummary(), s.resSteps());
                    incidentService.verify(inc.getId(), s.rep());
                }
                case "CLOSED" -> {
                    incidentService.acknowledge(inc.getId(), tech);
                    incidentService.startProgress(inc.getId(), tech);
                    incidentService.resolve(inc.getId(), tech, s.resSummary(), s.resSteps());
                    incidentService.verify(inc.getId(), s.rep());
                    if (s.rating() > 0) {
                        incidentService.submitFeedback(inc.getId(), s.rep(), s.rating(),
                                "Resolved effectively by " + tech.getFullName());
                    }
                    incidentService.close(inc.getId(), tech, "Verified and closed during baseline operations.");
                }
                default -> {
                    // Leave in ASSIGNED state.
                }
            }
        }
    }
}
