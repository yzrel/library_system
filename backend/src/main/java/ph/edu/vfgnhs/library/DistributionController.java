package ph.edu.vfgnhs.library;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

record DistributionPreviewLine(@NotNull Long bookId, @NotNull @Min(1) Integer quantity) {}
record DistributionPreviewRequest(
        @NotNull Long teacherBorrowerId,
        @NotBlank @Size(max = 120) String gradeSection,
        @NotBlank @Size(max = 20) String schoolYear,
        @NotNull LocalDate expectedReturnDate,
        @NotEmpty List<@Valid DistributionPreviewLine> lines) {}
record DistributionCreateLine(@NotNull Long bookId, @NotEmpty List<@NotNull Long> copyIds) {}
record DistributionCreateRequest(
        @NotNull Long teacherBorrowerId,
        @NotBlank @Size(max = 120) String gradeSection,
        @NotBlank @Size(max = 20) String schoolYear,
        @NotNull LocalDate expectedReturnDate,
        @Size(max = 1000) String notes,
        @NotEmpty List<@Valid DistributionCreateLine> lines) {}
record DistributionReturnRequest(@NotEmpty List<@NotNull Long> loanIds) {}

@RestController
@RequestMapping("/api/admin/distributions")
@PreAuthorize("hasRole('ADMIN')")
class DistributionAdminController {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Manila");
    private final ClassDistributionRepository distributions;
    private final LoanRepository loans;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final BorrowerRepository borrowers;
    private final ClassAdviserAssignmentRepository adviserAssignments;
    private final ApiSupport api;
    private final CirculationService circulation;

    DistributionAdminController(ClassDistributionRepository distributions, LoanRepository loans,
                                BookRepository books, BookCopyRepository copies, BorrowerRepository borrowers,
                                ClassAdviserAssignmentRepository adviserAssignments,
                                ApiSupport api, CirculationService circulation) {
        this.distributions = distributions; this.loans = loans; this.books = books; this.copies = copies;
        this.borrowers = borrowers; this.adviserAssignments = adviserAssignments;
        this.api = api; this.circulation = circulation;
    }

    @PostMapping("/preview")
    @Transactional(readOnly = true)
    Map<String, Object> preview(@Valid @RequestBody DistributionPreviewRequest request) {
        Borrower teacher = teacher(request.teacherBorrowerId());
        validateHeader(request.gradeSection(), request.schoolYear(), request.expectedReturnDate());
        validateAdvisory(teacher, request.gradeSection(), request.schoolYear());
        Set<Long> bookIds = new HashSet<>();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (DistributionPreviewLine line : request.lines()) {
            if (!bookIds.add(line.bookId()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Each title may appear only once");
            Book book = books.findById(line.bookId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Book not found"));
            List<BookCopy> available = copies.findAvailableByBookId(book.id).stream()
                    .filter(copy -> !loans.existsByCopyIdAndReturnedAtIsNull(copy.id)).toList();
            List<BookCopy> proposed = available.stream().limit(line.quantity()).toList();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bookId", book.id); item.put("title", book.title); item.put("author", book.author);
            item.put("quantity", line.quantity());
            item.put("proposedCopies", proposed.stream().map(api::copy).toList());
            item.put("availableCopies", available.stream().map(api::copy).toList());
            item.put("shortfall", Math.max(0, line.quantity() - proposed.size()));
            lines.add(item);
        }
        return headerPreview(teacher, request.gradeSection(), request.schoolYear(), request.expectedReturnDate(), lines);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> create(@Valid @RequestBody DistributionCreateRequest request, Authentication auth) {
        User actor = api.current(auth);
        Borrower teacher = teacher(request.teacherBorrowerId());
        validateHeader(request.gradeSection(), request.schoolYear(), request.expectedReturnDate());
        validateAdvisory(teacher, request.gradeSection(), request.schoolYear());
        Set<Long> allIds = new LinkedHashSet<>();
        Map<Long, Long> expectedBooks = new HashMap<>();
        for (DistributionCreateLine line : request.lines()) {
            if (!books.existsById(line.bookId())) throw new ApiException(HttpStatus.NOT_FOUND, "Book not found");
            for (Long copyId : line.copyIds()) {
                if (!allIds.add(copyId)) throw new ApiException(HttpStatus.BAD_REQUEST, "A copy can only be assigned once");
                expectedBooks.put(copyId, line.bookId());
            }
        }
        List<BookCopy> selected = copies.lockAllById(new ArrayList<>(allIds));
        if (selected.size() != allIds.size()) throw new ApiException(HttpStatus.NOT_FOUND, "One or more copies no longer exist");
        for (BookCopy copy : selected) {
            if (!copy.book.id.equals(expectedBooks.get(copy.id)))
                throw new ApiException(HttpStatus.BAD_REQUEST, copy.accessionNumber + " does not belong to the selected title");
            if (copy.status != CopyStatus.AVAILABLE || loans.existsByCopyIdAndReturnedAtIsNull(copy.id))
                throw new ApiException(HttpStatus.CONFLICT, copy.accessionNumber + " is no longer available");
        }

        ClassDistribution distribution = new ClassDistribution();
        distribution.teacher = teacher;
        distribution.gradeSection = request.gradeSection().trim();
        distribution.schoolYear = normalizeSchoolYear(request.schoolYear());
        distribution.expectedReturnDate = request.expectedReturnDate();
        distribution.distributedAt = Instant.now();
        distribution.status = DistributionStatus.ACTIVE;
        distribution.notes = blankToNull(request.notes());
        distribution.createdBy = actor.id;
        distributions.save(distribution);

        teacher.gradeSection = distribution.gradeSection;
        borrowers.save(teacher);
        Instant dueAt = distribution.expectedReturnDate.atTime(23, 59, 59).atZone(SCHOOL_ZONE).toInstant();
        for (BookCopy copy : selected) {
            Loan loan = new Loan();
            loan.copy = copy; loan.borrower = teacher; loan.borrowedAt = distribution.distributedAt;
            loan.dueAt = dueAt; loan.notes = distribution.notes; loan.checkedOutBy = actor.id;
            loan.distribution = distribution;
            loans.save(loan);
            copy.status = CopyStatus.ON_LOAN;
            copies.save(copy);
        }
        api.audit(actor, "CLASS_DISTRIBUTE", "CLASS_DISTRIBUTION", distribution.id,
                distribution.gradeSection + ", " + distribution.schoolYear + ", copies=" + selected.size());
        return detail(distribution);
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<Map<String, Object>> list(@RequestParam(defaultValue = "") String q,
                                   @RequestParam(defaultValue = "") String schoolYear,
                                   @RequestParam(defaultValue = "") String status) {
        String query = q.trim().toLowerCase(Locale.ROOT);
        return distributions.findAllDetailed().stream()
                .filter(d -> schoolYear.isBlank() || d.schoolYear.equalsIgnoreCase(schoolYear.trim()))
                .filter(d -> status.isBlank() || d.status.name().equalsIgnoreCase(status.trim()))
                .filter(d -> query.isBlank() || (d.teacher.user.firstName + " " + d.teacher.user.lastName + " " +
                        d.gradeSection + " " + d.schoolYear).toLowerCase(Locale.ROOT).contains(query))
                .map(this::summary).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    Map<String, Object> get(@PathVariable Long id) {
        return detail(distributions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Distribution not found")));
    }

    @PostMapping("/{id}/returns")
    @Transactional
    Map<String, Object> returnSelected(@PathVariable Long id, @Valid @RequestBody DistributionReturnRequest request,
                                       Authentication auth) {
        ClassDistribution distribution = distributions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Distribution not found"));
        User actor = api.current(auth);
        Set<Long> requested = new LinkedHashSet<>(request.loanIds());
        if (requested.size() != request.loanIds().size())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate return item");
        Map<Long, Loan> items = new HashMap<>();
        loans.findByDistributionDetailed(id).forEach(loan -> items.put(loan.id, loan));
        for (Long loanId : requested) {
            Loan loan = items.get(loanId);
            if (loan == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Loan does not belong to this distribution");
            circulation.returnLoan(loan, actor);
        }
        api.audit(actor, "CLASS_RETURN_SELECTED", "CLASS_DISTRIBUTION", id, "copies=" + requested.size());
        return detail(distribution);
    }

    @PostMapping("/{id}/return-all")
    @Transactional
    Map<String, Object> returnAll(@PathVariable Long id, Authentication auth) {
        ClassDistribution distribution = distributions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Distribution not found"));
        User actor = api.current(auth);
        List<Loan> active = loans.findByDistributionDetailed(id).stream().filter(loan -> loan.returnedAt == null).toList();
        if (active.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "All copies have already been returned");
        active.forEach(loan -> circulation.returnLoan(loan, actor));
        api.audit(actor, "CLASS_RETURN_ALL", "CLASS_DISTRIBUTION", id, "copies=" + active.size());
        return detail(distribution);
    }

    private Borrower teacher(Long id) {
        Borrower teacher = borrowers.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Teacher account not found"));
        if (teacher.user.role != Role.TEACHER)
            throw new ApiException(HttpStatus.BAD_REQUEST, "The custodian must be a teacher");
        if (teacher.status != BorrowerStatus.ACTIVE || teacher.user.status != AccountStatus.ACTIVE)
            throw new ApiException(HttpStatus.CONFLICT, "Teacher account is not active");
        return teacher;
    }

    private void validateHeader(String gradeSection, String schoolYear, LocalDate expectedReturnDate) {
        if (gradeSection == null || gradeSection.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Grade and section is required");
        String normalized = normalizeSchoolYear(schoolYear);
        String[] years = normalized.split("-");
        if (Integer.parseInt(years[1]) != Integer.parseInt(years[0]) + 1)
            throw new ApiException(HttpStatus.BAD_REQUEST, "School year must contain consecutive years");
        if (expectedReturnDate.isBefore(LocalDate.now(SCHOOL_ZONE)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Expected return date cannot be in the past");
    }

    private void validateAdvisory(Borrower teacher, String gradeSection, String schoolYear) {
        ClassAdviserAssignment assignment = adviserAssignments.findByTeacherId(teacher.id)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Teacher does not have an advisory class"));
        if (!assignment.classSection.active)
            throw new ApiException(HttpStatus.CONFLICT, "Teacher's advisory class is inactive");
        if (!ClassSectionController.label(assignment.classSection).equalsIgnoreCase(gradeSection.trim())
                || !assignment.classSection.schoolYear.equalsIgnoreCase(schoolYear.trim()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Distribution class must match the teacher's advisory assignment");
    }

    private String normalizeSchoolYear(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("\\d{4}-\\d{4}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "School year must use YYYY-YYYY");
        return normalized;
    }

    private Map<String, Object> headerPreview(Borrower teacher, String gradeSection, String schoolYear,
                                               LocalDate expectedReturnDate, List<Map<String, Object>> lines) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("teacher", api.borrower(teacher)); result.put("gradeSection", gradeSection.trim());
        result.put("schoolYear", normalizeSchoolYear(schoolYear)); result.put("expectedReturnDate", expectedReturnDate);
        result.put("lines", lines); return result;
    }

    private Map<String, Object> summary(ClassDistribution distribution) {
        List<Loan> items = loans.findByDistributionDetailed(distribution.id);
        long active = items.stream().filter(item -> item.returnedAt == null).count();
        Map<String, Object> result = header(distribution);
        result.put("totalCopies", items.size()); result.put("activeCopies", active);
        result.put("returnedCopies", items.size() - active);
        result.put("overdueCopies", items.stream().filter(item -> item.returnedAt == null && item.dueAt.isBefore(Instant.now())).count());
        return result;
    }

    private Map<String, Object> detail(ClassDistribution distribution) {
        Map<String, Object> result = summary(distribution);
        result.put("loans", loans.findByDistributionDetailed(distribution.id).stream().map(api::loan).toList());
        return result;
    }

    private Map<String, Object> header(ClassDistribution distribution) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", distribution.id); result.put("teacherBorrowerId", distribution.teacher.id);
        result.put("teacherName", distribution.teacher.user.firstName + " " + distribution.teacher.user.lastName);
        result.put("teacherCardNumber", distribution.teacher.cardNumber);
        result.put("gradeSection", distribution.gradeSection); result.put("schoolYear", distribution.schoolYear);
        result.put("distributedAt", distribution.distributedAt); result.put("expectedReturnDate", distribution.expectedReturnDate);
        result.put("status", distribution.status); result.put("notes", distribution.notes);
        return result;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
