package de.topas.farbound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FarboundConfigTest {
    @TempDir Path root;
    @Test void operatorApprovalAndInventoryBoundariesAreEnforced() throws Exception {
        var c = FarboundConfig.load(root.resolve("config.json")); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        assertTrue(c.mayStart(a)); c.allowedPlayers.add(a);
        assertTrue(c.mayStart(a)); assertFalse(c.mayStart(b)); c.enabled = false; assertFalse(c.mayStart(a));
        assertFalse(c.inventorySupported("STORE", Set.of("trinkets")));
        assertFalse(c.inventorySupported("EMPTY", Set.of("accessories")));
        assertTrue(c.inventorySupported("KEEP", Set.of("trinkets")));
    }
    @Test void invalidConfigIsPreservedAndRefused() throws Exception {
        Path p = root.resolve("config.json"); String invalid = "{\"maxMembers\":99}"; Files.writeString(p, invalid);
        assertThrows(java.io.IOException.class, () -> FarboundConfig.load(p)); assertEquals(invalid, Files.readString(p));
    }
    @Test void oldDefaultRadiusMigratesOnceWhileCustomRadiusAndUnknownSettingsSurvive() throws Exception {
        Path p=root.resolve("config.json");Files.writeString(p,"{\"homeRadius\":32,\"customNote\":\"preserve\"}");
        assertEquals(100,FarboundConfig.load(p).homeRadius);assertTrue(Files.readString(p).contains("preserve"));
        Files.writeString(p,"{\"homeRadius\":64}");assertEquals(64,FarboundConfig.load(p).homeRadius);
        Files.writeString(p,"{\"schema\":2,\"homeRadius\":32}");assertEquals(32,FarboundConfig.load(p).homeRadius);
    }
}
