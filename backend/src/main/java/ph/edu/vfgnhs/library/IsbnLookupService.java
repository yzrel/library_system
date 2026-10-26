package ph.edu.vfgnhs.library;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
class IsbnLookupService {
    private static final Logger log = LoggerFactory.getLogger(IsbnLookupService.class);

    private final BookRepository books;
    private final BookCopyRepository copies;
    private final ApiSupport api;
    private final RestClient http;

    IsbnLookupService(BookRepository books, BookCopyRepository copies, ApiSupport api, RestClient.Builder restClientBuilder) {
        this.books = books;
        this.copies = copies;
        this.api = api;
        this.http = restClientBuilder.build();
    }

    Map<String, Object> lookup(String rawIsbn) {
        if (!IsbnSupport.isValidLookupKey(rawIsbn)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Enter a valid 10- or 13-digit ISBN barcode");
        }
        String canonical = IsbnSupport.canonicalize(rawIsbn);
        String isbn = canonical.isEmpty() ? IsbnSupport.digits(rawIsbn) : canonical;

        Optional<Book> local = findLocal(rawIsbn);
        if (local.isPresent()) {
            Book book = local.get();
            Map<String, Object> result = emptyResult(isbn, "LOCAL");
            result.put("found", true);
            result.put("existingBookId", book.id);
            result.put("title", book.title);
            result.put("subtitle", book.subtitle);
            result.put("author", book.author);
            result.put("isbn", IsbnSupport.normalizeStored(book.isbn) != null
                    ? IsbnSupport.normalizeStored(book.isbn) : isbn);
            result.put("publisher", book.publisher);
            result.put("publicationYear", book.publicationYear);
            result.put("edition", book.edition);
            result.put("subject", book.subject);
            result.put("description", book.description);
            result.put("coverUrl", book.coverUrl);
            result.put("existingBook", api.book(book, copies.findByBookId(book.id)));
            return result;
        }

        Optional<Map<String, Object>> openLibrary = fromOpenLibrary(isbn);
        if (openLibrary.isPresent()) return openLibrary.get();

        Optional<Map<String, Object>> google = fromGoogleBooks(isbn);
        if (google.isPresent()) return google.get();

        Map<String, Object> none = emptyResult(isbn, "NONE");
        none.put("found", false);
        return none;
    }

    Optional<Book> findLocal(String rawIsbn) {
        for (String key : IsbnSupport.matchKeys(rawIsbn)) {
            List<Book> matches = books.findByNormalizedIsbn(key);
            if (!matches.isEmpty()) return Optional.of(matches.get(0));
        }
        return Optional.empty();
    }

    private Optional<Map<String, Object>> fromOpenLibrary(String isbn) {
        try {
            String url = "https://openlibrary.org/api/books?bibkeys=ISBN:" + isbn + "&format=json&jscmd=data";
            JsonNode root = http.get().uri(url).retrieve().body(JsonNode.class);
            if (root == null || root.isEmpty()) return Optional.empty();
            JsonNode book = root.get("ISBN:" + isbn);
            if (book == null || book.isNull()) {
                var fields = root.fields();
                if (!fields.hasNext()) return Optional.empty();
                book = fields.next().getValue();
            }
            if (book == null || book.isNull()) return Optional.empty();

            Map<String, Object> result = emptyResult(isbn, "OPEN_LIBRARY");
            result.put("found", true);
            result.put("title", text(book, "title"));
            result.put("subtitle", text(book, "subtitle"));
            result.put("author", joinNamed(book.get("authors")));
            result.put("publisher", joinNamed(book.get("publishers")));
            result.put("publicationYear", IsbnSupport.parseYear(text(book, "publish_date")));
            result.put("subject", firstNamed(book.get("subjects")));
            result.put("description", excerpt(book));
            result.put("coverUrl", cover(book));
            if (result.get("title") == null || result.get("title").toString().isBlank()) return Optional.empty();
            return Optional.of(result);
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Open Library lookup failed for ISBN {}: {}", isbn, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Map<String, Object>> fromGoogleBooks(String isbn) {
        try {
            String url = "https://www.googleapis.com/books/v1/volumes?q=isbn:" + isbn;
            JsonNode root = http.get().uri(url).retrieve().body(JsonNode.class);
            if (root == null) return Optional.empty();
            JsonNode items = root.get("items");
            if (items == null || !items.isArray() || items.isEmpty()) return Optional.empty();
            JsonNode info = items.get(0).path("volumeInfo");
            if (info.isMissingNode()) return Optional.empty();

            Map<String, Object> result = emptyResult(isbn, "GOOGLE_BOOKS");
            result.put("found", true);
            result.put("title", text(info, "title"));
            result.put("subtitle", text(info, "subtitle"));
            result.put("author", joinArray(info.get("authors")));
            result.put("publisher", text(info, "publisher"));
            result.put("publicationYear", IsbnSupport.parseYear(text(info, "publishedDate")));
            result.put("description", text(info, "description"));
            result.put("subject", firstArray(info.get("categories")));
            JsonNode image = info.path("imageLinks").path("thumbnail");
            if (image.isTextual()) {
                String cover = image.asText().replace("http://", "https://");
                result.put("coverUrl", cover);
            }
            if (result.get("title") == null || result.get("title").toString().isBlank()) return Optional.empty();
            return Optional.of(result);
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Google Books lookup failed for ISBN {}: {}", isbn, ex.getMessage());
            return Optional.empty();
        }
    }

    private static Map<String, Object> emptyResult(String isbn, String source) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("isbn", isbn);
        m.put("source", source);
        m.put("found", false);
        m.put("existingBookId", null);
        m.put("title", null);
        m.put("subtitle", null);
        m.put("author", null);
        m.put("publisher", null);
        m.put("publicationYear", null);
        m.put("edition", null);
        m.put("subject", null);
        m.put("description", null);
        m.put("coverUrl", null);
        return m;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    private static String joinNamed(JsonNode array) {
        if (array == null || !array.isArray()) return null;
        List<String> names = new ArrayList<>();
        array.forEach(n -> {
            JsonNode name = n.get("name");
            if (name != null && name.isTextual()) names.add(name.asText());
        });
        return IsbnSupport.joinNames(names);
    }

    private static String firstNamed(JsonNode array) {
        if (array == null || !array.isArray() || array.isEmpty()) return null;
        JsonNode name = array.get(0).get("name");
        return name != null && name.isTextual() ? name.asText().trim() : null;
    }

    private static String joinArray(JsonNode array) {
        if (array == null || !array.isArray()) return null;
        List<String> names = new ArrayList<>();
        array.forEach(n -> {
            if (n.isTextual()) names.add(n.asText());
        });
        return IsbnSupport.joinNames(names);
    }

    private static String firstArray(JsonNode array) {
        if (array == null || !array.isArray() || array.isEmpty()) return null;
        JsonNode first = array.get(0);
        return first != null && first.isTextual() ? first.asText().trim() : null;
    }

    private static String cover(JsonNode book) {
        JsonNode cover = book.get("cover");
        if (cover == null) return null;
        for (String key : List.of("large", "medium", "small")) {
            JsonNode url = cover.get(key);
            if (url != null && url.isTextual() && !url.asText().isBlank()) return url.asText().trim();
        }
        return null;
    }

    private static String excerpt(JsonNode book) {
        JsonNode excerpts = book.get("excerpts");
        if (excerpts != null && excerpts.isArray() && !excerpts.isEmpty()) {
            JsonNode text = excerpts.get(0).get("text");
            if (text != null && text.isTextual() && !text.asText().isBlank()) {
                String value = text.asText().trim();
                return value.length() > 2000 ? value.substring(0, 2000) : value;
            }
        }
        JsonNode notes = book.get("notes");
        if (notes != null && notes.isTextual() && !notes.asText().isBlank()) {
            String value = notes.asText().trim();
            return value.length() > 2000 ? value.substring(0, 2000) : value;
        }
        return null;
    }
}
