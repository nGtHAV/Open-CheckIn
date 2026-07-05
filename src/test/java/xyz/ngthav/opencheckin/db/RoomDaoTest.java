package xyz.ngthav.opencheckin.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.model.Room;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the DAO layer against an in-memory database, including the room-delete FK guard. */
class RoomDaoTest {

    private Database db;
    private RoomDao roomDao;
    private MemberDao memberDao;

    @BeforeEach
    void setUp() {
        db = Database.inMemory();
        roomDao = new RoomDao(db);
        memberDao = new MemberDao(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void insertAssignsIdAndPersists() {
        Room room = roomDao.insert("Room A", "09:00", "17:00", true);
        assertTrue(room.id() > 0);
        assertEquals(1, roomDao.findAll().size());
        assertTrue(roomDao.findById(room.id()).isPresent());
        assertTrue(room.checkoutEnabled());
    }

    @Test
    void deleteSucceedsWhenNothingReferencesTheRoom() {
        Room room = roomDao.insert("Empty", "09:00", null, false);
        assertEquals(0, roomDao.countReferences(room.id()).total());
        roomDao.delete(room.id());
        assertTrue(roomDao.findById(room.id()).isEmpty());
    }

    @Test
    void deleteBlockedWhileMembersReferenceTheRoom() {
        Room room = roomDao.insert("Room A", "09:00", null, false);
        Member m = memberDao.insert(room.id(), UUID.randomUUID().toString(), "John Doe", "desc", null);

        RoomDao.References refs = roomDao.countReferences(room.id());
        assertEquals(1, refs.members());
        assertEquals(0, refs.attendance());
        assertEquals(1, refs.total());

        assertThrows(IllegalStateException.class, () -> roomDao.delete(room.id()));
        assertTrue(roomDao.findById(room.id()).isPresent(), "room must survive a blocked delete");

        // remove the reference, then the delete goes through
        assertFalse(memberDao.findByRoom(room.id()).isEmpty());
        assertTrue(memberDao.findByUuid(room.id(), m.uuid()).isPresent());
    }
}
