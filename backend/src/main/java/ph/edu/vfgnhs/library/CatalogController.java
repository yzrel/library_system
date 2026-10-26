package ph.edu.vfgnhs.library;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

record BookRequest(@NotBlank String title, String subtitle, String author, String isbn, String publisher,
                   Integer publicationYear, String edition, String subject, String description, String coverUrl) {}
record CopyRequest(String accessionNumber, String callNumber, String volumeCopy, String pages,
                   LocalDate dateReceived, String sourceOfFund, BigDecimal cost, String status, String remarks, String barcode) {}
record BatchCopyRequest(@NotNull @Min(1) @Max(AccessionNumberService.MAX_BATCH_SIZE) Integer quantity,
                        String startingAccessionNumber, String callNumber, String volumeCopy, String pages,
                        LocalDate dateReceived, String sourceOfFund, BigDecimal cost, String remarks, String barcode) {}
record IntakeRequest(Long existingBookId, @NotBlank String title, String subtitle, String author, String isbn,
                     String publisher, Integer publicationYear, String edition, String subject, String description,
                     String coverUrl, String accessionNumber, String callNumber, String volumeCopy,
                     String pages, LocalDate dateReceived, String sourceOfFund, BigDecimal cost, String status,
                     String remarks, String barcode,
                     @Min(1) @Max(AccessionNumberService.MAX_BATCH_SIZE) Integer quantity) {}
record BorrowerUpdate(String gradeSection, String department, String phone, String status, String notes) {}

@RestController
@RequestMapping("/api/opac")
class OpacController {
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final ApiSupport api;

    OpacController(BookRepository books, BookCopyRepository copies, ApiSupport api) {
        this.books = books; this.copies = copies; this.api = api;
    }

    @GetMapping("/books")
    List<Map<String, Object>> search(@RequestParam(defaultValue = "") String q) {
        List<Book> found = books.search(q.trim());
        return api.books(found, found.isEmpty() ? List.of() : copies.findByBookIdIn(found.stream().map(b -> b.id).toList()));
    }

    @GetMapping("/books/{id}")
    Map<String, Object> detail(@PathVariable Long id) {
        Book book = books.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Book not found"));
        return api.book(book, copies.findByBookId(id));
    }
}

@RestController
@RequestMapping("/api/admin/catalog")
@PreAuthorize("hasRole('ADMIN')")
class CatalogAdminController {
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final BookSubjectRepository subjects;
    private final LoanRepository loans;
    private final ApiSupport api;
    private final IsbnLookupService isbnLookup;
    private final AccessionNumberService accessionNumbers;

    CatalogAdminController(BookRepository books, BookCopyRepository copies, BookSubjectRepository subjects,
                           LoanRepository loans, ApiSupport api, IsbnLookupService isbnLookup,
                           AccessionNumberService accessionNumbers) {
        this.books = books; this.copies = copies; this.subjects = subjects; this.loans = loans; this.api = api;
        this.isbnLookup = isbnLookup; this.accessionNumbers = accessionNumbers;
    }

    @GetMapping("/books")
    List<Map<String, Object>> books(@RequestParam(defaultValue = "") String q) {
        List<Book> found = books.search(q.trim());
        return api.books(found, found.isEmpty() ? List.of() : copies.findByBookIdIn(found.stream().map(b -> b.id).toList()));
    }

    @GetMapping("/next-accession")
    Map<String, Object> nextAccession() {
        return Map.of("accessionNumber", accessionNumbers.next());
    }

    @GetMapping("/isbn-lookup")
    Map<String, Object> isbnLookup(@RequestParam String isbn) {
        Map<String, Object> result = isbnLookup.lookup(isbn);
        result.put("suggestedAccessionNumber", accessionNumbers.next());
        return result;
    }

    @PostMapping("/intake")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> intake(@Valid @RequestBody IntakeRequest request, Authentication auth) {
        int quantity = request.quantity() == null ? 1 : request.quantity();
        List<String> accessions = accessionNumbers.sequence(request.accessionNumber(), quantity);
        String barcode = request.barcode() == null || request.barcode().isBlank() ? null : request.barcode().trim();
        if (quantity > 1 && barcode != null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Barcode is only allowed when adding one copy");
        if (barcode != null && copies.findByBarcode(barcode).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "Barcode already exists");

        User actor = api.current(auth);
        Book book = resolveIntakeBook(request);
        boolean createdBook = book.id == null;
        if (createdBook) {
            book.createdAt = Instant.now();
            book.updatedAt = book.createdAt;
            books.save(book);
            api.audit(actor, "CREATE_BOOK", "BOOK", book.id, book.title);
        }

        List<BookCopy> createdCopies = createCopies(book, accessions,
                new CopyRequest(null, request.callNumber(), request.volumeCopy(), request.pages(),
                        request.dateReceived(), request.sourceOfFund(), request.cost(),
                        request.status(), request.remarks(), barcode), actor);
        BookCopy firstCopy = createdCopies.get(0);
        api.audit(actor, "ISBN_INTAKE", "BOOK_COPY", firstCopy.id,
                (createdBook ? "new title " : "existing title ") + book.title + " / "
                        + accessions.get(0) + (quantity > 1 ? "–" + accessions.get(quantity - 1) : ""));

        Map<String, Object> result = new java.util.LinkedHashMap<>(api.book(book, copies.findByBookId(book.id)));
        result.put("createdBook", createdBook);
        result.put("copy", api.copy(firstCopy));
        result.put("createdCopies", createdCopies.stream().map(api::copy).toList());
        result.put("firstAccession", accessions.get(0));
        result.put("lastAccession", accessions.get(accessions.size() - 1));
        return result;
    }

    @PostMapping("/books")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> createBook(@Valid @RequestBody BookRequest request, Authentication auth) {
        Book book = new Book(); apply(book, request); book.createdAt = Instant.now(); book.updatedAt = book.createdAt;
        books.save(book); api.audit(api.current(auth), "CREATE_BOOK", "BOOK", book.id, book.title);
        return api.book(book, List.of());
    }

    @PutMapping("/books/{id}")
    @Transactional
    Map<String, Object> updateBook(@PathVariable Long id, @Valid @RequestBody BookRequest request, Authentication auth) {
        Book book = books.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Book not found"));
        apply(book, request); book.updatedAt = Instant.now(); books.save(book);
        api.audit(api.current(auth), "UPDATE_BOOK", "BOOK", id, book.title);
        return api.book(book, copies.findByBookId(id));
    }

    @PostMapping("/books/{bookId}/copies")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> addCopy(@PathVariable Long bookId, @Valid @RequestBody CopyRequest request, Authentication auth) {
        String accession = request.accessionNumber() == null || request.accessionNumber().isBlank()
                ? accessionNumbers.next()
                : request.accessionNumber().trim();
        if (copies.existsByAccessionNumberIgnoreCase(accession))
            throw new ApiException(HttpStatus.CONFLICT, "Accession number already exists");
        if (request.barcode() != null && !request.barcode().isBlank()
                && copies.findByBarcode(request.barcode().trim()).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "Barcode already exists");
        Book book = books.findById(bookId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Book not found"));
        BookCopy copy = new BookCopy(); copy.book = book;
        apply(copy, new CopyRequest(accession, request.callNumber(), request.volumeCopy(), request.pages(),
                request.dateReceived(), request.sourceOfFund(), request.cost(), request.status(),
                request.remarks(), request.barcode()));
        copies.save(copy);
        api.audit(api.current(auth), "CREATE_COPY", "BOOK_COPY", copy.id, copy.accessionNumber);
        return api.copy(copy);
    }

    @PostMapping("/books/{bookId}/copies/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> addCopies(@PathVariable Long bookId, @Valid @RequestBody BatchCopyRequest request,
                                  Authentication auth) {
        int quantity = request.quantity();
        String barcode = request.barcode() == null || request.barcode().isBlank() ? null : request.barcode().trim();
        if (quantity > 1 && barcode != null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Barcode is only allowed when adding one copy");
        if (barcode != null && copies.findByBarcode(barcode).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "Barcode already exists");

        Book book = books.findById(bookId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Book not found"));
        List<String> accessions = accessionNumbers.sequence(request.startingAccessionNumber(), quantity);
        User actor = api.current(auth);
        List<BookCopy> created = createCopies(book, accessions,
                new CopyRequest(null, request.callNumber(), request.volumeCopy(), request.pages(),
                        request.dateReceived(), request.sourceOfFund(), request.cost(), "AVAILABLE",
                        request.remarks(), barcode), actor);
        api.audit(actor, "BULK_CREATE_COPIES", "BOOK", book.id,
                book.title + ", " + accessions.get(0) + "–" + accessions.get(accessions.size() - 1)
                        + ", copies=" + quantity);
        return Map.of(
                "copies", created.stream().map(api::copy).toList(),
                "firstAccession", accessions.get(0),
                "lastAccession", accessions.get(accessions.size() - 1));
    }

    @PutMapping("/copies/{id}")
    @Transactional
    Map<String, Object> updateCopy(@PathVariable Long id, @Valid @RequestBody CopyRequest request, Authentication auth) {
        BookCopy copy = copies.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Copy not found"));
        if (request.accessionNumber() == null || request.accessionNumber().isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Accession number is required");
        copies.findByAccessionNumberIgnoreCase(request.accessionNumber().trim())
                .filter(other -> !other.id.equals(id))
                .ifPresent(other -> { throw new ApiException(HttpStatus.CONFLICT, "Accession number already exists"); });
        if (request.barcode() != null && !request.barcode().isBlank()) {
            copies.findByBarcode(request.barcode().trim())
                    .filter(other -> !other.id.equals(id))
                    .ifPresent(other -> { throw new ApiException(HttpStatus.CONFLICT, "Barcode already exists"); });
        }
        CopyStatus requestedStatus = request.status() == null || request.status().isBlank()
                ? copy.status : api.parseEnum(CopyStatus.class, request.status(), "Choose a valid copy status");
        if (copy.status == CopyStatus.ON_LOAN && requestedStatus == CopyStatus.AVAILABLE)
            throw new ApiException(HttpStatus.CONFLICT, "Return this copy in Circulation before marking it available");
        apply(copy, request); copies.save(copy);
        api.audit(api.current(auth), "UPDATE_COPY", "BOOK_COPY", id, copy.accessionNumber + " " + copy.status);
        return api.copy(copy);
    }

    @DeleteMapping("/copies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void deleteCopy(@PathVariable Long id, Authentication auth) {
        BookCopy copy = copies.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Copy not found"));
        if (copy.status == CopyStatus.ON_LOAN || loans.existsByCopyIdAndReturnedAtIsNull(copy.id))
            throw new ApiException(HttpStatus.CONFLICT, "Return this copy in Circulation before deleting it");
        if (loans.existsByCopyId(copy.id))
            throw new ApiException(HttpStatus.CONFLICT,
                    "This copy has circulation history and cannot be deleted. Mark it weeded or archived instead.");
        String accession = copy.accessionNumber;
        copies.delete(copy);
        api.audit(api.current(auth), "DELETE_COPY", "BOOK_COPY", id, accession);
    }

    @GetMapping("/copies")
    List<Map<String, Object>> allCopies() { return copies.findAllWithBook().stream().map(api::copy).toList(); }

    private List<BookCopy> createCopies(Book book, List<String> accessions, CopyRequest template, User actor) {
        List<BookCopy> created = new java.util.ArrayList<>(accessions.size());
        for (int index = 0; index < accessions.size(); index++) {
            BookCopy copy = new BookCopy();
            copy.book = book;
            apply(copy, new CopyRequest(accessions.get(index), template.callNumber(), template.volumeCopy(),
                    template.pages(), template.dateReceived(), template.sourceOfFund(), template.cost(),
                    template.status(), template.remarks(), index == 0 ? template.barcode() : null));
            copies.save(copy);
            api.audit(actor, "CREATE_COPY", "BOOK_COPY", copy.id, copy.accessionNumber);
            created.add(copy);
        }
        return created;
    }

    private Book resolveIntakeBook(IntakeRequest request) {
        if (request.existingBookId() != null) {
            return books.findById(request.existingBookId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Book not found"));
        }
        if (request.isbn() != null && !request.isbn().isBlank()) {
            var local = isbnLookup.findLocal(request.isbn());
            if (local.isPresent()) return local.get();
        }
        Book book = new Book();
        apply(book, new BookRequest(request.title(), request.subtitle(), request.author(), request.isbn(),
                request.publisher(), request.publicationYear(), request.edition(), request.subject(),
                request.description(), request.coverUrl()));
        return book;
    }

    private void apply(Book b, BookRequest r) {
        b.title = r.title().trim(); b.subtitle = IsbnSupport.blankToNull(r.subtitle());
        b.author = IsbnSupport.blankToNull(r.author());
        b.isbn = IsbnSupport.normalizeStored(r.isbn());
        b.publisher = IsbnSupport.blankToNull(r.publisher());
        b.publicationYear = r.publicationYear();
        b.edition = IsbnSupport.blankToNull(r.edition());
        b.subject = resolveSubject(r.subject());
        b.description = IsbnSupport.blankToNull(r.description());
        b.coverUrl = IsbnSupport.blankToNull(r.coverUrl());
    }

    private void apply(BookCopy c, CopyRequest r) {
        c.accessionNumber = r.accessionNumber().trim(); c.callNumber = IsbnSupport.blankToNull(r.callNumber());
        c.volumeCopy = IsbnSupport.blankToNull(r.volumeCopy());
        c.pages = IsbnSupport.blankToNull(r.pages()); c.dateReceived = r.dateReceived();
        c.sourceOfFund = IsbnSupport.blankToNull(r.sourceOfFund()); c.cost = r.cost();
        c.status = r.status() == null || r.status().isBlank() ? CopyStatus.AVAILABLE : api.parseEnum(CopyStatus.class, r.status(), "Choose a valid copy status");
        c.remarks = IsbnSupport.blankToNull(r.remarks());
        c.barcode = r.barcode() == null || r.barcode().isBlank() ? null : r.barcode().trim();
    }

    private String resolveSubject(String raw) {
        if (raw == null || raw.isBlank()) return null;
        BookSubject subject = subjects.findByNameIgnoreCase(raw.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Choose a subject from the library subject list"));
        if (!subject.active)
            throw new ApiException(HttpStatus.BAD_REQUEST, "That subject is inactive. Choose another subject or reactivate it in Settings");
        return subject.name;
    }
}

@RestController
@RequestMapping("/api/admin/borrowers")
@PreAuthorize("hasRole('ADMIN')")
class BorrowerAdminController {
    private final BorrowerRepository borrowers;
    private final ApiSupport api;

    BorrowerAdminController(BorrowerRepository borrowers, ApiSupport api) { this.borrowers = borrowers; this.api = api; }

    @GetMapping
    List<Map<String, Object>> list(@RequestParam(defaultValue = "") String q) {
        List<Borrower> result = q.isBlank() ? borrowers.findAllWithUser() : borrowers.search(q.trim());
        return result.stream().map(api::borrower).toList();
    }

    @PatchMapping("/{id}")
    Map<String, Object> update(@PathVariable Long id, @RequestBody BorrowerUpdate request, Authentication auth) {
        Borrower borrower = borrowers.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Borrower not found"));
        borrower.gradeSection = request.gradeSection(); borrower.department = request.department();
        borrower.phone = request.phone(); borrower.notes = request.notes();
        if (request.status() != null) borrower.status = api.parseEnum(BorrowerStatus.class, request.status(), "Choose a valid borrower status");
        borrowers.save(borrower); api.audit(api.current(auth), "UPDATE_BORROWER", "BORROWER", id, borrower.status.name());
        return api.borrower(borrower);
    }
}
