package ph.edu.vfgnhs.library;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class ApiSupport {
    private final UserRepository users;
    private final AuditLogRepository audits;

    ApiSupport(UserRepository users, AuditLogRepository audits) {
        this.users = users;
        this.audits = audits;
    }

    User current(Authentication authentication) {
        if (authentication == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
    }

    void audit(User actor, String action, String type, Object id, String details) {
        audit(actor, action, type, id, details, null, null);
    }

    void audit(User actor, String action, String type, Object id, String details, String ipAddress, String location) {
        AuditLog log = new AuditLog();
        log.actorId = actor == null ? null : actor.id;
        log.action = action;
        log.entityType = type;
        log.entityId = id == null ? null : id.toString();
        log.details = details;
        log.ipAddress = ipAddress;
        log.location = location;
        log.createdAt = Instant.now();
        audits.save(log);
    }

    <E extends Enum<E>> E parseEnum(Class<E> type, String value, String message) {
        if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, message);
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }

    Map<String, Object> user(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id); m.put("email", u.email); m.put("firstName", u.firstName);
        m.put("lastName", u.lastName); m.put("role", u.role); m.put("status", u.status);
        m.put("schoolId", u.schoolId); m.put("createdAt", u.createdAt);
        return m;
    }

    Map<String, Object> borrower(Borrower b) {
        Map<String, Object> m = new LinkedHashMap<>(user(b.user));
        m.put("borrowerId", b.id); m.put("cardNumber", b.cardNumber);
        m.put("gradeSection", b.gradeSection); m.put("department", b.department);
        if (b.classSection != null) {
            m.put("classSectionId", b.classSection.id);
            m.put("classSchoolYear", b.classSection.schoolYear);
        }
        m.put("phone", b.phone); m.put("borrowerStatus", b.status); m.put("notes", b.notes);
        return m;
    }

    List<Map<String, Object>> books(List<Book> found, List<BookCopy> copies) {
        Map<Long, List<BookCopy>> byBook = copies.stream()
                .collect(Collectors.groupingBy(c -> c.book.id, LinkedHashMap::new, Collectors.toList()));
        return found.stream().map(b -> book(b, byBook.getOrDefault(b.id, List.of()))).toList();
    }

    Map<String, Object> book(Book b, java.util.List<BookCopy> copies) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id); m.put("title", b.title); m.put("subtitle", b.subtitle);
        m.put("author", b.author); m.put("isbn", b.isbn); m.put("publisher", b.publisher);
        m.put("publicationYear", b.publicationYear); m.put("edition", b.edition);
        m.put("subject", b.subject); m.put("description", b.description); m.put("coverUrl", b.coverUrl);
        m.put("totalCopies", copies.size());
        m.put("availableCopies", copies.stream().filter(c -> c.status == CopyStatus.AVAILABLE).count());
        m.put("copies", copies.stream().map(this::copy).toList());
        return m;
    }

    Map<String, Object> copy(BookCopy c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id); m.put("bookId", c.book.id); m.put("title", c.book.title);
        m.put("author", c.book.author); m.put("accessionNumber", c.accessionNumber);
        m.put("callNumber", c.callNumber); m.put("volumeCopy", c.volumeCopy);
        m.put("pages", c.pages); m.put("dateReceived", c.dateReceived);
        m.put("sourceOfFund", c.sourceOfFund); m.put("cost", c.cost);
        m.put("status", c.status); m.put("remarks", c.remarks); m.put("barcode", c.barcode);
        return m;
    }

    Map<String, Object> loan(Loan l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.id); m.put("copyId", l.copy.id); m.put("bookId", l.copy.book.id);
        m.put("title", l.copy.book.title); m.put("author", l.copy.book.author);
        m.put("accessionNumber", l.copy.accessionNumber); m.put("callNumber", l.copy.callNumber);
        m.put("borrowerId", l.borrower.id); m.put("borrowerName", l.borrower.user.firstName + " " + l.borrower.user.lastName);
        m.put("cardNumber", l.borrower.cardNumber);
        m.put("gradeSection", l.borrower.gradeSection != null && !l.borrower.gradeSection.isBlank()
                ? l.borrower.gradeSection
                : l.borrower.department);
        m.put("borrowedAt", l.borrowedAt);
        m.put("dueAt", l.dueAt); m.put("returnedAt", l.returnedAt); m.put("notes", l.notes);
        m.put("overdue", l.returnedAt == null && l.dueAt.isBefore(Instant.now()));
        if (l.distribution != null) {
            m.put("distributionId", l.distribution.id);
            m.put("distributionGradeSection", l.distribution.gradeSection);
            m.put("distributionSchoolYear", l.distribution.schoolYear);
        }
        return m;
    }
}

class ApiException extends RuntimeException {
    final HttpStatus status;
    ApiException(HttpStatus status, String message) { super(message); this.status = status; }
}

@RestControllerAdvice
class ApiExceptionHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    org.springframework.http.ResponseEntity<Map<String, Object>> handle(ApiException ex) {
        return org.springframework.http.ResponseEntity.status(ex.status)
                .body(Map.of("message", ex.getMessage(), "status", ex.status.value()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> validation(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(e -> e.getField() + ": " + e.getDefaultMessage()).orElse("Invalid request");
        return Map.of("message", message, "status", 400);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Map<String, Object> denied() {
        return Map.of("message", "You do not have permission to perform this action", "status", 403);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, Object> conflict() {
        return Map.of("message", "That record already exists", "status", 409);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    Map<String, Object> unexpected(Exception ex) {
        log.error("Unhandled API error", ex);
        return Map.of("message", "The server could not complete the request", "status", 500);
    }
}
