package de.topas.farbound;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionPolicyTest {
    @Test void outsidersAreNeverRestricted() {
        assertTrue(ExpeditionPolicy.dimensionAllowed(false, true, true, "minecraft:the_nether"));
    }
    @Test void memberNetherRestrictionDoesNotBlockOverworld() {
        assertFalse(ExpeditionPolicy.dimensionAllowed(true, true, false, "minecraft:the_nether"));
        assertTrue(ExpeditionPolicy.dimensionAllowed(true, true, false, "minecraft:overworld"));
    }
    @Test void endAndNetherAreIndependent() {
        assertFalse(ExpeditionPolicy.dimensionAllowed(true, false, true, "minecraft:the_end"));
        assertTrue(ExpeditionPolicy.dimensionAllowed(true, false, true, "minecraft:the_nether"));
    }
    @Test void lootBlocksOnlyProfilesThatWillBeReplaced() {
        assertFalse(ExpeditionPolicy.canRestore(true, false, false, true));
        assertFalse(ExpeditionPolicy.canRestore(false, true, true, false));
        assertTrue(ExpeditionPolicy.canRestore(false, false, false, false));
        assertTrue(ExpeditionPolicy.canRestore(true, true, true, true));
    }
    @Test void journalAndPlayerMarkerMustAgree() {
        assertEquals("ACTIVE", ExpeditionPolicy.reconcile("ACTIVE", "ACTIVE"));
        assertEquals("FINISHED", ExpeditionPolicy.reconcile("RESTORING", "FINISHED"));
        assertEquals("RECOVERY_REQUIRED", ExpeditionPolicy.reconcile("ACTIVE", "HOME"));
        assertEquals("HOME", ExpeditionPolicy.reconcile("PREPARED", "HOME"));
    }
    @Test void annulusAndBorderAreBothRequired() {
        assertTrue(ExpeditionPolicy.validTarget(0, 0, 10000, 0, 10000, -20000, 20000, -20000, 20000, 16));
        assertFalse(ExpeditionPolicy.validTarget(0, 0, 20000, 0, 10000, -20000, 20000, -20000, 20000, 16));
        assertFalse(ExpeditionPolicy.validTarget(0, 0, 10000, 0, 10000, -10000, 10000, -10000, 10000, 16));
    }
    @Test void staleUiRevisionCannotChangeRules() {
        assertTrue(ExpeditionPolicy.acceptRevision(4, 4));
        assertFalse(ExpeditionPolicy.acceptRevision(3, 4));
    }
    @Test void interruptedReturnWithActiveProfileCanResumeWithoutGivingTwice() {
        assertEquals("RESTORING", ExpeditionPolicy.reconcile("RESTORING", "ACTIVE"));
        assertEquals("FINISHED", ExpeditionPolicy.reconcile("RESTORING", "FINISHED"));
        assertEquals("RECOVERY_REQUIRED", ExpeditionPolicy.reconcile("FINISHED", "ACTIVE"));
    }
    @Test void unknownEqualMarkersMustNotAuthorizeAccess() {
        assertEquals("RECOVERY_REQUIRED", ExpeditionPolicy.reconcile("garbage", "garbage"));
    }
}
