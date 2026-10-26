package ph.edu.vfgnhs.library;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class IsbnSupport {
    private IsbnSupport() {}

    static String digits(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toUpperCase(raw.charAt(i));
            if (Character.isDigit(c) || c == 'X') out.append(c);
        }
        return out.toString();
    }

    /** Canonical ISBN-13 when possible; otherwise cleaned ISBN-10 / digits. Empty if invalid. */
    static String canonicalize(String raw) {
        String cleaned = digits(raw);
        if (cleaned.isEmpty()) return "";
        if (cleaned.length() == 13 && isValidIsbn13(cleaned)) return cleaned;
        if (cleaned.length() == 10 && isValidIsbn10(cleaned)) return toIsbn13(cleaned);
        if (cleaned.length() == 13) return cleaned;
        if (cleaned.length() == 10) return cleaned;
        return "";
    }

    static boolean isValidLookupKey(String raw) {
        String cleaned = digits(raw);
        return cleaned.length() == 10 || cleaned.length() == 13;
    }

    /** Forms to match against stored ISBNs (hyphen-stripped). */
    static Set<String> matchKeys(String raw) {
        Set<String> keys = new LinkedHashSet<>();
        String cleaned = digits(raw);
        if (cleaned.isEmpty()) return keys;
        keys.add(cleaned);
        String canonical = canonicalize(raw);
        if (!canonical.isEmpty()) keys.add(canonical);
        if (cleaned.length() == 13 && cleaned.startsWith("978")) {
            String isbn10 = fromIsbn13(cleaned);
            if (!isbn10.isEmpty()) keys.add(isbn10);
        }
        if (cleaned.length() == 10 && isValidIsbn10(cleaned)) keys.add(toIsbn13(cleaned));
        return keys;
    }

    static boolean isValidIsbn10(String isbn10) {
        if (isbn10 == null || isbn10.length() != 10) return false;
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            char c = isbn10.charAt(i);
            if (!Character.isDigit(c)) return false;
            sum += (c - '0') * (10 - i);
        }
        char check = isbn10.charAt(9);
        int value = check == 'X' ? 10 : (Character.isDigit(check) ? check - '0' : -1);
        if (value < 0) return false;
        sum += value;
        return sum % 11 == 0;
    }

    static boolean isValidIsbn13(String isbn13) {
        if (isbn13 == null || isbn13.length() != 13 || !isbn13.chars().allMatch(Character::isDigit)) return false;
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = isbn13.charAt(i) - '0';
            sum += d * (i % 2 == 0 ? 1 : 3);
        }
        int check = (10 - (sum % 10)) % 10;
        return check == isbn13.charAt(12) - '0';
    }

    static String toIsbn13(String isbn10) {
        if (!isValidIsbn10(isbn10)) return "";
        String core = "978" + isbn10.substring(0, 9);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = core.charAt(i) - '0';
            sum += d * (i % 2 == 0 ? 1 : 3);
        }
        int check = (10 - (sum % 10)) % 10;
        return core + check;
    }

    static String fromIsbn13(String isbn13) {
        if (isbn13 == null || isbn13.length() != 13 || !isbn13.startsWith("978")) return "";
        String core = isbn13.substring(3, 12);
        int sum = 0;
        for (int i = 0; i < 9; i++) sum += (core.charAt(i) - '0') * (10 - i);
        int check = (11 - (sum % 11)) % 11;
        return core + (check == 10 ? "X" : String.valueOf(check));
    }

    static Integer parseYear(String publishDate) {
        if (publishDate == null || publishDate.isBlank()) return null;
        String digitsOnly = publishDate.replaceAll("[^0-9]", " ").trim();
        if (digitsOnly.isEmpty()) return null;
        String first = digitsOnly.split("\\s+")[0];
        if (first.length() < 4) return null;
        try {
            int year = Integer.parseInt(first.substring(0, 4));
            return year >= 1000 && year <= 2100 ? year : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String joinNames(Iterable<String> names) {
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            if (out.length() > 0) out.append(", ");
            out.append(name.trim());
        }
        return out.length() == 0 ? null : out.toString();
    }

    static String normalizeStored(String raw) {
        String canonical = canonicalize(raw);
        if (!canonical.isEmpty()) return canonical;
        String cleaned = digits(raw);
        return cleaned.isEmpty() ? blankToNull(raw == null ? null : raw.trim()) : cleaned;
    }

    static String displaySource(String source) {
        return source == null ? "" : source.toLowerCase(Locale.ROOT);
    }
}
