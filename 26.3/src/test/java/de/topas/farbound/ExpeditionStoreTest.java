package de.topas.farbound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionStoreTest {
    @TempDir Path dir;
    @Test void persistedMembershipAndTimerSurviveReload() throws Exception {
        var store = new ExpeditionStore(dir);
        var e = new Expedition(UUID.randomUUID());
        e.elapsedTicks = 1200;
        store.expedition = e;
        store.save();
        var loaded = new ExpeditionStore(dir);
        assertEquals(e.id, loaded.expedition.id);
        assertTrue(loaded.expedition.members.containsKey(e.leader));
        assertEquals(1200, loaded.expedition.elapsedTicks);
    }
    @Test void corruptStateNeverBecomesEmptyWorld() throws Exception {
        Files.writeString(dir.resolve("expedition.json"), "{broken");
        assertThrows(Exception.class, () -> new ExpeditionStore(dir));
    }
    @Test void invalidSchemaIsRefused() throws Exception {
        Files.writeString(dir.resolve("expedition.json"), "{\"schema\":999}");
        assertThrows(Exception.class, () -> new ExpeditionStore(dir));
    }
    @Test void changesInvalidateReadinessAndStaleConfirmation() {
        var e = new Expedition(UUID.randomUUID());
        e.members.get(e.leader).ready = true;
        int before = e.revision;
        e.changed();
        assertFalse(e.members.get(e.leader).ready);
        assertFalse(ExpeditionPolicy.acceptRevision(before, e.revision));
    }
    @Test void dimensionsNeedAllVotesAndSafeReturnBeforeBlocking() {
        var e = new Expedition(UUID.randomUUID());
        var friend = UUID.randomUUID();
        e.members.put(friend, new Expedition.Member());
        e.propose(true, true);
        e.vote(e.leader);
        assertFalse(e.applyProposal(false));
        e.vote(friend);
        assertFalse(e.applyProposal(true));
        assertTrue(e.applyProposal(false));
        assertTrue(e.netherBlocked);
    }
}
