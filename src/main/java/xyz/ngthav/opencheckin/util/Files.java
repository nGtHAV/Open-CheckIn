package xyz.ngthav.opencheckin.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Filesystem paths and safe filename generation.
 *
 * <p>Layout, relative to the working directory (created on first run):
 * <pre>
 *   data/open-checkin.db
 *   data/pictures/&lt;roomId&gt;/yyyyMMdd-&lt;memberName&gt;.png
 * </pre>
 */
public final class Files {

    private Files() {
    }

    // ----- well-known locations -----

    public static Path dataDir() {
        return Path.of("data");
    }

    public static Path dbPath() {
        return dataDir().resolve("open-checkin.db");
    }

    public static Path picturesDir() {
        return dataDir().resolve("pictures");
    }

    /** One folder per room, keyed by the stable room id (survives renames). */
    public static Path roomPicturesDir(long roomId) {
        return picturesDir().resolve(Long.toString(roomId));
    }

    /** Full path to a member's picture, rebuilt from room + filename. */
    public static Path picturePath(long roomId, String pictureName) {
        return roomPicturesDir(roomId).resolve(pictureName);
    }

    // ----- filename building -----

    /**
     * Strips a member name down to a filesystem-safe token: keeps letters, digits, {@code . _ -}
     * and drops everything else (spaces included, so {@code "John Doe"} → {@code "JohnDoe"}).
     * Falls back to {@code "member"} when nothing survives (e.g. a name of only symbols).
     *
     * <p>Pure and side-effect free — unit-tested.
     */
    public static String sanitizeFilename(String name) {
        if (name == null) {
            return "member";
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        String cleaned = sb.toString();
        return cleaned.isEmpty() ? "member" : cleaned;
    }

    /**
     * Builds a picture filename {@code yyyyMMdd-<sanitizedName>.png}. When {@code disambiguator}
     * is non-blank (two members share a name on the same day) it is appended as a
     * short suffix so the earlier file is not clobbered.
     */
    public static String pictureFileName(LocalDate date, String memberName, String disambiguator) {
        String base = date.format(Dates.FILE_DATE) + "-" + sanitizeFilename(memberName);
        if (disambiguator != null && !disambiguator.isBlank()) {
            base = base + "-" + sanitizeFilename(disambiguator);
        }
        return base + ".png";
    }

    public static String pictureFileName(LocalDate date, String memberName) {
        return pictureFileName(date, memberName, null);
    }

    // ----- directory helpers -----

    public static void ensureDir(Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create directory: " + dir, e);
        }
    }
}
