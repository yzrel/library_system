package ph.edu.vfgnhs.library;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

@SpringBootApplication
public class LibraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }

    @Bean
    CommandLineRunner seedAdmin(
            UserRepository users,
            BorrowerRepository borrowers,
            SettingRepository settings,
            PasswordEncoder encoder,
            @Value("${library.seed-admin.email}") String email,
            @Value("${library.seed-admin.password}") String password) {
        return args -> {
            if (users.findByEmailIgnoreCase(email).isEmpty() && !users.existsBySchoolIdIgnoreCase("ADMIN-001")) {
                User admin = new User();
                admin.email = email.toLowerCase();
                admin.passwordHash = encoder.encode(password);
                admin.firstName = "System";
                admin.lastName = "Administrator";
                admin.role = Role.ADMIN;
                admin.status = AccountStatus.ACTIVE;
                admin.schoolId = "ADMIN-001";
                admin.createdAt = Instant.now();
                admin.updatedAt = admin.createdAt;
                users.save(admin);
            }
            settings.saveIfAbsent("school_name", "Virginia F. Griño National High School");
            settings.saveIfAbsent("school_abbreviation", "VFGNHS");
            settings.saveIfAbsent("student_loan_days", "7");
            settings.saveIfAbsent("teacher_loan_days", "14");
            settings.saveIfAbsent("student_loan_limit", "3");
            settings.saveIfAbsent("teacher_loan_limit", "8");
        };
    }
}
