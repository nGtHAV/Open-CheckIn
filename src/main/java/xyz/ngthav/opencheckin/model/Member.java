package xyz.ngthav.opencheckin.model;

import java.time.LocalDateTime;

/**
 * A person who belongs to exactly one {@link Room}. Identified on a QR card by {@code uuid}
 * (resolved within a room).
 *
 * <p>{@code pictureName} is the <b>filename only</b> (e.g. {@code "20260705-JohnDoe.png"});
 * the full path is rebuilt from the room folder. {@code id == 0} denotes a
 * member that has not been persisted yet.
 */
public record Member(
        long id,
        String uuid,
        String name,
        String description,
        String pictureName,
        long roomId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean hasPicture() {
        return pictureName != null && !pictureName.isBlank();
    }
}
