package ph.edu.vfgnhs.library;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
class ReportsController {
    private final BookRepository books; private final BookCopyRepository copies;
    private final BorrowerRepository borrowers; private final LoanRepository loans; private final ApiSupport api;

    ReportsController(BookRepository books, BookCopyRepository copies, BorrowerRepository borrowers,
                      LoanRepository loans, ApiSupport api) {
        this.books = books; this.copies = copies; this.borrowers = borrowers; this.loans = loans; this.api = api;
    }

    @GetMapping("/dashboard")
    Map<String, Object> dashboard() {
        PageRequest topTen = PageRequest.of(0, 10);
        return Map.of(
                "titles", books.count(), "copies", copies.count(), "available", copies.countByStatus(CopyStatus.AVAILABLE),
                "borrowers", borrowers.count(), "activeLoans", loans.countByReturnedAtIsNull(),
                "overdue", loans.countOverdue(Instant.now()),
                "totalCirculations", loans.count(),
                "topTitles", namedCounts(loans.topTitles(topTen)),
                "topBorrowers", namedCounts(loans.topBorrowers(topTen)));
    }

    @GetMapping("/accession")
    List<Map<String, Object>> accession() { return copies.findAllWithBook().stream().map(api::copy).toList(); }

    @GetMapping("/circulation")
    List<Map<String, Object>> circulation() { return loans.findAllDetailed().stream().map(api::loan).toList(); }

    @GetMapping(value = "/circulation.csv", produces = "text/csv")
    ResponseEntity<String> circulationCsv() {
        StringBuilder csv = new StringBuilder("Loan ID,Date,Name,Grade and Section,Title of Books,Accession Number,Date Borrowed,Date Returned\n");
        for (Loan l : loans.findAllDetailed()) {
            String grade = l.borrower.gradeSection != null && !l.borrower.gradeSection.isBlank()
                    ? l.borrower.gradeSection
                    : (l.borrower.department == null ? "" : l.borrower.department);
            csv.append(l.id).append(',')
                    .append(l.dueAt).append(',')
                    .append(quote(l.borrower.user.firstName + " " + l.borrower.user.lastName)).append(',')
                    .append(quote(grade)).append(',')
                    .append(quote(l.copy.book.title)).append(',')
                    .append(quote(l.copy.accessionNumber)).append(',')
                    .append(l.borrowedAt).append(',')
                    .append(l.returnedAt == null ? "" : l.returnedAt).append('\n');
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=circulation.csv")
                .contentType(MediaType.parseMediaType("text/csv")).body(csv.toString());
    }

    private List<Map<String, Object>> namedCounts(List<Object[]> rows) {
        return rows.stream().map(row -> Map.<String, Object>of("name", row[0], "count", row[1])).toList();
    }
    private String quote(String s) { return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\""; }
}

@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
class SettingsController {
    private final SettingRepository settings; private final ApiSupport api;
    SettingsController(SettingRepository settings, ApiSupport api) { this.settings = settings; this.api = api; }

    @GetMapping Map<String, String> list() {
        return settings.findAll().stream().collect(Collectors.toMap(s -> s.key, s -> s.value, (a,b) -> b, LinkedHashMap::new));
    }

    @PutMapping
    @Transactional
    Map<String, String> update(@RequestBody Map<String, String> values, Authentication auth) {
        values.forEach((key, value) -> {
            if (!Set.of("school_name","school_abbreviation","student_loan_days","teacher_loan_days","student_loan_limit","teacher_loan_limit").contains(key))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown setting: " + key);
            Setting s = settings.findById(key).orElseGet(() -> { Setting n = new Setting(); n.key = key; return n; });
            s.value = value; settings.save(s);
        });
        api.audit(api.current(auth), "UPDATE_SETTINGS", "SETTING", null, values.keySet().toString());
        return list();
    }
}

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
class AuditController {
    private final AuditLogRepository audits;
    private final UserRepository users;
    AuditController(AuditLogRepository audits, UserRepository users) { this.audits = audits; this.users = users; }
    @GetMapping List<Map<String, Object>> list(@RequestParam(defaultValue = "100") int limit) {
        var logs = audits.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 500)));
        var actors = users.findAllById(logs.stream().map(a -> a.actorId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(u -> u.id, u -> u.firstName + " " + u.lastName));
        return logs.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id); m.put("actorId", a.actorId);
            m.put("actorName", a.actorId == null ? "System" : actors.getOrDefault(a.actorId, "Unknown account"));
            m.put("action", a.action); m.put("entityType", a.entityType);
            m.put("entityId", a.entityId); m.put("details", a.details);
            m.put("ipAddress", a.ipAddress); m.put("location", a.location);
            m.put("createdAt", a.createdAt); return m;
        }).toList();
    }
}

@RestController
@RequestMapping("/api/admin/requests")
@PreAuthorize("hasRole('ADMIN')")
class RequestAdminController {
    private final ReservationRepository reservations;
    private final AcquisitionRequestRepository acquisitions;
    private final ApiSupport api;

    RequestAdminController(ReservationRepository reservations, AcquisitionRequestRepository acquisitions, ApiSupport api) {
        this.reservations = reservations; this.acquisitions = acquisitions; this.api = api;
    }

    @GetMapping("/reservations")
    List<Map<String, Object>> reservations() {
        return reservations.findAllDetailed().stream().map(r -> Map.<String, Object>of(
                "id", r.id, "title", r.book.title, "borrower", r.borrower.user.firstName + " " + r.borrower.user.lastName,
                "status", r.status, "requestedAt", r.requestedAt)).toList();
    }

    @PatchMapping("/reservations/{id}")
    Map<String, Object> reservation(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        Reservation r = reservations.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reservation not found"));
        r.status = api.parseEnum(RequestStatus.class, body.get("status"), "Choose a valid reservation status"); reservations.save(r);
        api.audit(api.current(auth), "UPDATE_RESERVATION", "RESERVATION", id, r.status.name());
        return Map.of("id", id, "status", r.status);
    }

    @GetMapping("/acquisitions")
    List<Map<String, Object>> acquisitions() {
        return acquisitions.findAllDetailed().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id); m.put("title", a.title); m.put("author", a.author); m.put("reason", a.reason);
            m.put("requester", a.requester.firstName + " " + a.requester.lastName);
            m.put("status", a.status); m.put("requestedAt", a.requestedAt); return m;
        }).toList();
    }

    @PatchMapping("/acquisitions/{id}")
    Map<String, Object> acquisition(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        AcquisitionRequest a = acquisitions.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found"));
        a.status = api.parseEnum(RequestStatus.class, body.get("status"), "Choose a valid request status"); acquisitions.save(a);
        api.audit(api.current(auth), "UPDATE_ACQUISITION", "ACQUISITION", id, a.status.name());
        return Map.of("id", id, "status", a.status);
    }
}

@RestController
class PrintDataController {
    private final BookCopyRepository copies; private final BorrowerRepository borrowers;
    private final LoanRepository loans; private final ApiSupport api;
    PrintDataController(BookCopyRepository copies, BorrowerRepository borrowers, LoanRepository loans, ApiSupport api) {
        this.copies = copies; this.borrowers = borrowers; this.loans = loans; this.api = api;
    }

    @GetMapping("/api/admin/print/book-card/{copyId}")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, Object> bookCard(@PathVariable Long copyId) {
        BookCopy c = copies.findById(copyId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Copy not found"));
        return Map.of("copy", api.copy(c), "transactions", loans.findByCopyDetailed(copyId).stream().map(api::loan).toList());
    }

    @GetMapping("/api/admin/print/borrower-card/{borrowerId}")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, Object> borrowerCard(@PathVariable Long borrowerId) { return card(borrowerId); }

    @GetMapping("/api/portal/borrower-card")
    Map<String, Object> ownCard(Authentication auth) {
        Borrower b = borrowers.findByUserId(api.current(auth).id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Borrower profile not found"));
        return card(b.id);
    }

    private Map<String, Object> card(Long id) {
        Borrower b = borrowers.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Borrower not found"));
        return Map.of("borrower", api.borrower(b), "transactions", loans.findByBorrowerDetailed(id).stream().map(api::loan).toList());
    }
}
