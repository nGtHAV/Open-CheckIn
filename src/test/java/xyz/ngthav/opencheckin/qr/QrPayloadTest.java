package xyz.ngthav.opencheckin.qr;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrPayloadTest {

    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    void roundTrip() {
        QrPayload original = new QrPayload(12, UUID);
        Optional<QrPayload> parsed = QrPayload.parse(original.format());
        assertTrue(parsed.isPresent());
        assertEquals(original, parsed.get());
    }

    @Test
    void formatIsCompactJson() {
        assertEquals("{\"roomId\":12,\"memberUUID\":\"" + UUID + "\"}",
                new QrPayload(12, UUID).format());
    }

    @Test
    void toleratesWhitespaceAndKeyOrder() {
        String json = "  {  \"memberUUID\" : \"" + UUID + "\" ,  \"roomId\"  :  7  }  ";
        Optional<QrPayload> parsed = QrPayload.parse(json);
        assertTrue(parsed.isPresent());
        assertEquals(7, parsed.get().roomId());
        assertEquals(UUID, parsed.get().memberUUID());
    }

    @Test
    void rejectsMalformed() {
        assertFalse(QrPayload.parse(null).isPresent());
        assertFalse(QrPayload.parse("").isPresent());
        assertFalse(QrPayload.parse("not json").isPresent());
        assertFalse(QrPayload.parse("{}").isPresent());
        assertFalse(QrPayload.parse("{\"roomId\":12}").isPresent());       // no uuid
        assertFalse(QrPayload.parse("{\"memberUUID\":\"" + UUID + "\"}").isPresent()); // no roomId
        assertFalse(QrPayload.parse("{\"roomId\":12,\"memberUUID\":\"\"}").isPresent()); // blank uuid
    }
}
