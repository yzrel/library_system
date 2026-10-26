package ph.edu.vfgnhs.library;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

record ClassSectionRequest(String gradeLevel, String sectionName, String schoolYear, Boolean active) {}
record AdviserAssignmentRequest(Long teacherBorrowerId) {}

@RestController
class ClassSectionController {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Manila");
    private final ClassSectionRepository classes;
    private final ClassAdviserAssignmentRepository assignments;
    private final BorrowerRepository borrowers;
    private final ApiSupport api;

    ClassSectionController(ClassSectionRepository classes, ClassAdviserAssignmentRepository assignments,
                           BorrowerRepository borrowers, ApiSupport api) {
        this.classes = classes; this.assignments = assignments; this.borrowers = borrowers; this.api = api;
    }

    @GetMapping("/api/classes")
    @Transactional(readOnly = true)
    List<Map<String, Object>> publicOptions(@RequestParam(defaultValue = "") String schoolYear) {
        String year = schoolYear.isBlank() ? currentSchoolYear() : normalizeSchoolYear(schoolYear);
        return classes.findBySchoolYearAndActiveTrueOrderByGradeLevelAscSectionNameAsc(year)
                .stream().map(this::view).toList();
    }

    @GetMapping("/api/admin/classes")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    List<Map<String, Object>> list(@RequestParam(defaultValue = "") String schoolYear,
                                   @RequestParam(defaultValue = "") String active) {
        return classes.findAllByOrderBySchoolYearDescGradeLevelAscSectionNameAsc().stream()
                .filter(item -> schoolYear.isBlank() || item.schoolYear.equalsIgnoreCase(schoolYear.trim()))
                .filter(item -> active.isBlank() || item.active == Boolean.parseBoolean(active))
                .map(this::view).toList();
    }

    @PostMapping("/api/admin/classes")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> create(@RequestBody ClassSectionRequest request, Authentication auth) {
        Values values = validate(request);
        if (classes.existsBySchoolYearIgnoreCaseAndGradeLevelIgnoreCaseAndSectionNameIgnoreCase(
                values.schoolYear, values.gradeLevel, values.sectionName))
            throw new ApiException(HttpStatus.CONFLICT, "This grade and section already exists for the school year");
        ClassSection item = new ClassSection();
        item.gradeLevel = values.gradeLevel; item.sectionName = values.sectionName;
        item.schoolYear = values.schoolYear; item.active = request.active() == null || request.active();
        classes.save(item);
        api.audit(api.current(auth), "CREATE_CLASS_SECTION", "CLASS_SECTION", item.id, label(item) + ", " + item.schoolYear);
        return view(item);
    }

    @PatchMapping("/api/admin/classes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    Map<String, Object> update(@PathVariable Long id, @RequestBody ClassSectionRequest request, Authentication auth) {
        ClassSection item = classes.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Class not found"));
        Values values = validate(request);
        if (classes.existsBySchoolYearIgnoreCaseAndGradeLevelIgnoreCaseAndSectionNameIgnoreCaseAndIdNot(
                values.schoolYear, values.gradeLevel, values.sectionName, id))
            throw new ApiException(HttpStatus.CONFLICT, "This grade and section already exists for the school year");
        if (Boolean.FALSE.equals(request.active()) && item.active) ensureCanDeactivate(item.id);
        item.gradeLevel = values.gradeLevel; item.sectionName = values.sectionName;
        item.schoolYear = values.schoolYear;
        if (request.active() != null) item.active = request.active();
        classes.save(item);
        borrowers.findByClassSectionId(id).forEach(borrower -> {
            borrower.gradeSection = label(item); borrowers.save(borrower);
        });
        assignments.findByClassSectionId(id).ifPresent(assignment -> {
            assignment.teacher.gradeSection = label(item); borrowers.save(assignment.teacher);
        });
        api.audit(api.current(auth), "UPDATE_CLASS_SECTION", "CLASS_SECTION", id, label(item) + ", " + item.schoolYear);
        return view(item);
    }

    @PutMapping("/api/admin/classes/{id}/adviser")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    Map<String, Object> assignAdviser(@PathVariable Long id, @RequestBody AdviserAssignmentRequest request,
                                      Authentication auth) {
        ClassSection item = classes.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Class not found"));
        ClassAdviserAssignment existing = assignments.findByClassSectionId(id).orElse(null);
        if (request.teacherBorrowerId() == null) {
            if (existing != null) {
                existing.teacher.gradeSection = null; borrowers.save(existing.teacher);
                assignments.delete(existing);
                api.audit(api.current(auth), "REMOVE_CLASS_ADVISER", "CLASS_SECTION", id, label(item));
            }
            return view(item);
        }
        if (!item.active) throw new ApiException(HttpStatus.CONFLICT, "An inactive class cannot receive an adviser");
        Borrower teacher = borrowers.findById(request.teacherBorrowerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Teacher not found"));
        if (teacher.user.role != Role.TEACHER)
            throw new ApiException(HttpStatus.BAD_REQUEST, "The adviser must have a teacher account");
        if (teacher.user.status != AccountStatus.ACTIVE || teacher.status != BorrowerStatus.ACTIVE)
            throw new ApiException(HttpStatus.CONFLICT, "Teacher account is not active");
        ClassAdviserAssignment teacherAssignment = assignments.findByTeacherId(teacher.id).orElse(null);
        if (teacherAssignment != null && !teacherAssignment.classSection.id.equals(id)) {
            assignments.delete(teacherAssignment);
            assignments.flush();
        }
        if (existing != null && !existing.teacher.id.equals(teacher.id))
            throw new ApiException(HttpStatus.CONFLICT, "This class already has an adviser");
        if (existing == null) {
            existing = new ClassAdviserAssignment(); existing.classSection = item; existing.teacher = teacher;
            assignments.save(existing);
        }
        teacher.gradeSection = label(item); borrowers.save(teacher);
        api.audit(api.current(auth), "ASSIGN_CLASS_ADVISER", "CLASS_SECTION", id,
                label(item) + " to " + teacher.user.firstName + " " + teacher.user.lastName);
        return view(item);
    }

    private void ensureCanDeactivate(Long id) {
        if (assignments.countByClassSectionId(id) > 0)
            throw new ApiException(HttpStatus.CONFLICT, "Remove the class adviser before deactivating this class");
    }

    private Map<String, Object> view(ClassSection item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.id); result.put("gradeLevel", item.gradeLevel); result.put("sectionName", item.sectionName);
        result.put("label", label(item)); result.put("schoolYear", item.schoolYear); result.put("active", item.active);
        assignments.findByClassSectionId(item.id).ifPresentOrElse(assignment -> {
            result.put("adviserBorrowerId", assignment.teacher.id);
            result.put("adviserName", assignment.teacher.user.firstName + " " + assignment.teacher.user.lastName);
        }, () -> {
            result.put("adviserBorrowerId", null); result.put("adviserName", null);
        });
        return result;
    }

    static String label(ClassSection item) {
        return item.sectionName == null || item.sectionName.isBlank()
                ? item.gradeLevel : item.gradeLevel + " - " + item.sectionName;
    }

    static String currentSchoolYear() {
        LocalDate today = LocalDate.now(SCHOOL_ZONE);
        int start = today.getMonthValue() >= 6 ? today.getYear() : today.getYear() - 1;
        return start + "-" + (start + 1);
    }

    private Values validate(ClassSectionRequest request) {
        String grade = request.gradeLevel() == null ? "" : request.gradeLevel().trim();
        String section = request.sectionName() == null ? "" : request.sectionName().trim();
        if (grade.isBlank() || grade.length() > 80)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Grade level is required and must not exceed 80 characters");
        if (section.isBlank() || section.length() > 80)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Section name is required and must not exceed 80 characters");
        return new Values(grade, section, normalizeSchoolYear(request.schoolYear()));
    }

    private String normalizeSchoolYear(String value) {
        String year = value == null ? "" : value.trim();
        if (!year.matches("\\d{4}-\\d{4}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "School year must use YYYY-YYYY");
        String[] parts = year.split("-");
        if (Integer.parseInt(parts[1]) != Integer.parseInt(parts[0]) + 1)
            throw new ApiException(HttpStatus.BAD_REQUEST, "School year must contain consecutive years");
        return year;
    }

    private record Values(String gradeLevel, String sectionName, String schoolYear) {}
}
