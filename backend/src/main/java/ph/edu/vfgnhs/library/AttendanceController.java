package ph.edu.vfgnhs.library;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

record CheckInRequest(@NotBlank String identifier, @NotBlank String purpose, @NotBlank String entryMethod) {}
record FeedbackRequest(@NotNull Long visitId, @NotBlank String rating) {}

@RestController
@RequestMapping("/api/attendance")
class AttendanceController {
    private static final Duration COOLDOWN = Duration.ofMinutes(30);
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Manila");
    private final BorrowerRepository borrowers;
    private final UserRepository users;
    private final LibraryVisitRepository visits;
    private final ServiceFeedbackRepository feedback;
    private final ApiSupport api;

    AttendanceController(BorrowerRepository borrowers, UserRepository users, LibraryVisitRepository visits,
                         ServiceFeedbackRepository feedback, ApiSupport api) {
        this.borrowers = borrowers; this.users = users; this.visits = visits; this.feedback = feedback; this.api = api;
    }

    @GetMapping("/suggest")
    List<Map<String, Object>> suggest(@RequestParam(defaultValue = "") String q) {
        String query = q.trim();
        if (query.isEmpty()) return List.of();
        return borrowers.suggest(query, PageRequest.of(0, 8)).stream().map(b -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", b.user.firstName + " " + b.user.lastName);
            row.put("identifier", b.cardNumber);
            row.put("schoolId", b.user.schoolId);
            row.put("role", b.user.role);
            row.put("gradeSection", group(b));
            return row;
        }).toList();
    }

    @GetMapping("/today")
    Map<String, Object> today() {
        LocalDate day = LocalDate.now(SCHOOL_ZONE);
        Instant from = day.atStartOfDay(SCHOOL_ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(SCHOOL_ZONE).toInstant();
        List<Map<String, Object>> rows = visits.report(from, to, "").stream().map(visit -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", visit.id);
            row.put("checkedInAt", visit.checkedInAt);
            row.put("name", visit.borrower.user.firstName + " " + visit.borrower.user.lastName);
            row.put("role", visit.borrower.user.role);
            row.put("gradeSection", group(visit.borrower));
            row.put("purpose", visit.purpose);
            row.put("entryMethod", visit.entryMethod);
            return row;
        }).toList();
        return Map.of("date", day.toString(), "count", rows.size(), "visits", rows);
    }

    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> checkIn(@Valid @RequestBody CheckInRequest request) {
        String identifier = request.identifier().trim();
        Borrower borrower = borrowers.findByCardNumberIgnoreCase(identifier)
                .or(() -> users.findBySchoolIdIgnoreCase(identifier).flatMap(u -> borrowers.findByUserId(u.id)))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Library account not found"));
        if (borrower.user.status != AccountStatus.ACTIVE || borrower.status != BorrowerStatus.ACTIVE)
            throw new ApiException(HttpStatus.FORBIDDEN, "This library account is not active");

        VisitPurpose purpose = parsePurpose(request.purpose());
        EntryMethod method = parseMethod(request.entryMethod());
        Instant now = Instant.now();
        if (visits.existsByBorrowerIdAndCheckedInAtAfter(borrower.id, now.minus(COOLDOWN)))
            throw new ApiException(HttpStatus.CONFLICT, "Attendance was already recorded within the last 30 minutes");

        LibraryVisit visit = new LibraryVisit();
        visit.borrower = borrower; visit.checkedInAt = now; visit.purpose = purpose; visit.entryMethod = method;
        visits.save(visit);
        api.audit(null, "ATTENDANCE_CHECK_IN", "LIBRARY_VISIT", visit.id, "purpose=" + purpose);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("visitId", visit.id);
        result.put("checkedInAt", visit.checkedInAt);
        result.put("name", borrower.user.firstName + " " + borrower.user.lastName);
        result.put("gradeSection", borrower.gradeSection);
        result.put("department", borrower.department);
        result.put("purpose", visit.purpose);
        return result;
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> feedback(@Valid @RequestBody FeedbackRequest request) {
        LibraryVisit visit = visits.findById(request.visitId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attendance entry not found"));
        if (feedback.existsByVisitId(visit.id))
            throw new ApiException(HttpStatus.CONFLICT, "Feedback has already been submitted for this visit");
        if (visit.checkedInAt.isBefore(Instant.now().minus(Duration.ofHours(2))))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Feedback period has expired");
        FeedbackRating rating;
        try { rating = FeedbackRating.valueOf(request.rating().trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid feedback rating"); }
        ServiceFeedback entry = new ServiceFeedback();
        entry.visit = visit; entry.rating = rating; entry.createdAt = Instant.now();
        feedback.save(entry);
        return Map.of("rating", entry.rating, "createdAt", entry.createdAt);
    }

    private VisitPurpose parsePurpose(String value) {
        try { return VisitPurpose.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid attendance purpose"); }
    }

    private EntryMethod parseMethod(String value) {
        try { return EntryMethod.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid entry method"); }
    }

    private String group(Borrower borrower) {
        if (borrower.gradeSection != null && !borrower.gradeSection.isBlank()) return borrower.gradeSection;
        return borrower.department == null ? "" : borrower.department;
    }
}

@RestController
@RequestMapping("/api/admin/attendance")
@PreAuthorize("hasRole('ADMIN')")
class AttendanceAdminController {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Manila");
    private final LibraryVisitRepository visits;
    private final ServiceFeedbackRepository feedback;

    AttendanceAdminController(LibraryVisitRepository visits, ServiceFeedbackRepository feedback) {
        this.visits = visits; this.feedback = feedback;
    }

    @GetMapping
    Map<String, Object> list(@RequestParam(required = false) LocalDate from,
                             @RequestParam(required = false) LocalDate to,
                             @RequestParam(defaultValue = "") String q) {
        Range range = range(from, to);
        List<Map<String, Object>> rows = visits.report(range.from, range.to, q.trim()).stream().map(this::row).toList();
        Map<String, Long> summary = new LinkedHashMap<>();
        for (FeedbackRating rating : FeedbackRating.values()) summary.put(rating.name(), 0L);
        feedback.summary(range.from, range.to).forEach(item -> summary.put(((FeedbackRating)item[0]).name(), (Long)item[1]));
        return Map.of("visits", rows, "feedback", summary);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    ResponseEntity<String> csv(@RequestParam(required = false) LocalDate from,
                               @RequestParam(required = false) LocalDate to,
                               @RequestParam(defaultValue = "") String q) {
        Range range = range(from, to);
        StringBuilder csv = new StringBuilder("Date and Time,Name,Role,Grade and Section or Department,Purpose,Entry Method\n");
        for (LibraryVisit visit : visits.report(range.from, range.to, q.trim())) {
            csv.append(visit.checkedInAt.atZone(SCHOOL_ZONE).toLocalDateTime()).append(',')
                    .append(quote(visit.borrower.user.firstName + " " + visit.borrower.user.lastName)).append(',')
                    .append(visit.borrower.user.role).append(',')
                    .append(quote(group(visit.borrower))).append(',')
                    .append(quote(label(visit.purpose))).append(',')
                    .append(visit.entryMethod).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance-logbook.csv")
                .contentType(MediaType.parseMediaType("text/csv")).body(csv.toString());
    }

    private Map<String, Object> row(LibraryVisit visit) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", visit.id); row.put("checkedInAt", visit.checkedInAt);
        row.put("name", visit.borrower.user.firstName + " " + visit.borrower.user.lastName);
        row.put("role", visit.borrower.user.role); row.put("gradeSection", group(visit.borrower));
        row.put("purpose", visit.purpose); row.put("entryMethod", visit.entryMethod);
        return row;
    }

    private String group(Borrower borrower) {
        if (borrower.gradeSection != null && !borrower.gradeSection.isBlank()) return borrower.gradeSection;
        return borrower.department == null ? "" : borrower.department;
    }

    private String label(VisitPurpose purpose) {
        return switch (purpose) {
            case ATTEND_CLASS -> "Attend Class";
            case PLAY_BOARD_GAMES -> "Play Board Games";
            case USE_DIGITAL_SERVICES -> "Use Digital Services";
            default -> purpose.name().charAt(0) + purpose.name().substring(1).toLowerCase(Locale.ROOT);
        };
    }

    private Range range(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(SCHOOL_ZONE) : from;
        LocalDate end = to == null ? start : to;
        if (end.isBefore(start)) throw new ApiException(HttpStatus.BAD_REQUEST, "End date must not be before start date");
        if (start.plusYears(1).isBefore(end)) throw new ApiException(HttpStatus.BAD_REQUEST, "Date range cannot exceed one year");
        return new Range(start.atStartOfDay(SCHOOL_ZONE).toInstant(), end.plusDays(1).atStartOfDay(SCHOOL_ZONE).toInstant());
    }

    private String quote(String value) { return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\""; }
    private record Range(Instant from, Instant to) {}
}
