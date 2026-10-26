package ph.edu.vfgnhs.library;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IsbnSupportTest {
    @Test
    void canonicalizesIsbn10ToIsbn13() {
        assertEquals("9780306406157", IsbnSupport.canonicalize("0-306-40615-2"));
        assertEquals("9780306406157", IsbnSupport.canonicalize("0306406152"));
    }

    @Test
    void keepsValidIsbn13() {
        assertEquals("9780140328721", IsbnSupport.canonicalize("978-0-14-032872-1"));
        assertTrue(IsbnSupport.isValidIsbn13("9780140328721"));
    }

    @Test
    void rejectsInvalidLookupKeys() {
        assertFalse(IsbnSupport.isValidLookupKey("123"));
        assertFalse(IsbnSupport.isValidLookupKey(""));
        assertTrue(IsbnSupport.isValidLookupKey("0306406152"));
        assertTrue(IsbnSupport.isValidLookupKey("9780140328721"));
    }

    @Test
    void matchKeysIncludeIsbn10And13() {
        assertTrue(IsbnSupport.matchKeys("9780306406157").contains("0306406152"));
        assertTrue(IsbnSupport.matchKeys("0-306-40615-2").contains("9780306406157"));
    }

    @Test
    void parseYearFromPublishDate() {
        assertEquals(1988, IsbnSupport.parseYear("1988"));
        assertEquals(2008, IsbnSupport.parseYear("2008-06-01"));
        assertNull(IsbnSupport.parseYear("no-year"));
    }
}
