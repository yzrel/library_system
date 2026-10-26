package ph.edu.vfgnhs.library;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record RegisterRequest(
        @Email @NotBlank String email,
        @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "STUDENT|TEACHER") String role,
        @NotBlank @Size(max = 80) String schoolId,
        Long classSectionId,
        @Size(max = 120) String gradeSection,
        @Size(max = 120) String department,
        @Size(max = 40) String phone) {}
record LoginRequest(@NotBlank String email, @NotBlank String password) {}
record ProfileRequest(@NotBlank String firstName, @NotBlank String lastName, Long classSectionId, String gradeSection, String department, String phone) {}
record PasswordRequest(@NotBlank String currentPassword, @Size(min = 8, max = 72) String newPassword) {}
record AdminAccountRequest(String role, String status, String password, String email, Long classSectionId, String gradeSection) {}

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final UserRepository users;
    private final BorrowerRepository borrowers;
    private final ClassSectionRepository classes;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final ApiSupport api;

    AuthController(UserRepository users, BorrowerRepository borrowers, ClassSectionRepository classes,
                   PasswordEncoder encoder, JwtService jwt, ApiSupport api) {
        this.users = users; this.borrowers = borrowers; this.classes = classes;
        this.encoder = encoder; this.jwt = jwt; this.api = api;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String schoolId = request.schoolId().trim();
        if (users.existsByEmailIgnoreCase(email)) throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        if (users.existsBySchoolIdIgnoreCase(schoolId)) throw new ApiException(HttpStatus.CONFLICT, "School ID is already registered");
        if (!request.password().matches(".*[A-Za-z].*") || !request.password().matches(".*\\d.*"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must contain a letter and a number");

        User user = new User();
        user.email = email; user.passwordHash = encoder.encode(request.password());
        user.firstName = request.firstName().trim(); user.lastName = request.lastName().trim();
        user.role = api.parseEnum(Role.class, request.role(), "Choose a student or teacher account"); user.status = AccountStatus.ACTIVE; user.schoolId = schoolId;
        user.createdAt = Instant.now(); user.updatedAt = user.createdAt;
        users.save(user);

        Borrower borrower = new Borrower();
        borrower.user = user;
        borrower.cardNumber = (user.role == Role.STUDENT ? "S-" : "T-") + schoolId.toUpperCase().replaceAll("[^A-Z0-9-]", "");
        if (user.role == Role.STUDENT && request.classSectionId() != null) {
            ClassSection selectedClass = activeCurrentClass(request.classSectionId());
            borrower.classSection = selectedClass; borrower.gradeSection = ClassSectionController.label(selectedClass);
        } else {
            borrower.gradeSection = request.gradeSection();
        }
        borrower.department = request.department();
        borrower.phone = request.phone(); borrower.status = BorrowerStatus.ACTIVE;
        borrowers.save(borrower);
        api.audit(user, "REGISTER", "USER", user.id, user.role.name());
        return Map.of("token", jwt.issue(user), "user", api.borrower(borrower));
    }

    @PostMapping("/login")
    Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password"));
        if (!encoder.matches(request.password(), user.passwordHash))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password");
        if (user.status != AccountStatus.ACTIVE)
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is " + user.status.name().toLowerCase());
        LoginLocation.Snapshot place = LoginLocation.from(http);
        api.audit(user, "LOGIN", "USER", user.id, null, place.ipAddress(), place.location());
        return Map.of("token", jwt.issue(user), "user", view(user));
    }

    @GetMapping("/me")
    Map<String, Object> me(Authentication auth) { return view(api.current(auth)); }

    @PutMapping("/profile")
    @Transactional
    Map<String, Object> profile(Authentication auth, @Valid @RequestBody ProfileRequest request) {
        User user = api.current(auth);
        user.firstName = request.firstName().trim(); user.lastName = request.lastName().trim(); user.updatedAt = Instant.now();
        users.save(user);
        borrowers.findByUserId(user.id).ifPresent(b -> {
            if (user.role == Role.STUDENT && request.classSectionId() != null) {
                ClassSection selectedClass = activeCurrentClass(request.classSectionId());
                b.classSection = selectedClass; b.gradeSection = ClassSectionController.label(selectedClass);
            }
            b.department = request.department(); b.phone = request.phone();
            borrowers.save(b);
        });
        api.audit(user, "UPDATE_PROFILE", "USER", user.id, null);
        return view(user);
    }

    @PutMapping("/password")
    void password(Authentication auth, @Valid @RequestBody PasswordRequest request) {
        User user = api.current(auth);
        if (!encoder.matches(request.currentPassword(), user.passwordHash))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        user.passwordHash = encoder.encode(request.newPassword()); user.updatedAt = Instant.now(); users.save(user);
        api.audit(user, "CHANGE_PASSWORD", "USER", user.id, null);
    }

    private Map<String, Object> view(User user) {
        return borrowers.findByUserId(user.id).map(api::borrower).orElseGet(() -> api.user(user));
    }

    private ClassSection activeCurrentClass(Long id) {
        ClassSection selected = classes.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Selected class does not exist"));
        if (!selected.active || !selected.schoolYear.equals(ClassSectionController.currentSchoolYear()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select an active class for the current school year");
        return selected;
    }
}

@RestController
@RequestMapping("/api/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
class AccountAdminController {
    private final UserRepository users;
    private final BorrowerRepository borrowers;
    private final ClassSectionRepository classes;
    private final PasswordEncoder encoder;
    private final ApiSupport api;

    AccountAdminController(UserRepository users, BorrowerRepository borrowers, ClassSectionRepository classes,
                           PasswordEncoder encoder, ApiSupport api) {
        this.users = users; this.borrowers = borrowers; this.classes = classes;
        this.encoder = encoder; this.api = api;
    }

    @GetMapping
    List<Map<String, Object>> list() {
        return users.findAll().stream()
                .map(u -> borrowers.findByUserId(u.id).map(api::borrower).orElseGet(() -> api.user(u))).toList();
    }

    @PatchMapping("/{id}")
    @Transactional
    Map<String, Object> update(@PathVariable Long id, @RequestBody AdminAccountRequest request, Authentication auth) {
        User actor = api.current(auth);
        User user = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        if (request.role() != null) user.role = api.parseEnum(Role.class, request.role(), "Choose a valid role");
        if (request.status() != null) user.status = api.parseEnum(AccountStatus.class, request.status(), "Choose a valid account status");
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 8 || request.password().length() > 72)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be between 8 and 72 characters");
            if (!request.password().matches(".*[A-Za-z].*") || !request.password().matches(".*\\d.*"))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Password must contain a letter and a number");
            user.passwordHash = encoder.encode(request.password());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String login = request.email().trim().toLowerCase();
            if (login.length() < 3 || login.length() > 254)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Username or email must be between 3 and 254 characters");
            if (users.existsByEmailIgnoreCase(login) && !user.email.equalsIgnoreCase(login))
                throw new ApiException(HttpStatus.CONFLICT, "Username or email is already registered");
            user.email = login;
        }
        if (user.role == Role.TEACHER && request.gradeSection() != null) {
            String advisoryClass = request.gradeSection().trim();
            if (advisoryClass.isBlank() || advisoryClass.length() > 120)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Advisory class is required and must not exceed 120 characters");
            Borrower borrower = borrowers.findByUserId(id)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Teacher borrower record not found"));
            borrower.gradeSection = advisoryClass;
            borrowers.save(borrower);
        }
        Borrower borrower = borrowers.findByUserId(id).orElse(null);
        if (borrower != null && user.role == Role.STUDENT && request.classSectionId() != null) {
            ClassSection selectedClass = classes.findById(request.classSectionId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Selected class does not exist"));
            if (!selectedClass.active || !selectedClass.schoolYear.equals(ClassSectionController.currentSchoolYear()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Select an active class for the current school year");
            borrower.classSection = selectedClass;
            borrower.gradeSection = ClassSectionController.label(selectedClass);
            borrowers.save(borrower);
        } else if (borrower != null && user.role != Role.STUDENT) {
            borrower.classSection = null;
            borrowers.save(borrower);
        }
        user.updatedAt = Instant.now(); users.save(user);
        api.audit(actor, "UPDATE_ACCOUNT", "USER", id, "role=" + user.role + ", status=" + user.status);
        return borrowers.findByUserId(id).map(api::borrower).orElseGet(() -> api.user(user));
    }
}
