package xyz.ngthav.opencheckin.qr;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The value encoded on a member's QR card: {@code {"roomId": 12, "memberUUID": "..."}}.
 * Kept deliberately minimal — the member is resolved by looking up
 * {@code memberUUID} <b>within</b> {@code roomId}.
 *
 * <p>{@link #parse(String)} is tolerant of surrounding whitespace and key order; anything that
 * does not carry both a numeric {@code roomId} and a string {@code memberUUID} yields
 * {@link Optional#empty()}. Round-trip ({@code parse(format())}) is unit-tested.
 */
public record QrPayload(long roomId, String memberUUID) {

    // Whitespace-tolerant, order-independent extraction. A full JSON parser is overkill for a
    // two-field object we produce ourselves.
    private static final Pattern ROOM_ID =
            Pattern.compile("\"roomId\"\\s*:\\s*(-?\\d+)");
    private static final Pattern MEMBER_UUID =
            Pattern.compile("\"memberUUID\"\\s*:\\s*\"([^\"]*)\"");

    /** Object → compact JSON string. */
    public String format() {
        return "{\"roomId\":" + roomId + ",\"memberUUID\":\"" + memberUUID + "\"}";
    }

    /** JSON string → payload, or empty when it does not parse / is missing a field. */
    public static Optional<QrPayload> parse(String json) {
        if (json == null) {
            return Optional.empty();
        }
        Matcher room = ROOM_ID.matcher(json);
        Matcher uuid = MEMBER_UUID.matcher(json);
        if (!room.find() || !uuid.find()) {
            return Optional.empty();
        }
        try {
            long roomId = Long.parseLong(room.group(1));
            String memberUUID = uuid.group(1);
            if (memberUUID.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new QrPayload(roomId, memberUUID));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
