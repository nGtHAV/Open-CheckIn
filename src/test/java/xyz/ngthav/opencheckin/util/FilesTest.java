package xyz.ngthav.opencheckin.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilesTest {

    @Test
    void stripsSpaces() {
        assertEquals("JohnDoe", Files.sanitizeFilename("John Doe"));
        assertEquals("JaneSmith", Files.sanitizeFilename("  Jane   Smith "));
    }

    @Test
    void dropsUnsafeCharactersKeepsDotDashUnderscore() {
        assertEquals("abcde", Files.sanitizeFilename("a/b\\c:d*e"));
        assertEquals("foo_bar-1.2", Files.sanitizeFilename("foo_bar-1.2"));
    }

    @Test
    void fallsBackWhenNothingSurvives() {
        assertEquals("member", Files.sanitizeFilename("!!!"));
        assertEquals("member", Files.sanitizeFilename(""));
        assertEquals("member", Files.sanitizeFilename(null));
        assertEquals("member", Files.sanitizeFilename("файл")); // all non-ASCII stripped
    }

    @Test
    void pictureFileNameFormat() {
        LocalDate date = LocalDate.of(2026, 7, 5);
        assertEquals("20260705-JohnDoe.png", Files.pictureFileName(date, "John Doe"));
    }

    @Test
    void pictureFileNameWithDisambiguator() {
        LocalDate date = LocalDate.of(2026, 7, 5);
        assertEquals("20260705-JohnDoe-ab12.png", Files.pictureFileName(date, "John Doe", "ab12"));
        // a blank disambiguator is ignored
        assertEquals("20260705-JohnDoe.png", Files.pictureFileName(date, "John Doe", "  "));
    }
}
