package ph.edu.vfgnhs.library;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
class AccessionNumberService {
    static final int MAX_BATCH_SIZE = 200;
    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)(\\d+)$");
    private final BookCopyRepository copies;

    AccessionNumberService(BookCopyRepository copies) {
        this.copies = copies;
    }

    /** Next school accession number for the current year, e.g. 2026-0001. */
    @Transactional
    String next() {
        return next(1).get(0);
    }

    @Transactional
    List<String> next(int count) {
        try {
            return allocate(count);
        } catch (DataIntegrityViolationException ex) {
            try {
                return allocate(count);
            } catch (DataIntegrityViolationException retry) {
                throw new ApiException(HttpStatus.CONFLICT, "Accession number already exists");
            }
        }
    }

    private List<String> allocate(int count) {
        validateCount(count);
        int year = Year.now(ZoneId.of("Asia/Manila")).getValue();
        String prefix = year + "-";
        List<String> existing = copies.lockAccessionNumbersStartingWith(prefix);
        int max = 0;
        for (String accession : existing) {
            max = Math.max(max, sequenceOf(accession, prefix));
        }
        List<String> result = new ArrayList<>(count);
        int seq = max + 1;
        while (result.size() < count) {
            String candidate = prefix + String.format(Locale.ROOT, "%04d", seq++);
            if (!copies.existsByAccessionNumberIgnoreCase(candidate)) result.add(candidate);
        }
        return result;
    }

    List<String> sequence(String start, int count) {
        validateCount(count);
        if (start == null || start.isBlank()) return next(count);

        String first = start.trim();
        if (count == 1) {
            rejectConflicts(List.of(first));
            return List.of(first);
        }

        Matcher matcher = TRAILING_NUMBER.matcher(first);
        if (!matcher.matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Starting accession number must end in digits when adding multiple copies");
        }
        String prefix = matcher.group(1);
        String numeric = matcher.group(2);
        long initial;
        try {
            initial = Long.parseLong(numeric);
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Starting accession number is too large");
        }

        List<String> result = new ArrayList<>(count);
        for (int offset = 0; offset < count; offset++) {
            long value;
            try {
                value = Math.addExact(initial, offset);
            } catch (ArithmeticException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Starting accession number is too large");
            }
            result.add(prefix + String.format(Locale.ROOT, "%0" + numeric.length() + "d", value));
        }
        rejectConflicts(result);
        return result;
    }

    private void rejectConflicts(List<String> accessions) {
        List<String> conflicts = accessions.stream()
                .filter(copies::existsByAccessionNumberIgnoreCase)
                .toList();
        if (!conflicts.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Accession number already exists: " + String.join(", ", conflicts));
        }
    }

    private static void validateCount(int count) {
        if (count < 1 || count > MAX_BATCH_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Quantity must be between 1 and " + MAX_BATCH_SIZE);
        }
    }

    private static int sequenceOf(String accession, String prefix) {
        if (accession == null) return 0;
        String value = accession.trim();
        if (!value.regionMatches(true, 0, prefix, 0, prefix.length())) return 0;
        String suffix = value.substring(prefix.length()).trim();
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) return 0;
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
