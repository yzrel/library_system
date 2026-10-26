package ph.edu.vfgnhs.library;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record BookSubjectRequest(String name, Boolean active) {}

@RestController
@RequestMapping("/api/admin/subjects")
@PreAuthorize("hasRole('ADMIN')")
class SubjectController {
    private final BookSubjectRepository subjects;
    private final BookRepository books;
    private final ApiSupport api;

    SubjectController(BookSubjectRepository subjects, BookRepository books, ApiSupport api) {
        this.subjects = subjects; this.books = books; this.api = api;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<Map<String, Object>> list() {
        return subjects.findAllByOrderBySortOrderAscNameAsc().stream().map(this::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> create(@RequestBody BookSubjectRequest request, Authentication auth) {
        String name = validateName(request.name());
        if (subjects.existsByNameIgnoreCase(name))
            throw new ApiException(HttpStatus.CONFLICT, "That subject already exists");
        BookSubject item = new BookSubject();
        item.name = name;
        item.sortOrder = nextSortOrder();
        item.active = request.active() == null || request.active();
        subjects.save(item);
        api.audit(api.current(auth), "CREATE_SUBJECT", "BOOK_SUBJECT", item.id, item.name);
        return view(item);
    }

    @PatchMapping("/{id}")
    @Transactional
    Map<String, Object> update(@PathVariable Long id, @RequestBody BookSubjectRequest request, Authentication auth) {
        BookSubject item = subjects.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subject not found"));
        String previous = item.name;
        if (request.name() != null) {
            String name = validateName(request.name());
            if (subjects.existsByNameIgnoreCaseAndIdNot(name, id))
                throw new ApiException(HttpStatus.CONFLICT, "That subject already exists");
            item.name = name;
        }
        if (request.active() != null) item.active = request.active();
        subjects.save(item);
        if (!previous.equalsIgnoreCase(item.name)) {
            books.findBySubjectIgnoreCase(previous).forEach(book -> {
                book.subject = item.name;
                books.save(book);
            });
        }
        api.audit(api.current(auth), "UPDATE_SUBJECT", "BOOK_SUBJECT", id, item.name);
        return view(item);
    }

    private int nextSortOrder() {
        return subjects.findTopByOrderBySortOrderDesc().map(item -> item.sortOrder + 10).orElse(10);
    }

    private String validateName(String value) {
        String name = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (name.isBlank() || name.length() > 80)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Subject name is required and must not exceed 80 characters");
        return name;
    }

    private Map<String, Object> view(BookSubject item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.id);
        result.put("name", item.name);
        result.put("sortOrder", item.sortOrder);
        result.put("active", item.active);
        return result;
    }
}
