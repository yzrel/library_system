package ph.edu.vfgnhs.library;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record CheckoutRequest(@NotNull Long copyId, @NotNull Long borrowerId, String notes) {}
record ReservationRequest(@NotNull Long bookId) {}
record AcquisitionRequestBody(@NotBlank String title, String author, String reason) {}

@RestController
@RequestMapping("/api/admin/circulation")
@PreAuthorize("hasRole('ADMIN')")
class CirculationAdminController {
    private final LoanRepository loans; private final BookCopyRepository copies;
    private final BorrowerRepository borrowers; private final SettingRepository settings; private final ApiSupport api;
    private final CirculationService circulation;

    CirculationAdminController(LoanRepository loans, BookCopyRepository copies, BorrowerRepository borrowers,
                               SettingRepository settings, ApiSupport api, CirculationService circulation) {
        this.loans = loans; this.copies = copies; this.borrowers = borrowers; this.settings = settings; this.api = api;
        this.circulation = circulation;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<Map<String, Object>> list(@RequestParam(defaultValue = "false") boolean active) {
        return (active ? loans.findActiveDetailed() : loans.findAllDetailed()).stream().map(api::loan).toList();
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> checkout(@Valid @RequestBody CheckoutRequest request, Authentication auth) {
        User actor = api.current(auth);
        BookCopy copy = copies.lockAllById(List.of(request.copyId())).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Copy not found"));
        Borrower borrower = borrowers.findById(request.borrowerId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Borrower not found"));
        if (copy.status != CopyStatus.AVAILABLE || loans.existsByCopyIdAndReturnedAtIsNull(copy.id))
            throw new ApiException(HttpStatus.CONFLICT, "This copy is not available");
        if (borrower.status != BorrowerStatus.ACTIVE || borrower.user.status != AccountStatus.ACTIVE)
            throw new ApiException(HttpStatus.CONFLICT, "Borrower is not active");
        int limit = settingInt(borrower.user.role == Role.TEACHER ? "teacher_loan_limit" : "student_loan_limit", borrower.user.role == Role.TEACHER ? 8 : 3);
        if (loans.countRegularActiveByBorrowerId(borrower.id) >= limit)
            throw new ApiException(HttpStatus.CONFLICT, "Borrower has reached the active loan limit");
        int days = settingInt(borrower.user.role == Role.TEACHER ? "teacher_loan_days" : "student_loan_days", borrower.user.role == Role.TEACHER ? 14 : 7);
        Loan loan = new Loan(); loan.copy = copy; loan.borrower = borrower; loan.borrowedAt = Instant.now();
        loan.dueAt = loan.borrowedAt.plus(days, ChronoUnit.DAYS); loan.notes = request.notes(); loan.checkedOutBy = actor.id;
        loans.save(loan); copy.status = CopyStatus.ON_LOAN; copies.save(copy);
        api.audit(actor, "CHECKOUT", "LOAN", loan.id, copy.accessionNumber + " to " + borrower.cardNumber);
        return api.loan(loan);
    }

    @PostMapping("/{id}/return")
    @Transactional
    Map<String, Object> returnBook(@PathVariable Long id, Authentication auth) {
        User actor = api.current(auth);
        Loan loan = loans.findDetailedById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Loan not found"));
        return api.loan(circulation.returnLoan(loan, actor));
    }

    private int settingInt(String key, int fallback) {
        return settings.findById(key).map(s -> {
            try { return Integer.parseInt(s.value); } catch (NumberFormatException e) { return fallback; }
        }).orElse(fallback);
    }
}

@RestController
@RequestMapping("/api/portal")
class PatronController {
    private final BorrowerRepository borrowers; private final LoanRepository loans;
    private final BookRepository books; private final ReservationRepository reservations;
    private final AcquisitionRequestRepository acquisitions; private final ApiSupport api;

    PatronController(BorrowerRepository borrowers, LoanRepository loans, BookRepository books,
                     ReservationRepository reservations, AcquisitionRequestRepository acquisitions, ApiSupport api) {
        this.borrowers = borrowers; this.loans = loans; this.books = books; this.reservations = reservations;
        this.acquisitions = acquisitions; this.api = api;
    }

    @GetMapping("/loans")
    List<Map<String, Object>> loans(Authentication auth) {
        Borrower b = ownBorrower(auth);
        return loans.findByBorrowerDetailed(b.id).stream().map(api::loan).toList();
    }

    @PostMapping("/reservations")
    @Transactional
    Map<String, Object> reserve(Authentication auth, @Valid @RequestBody ReservationRequest request) {
        User user = api.current(auth); Borrower borrower = ownBorrower(auth);
        Book book = books.findById(request.bookId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Book not found"));
        if (reservations.existsByBorrowerIdAndBookIdAndStatus(borrower.id, book.id, RequestStatus.PENDING))
            throw new ApiException(HttpStatus.CONFLICT, "You already have a pending reservation for this title");
        Reservation r = new Reservation(); r.book = book; r.borrower = borrower;
        r.status = RequestStatus.PENDING; r.requestedAt = Instant.now(); reservations.save(r);
        api.audit(user, "RESERVE", "BOOK", book.id, book.title);
        return Map.of("id", r.id, "bookId", book.id, "title", book.title, "status", r.status, "requestedAt", r.requestedAt);
    }

    @GetMapping("/reservations")
    List<Map<String, Object>> reservations(Authentication auth) {
        return reservations.findByBorrowerIdOrderByRequestedAtDesc(ownBorrower(auth).id).stream()
                .map(r -> Map.<String, Object>of("id", r.id, "bookId", r.book.id, "title", r.book.title,
                        "status", r.status, "requestedAt", r.requestedAt)).toList();
    }

    @PostMapping("/acquisition-requests")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Transactional
    Map<String, Object> acquire(Authentication auth, @Valid @RequestBody AcquisitionRequestBody request) {
        User user = api.current(auth); AcquisitionRequest a = new AcquisitionRequest();
        a.requester = user; a.title = request.title(); a.author = request.author(); a.reason = request.reason();
        a.status = RequestStatus.PENDING; a.requestedAt = Instant.now(); acquisitions.save(a);
        api.audit(user, "ACQUISITION_REQUEST", "ACQUISITION", a.id, a.title);
        return Map.of("id", a.id, "title", a.title, "status", a.status, "requestedAt", a.requestedAt);
    }

    @GetMapping("/acquisition-requests")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    List<Map<String, Object>> acquisitions(Authentication auth) {
        return acquisitions.findByRequesterIdOrderByRequestedAtDesc(api.current(auth).id).stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id); m.put("title", a.title); m.put("author", a.author);
            m.put("reason", a.reason); m.put("status", a.status); m.put("requestedAt", a.requestedAt); return m;
        }).toList();
    }

    private Borrower ownBorrower(Authentication auth) {
        return borrowers.findByUserId(api.current(auth).id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Borrower profile not found"));
    }
}
