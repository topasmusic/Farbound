package de.topas.farbound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DepartureCountdownTest {
    @Test void serverDigitsProduceExactlyOneTickEachAndZeroOnlyAfterSuccessfulTransfer() {
        var c = new DepartureCountdown(); long time = 1000;
        for (int n = 10; n >= 1; n--) {
            assertEquals(DepartureCountdown.Cue.TICK,c.update("COUNTDOWN","HOME",n,time));
            assertEquals(n,c.number(time+999));
            assertEquals(DepartureCountdown.Cue.NONE,c.update("COUNTDOWN","HOME",n,time+50));
            time += 1000;
        }
        assertEquals(DepartureCountdown.Cue.ARRIVAL,c.update("ACTIVE","ACTIVE",0,time));
        assertEquals(0,c.number(time+699)); assertEquals(-1,c.number(time+700));
        assertEquals(DepartureCountdown.Cue.NONE,c.update("ACTIVE","ACTIVE",0,time+100));
    }
    @Test void cancelDisconnectAndUnrelatedPlayersHaveNoArrivalOrStaleOverlay() {
        var c = new DepartureCountdown();
        c.update("COUNTDOWN","HOME",5,1000);
        assertEquals(DepartureCountdown.Cue.NONE,c.update("DRAFT","HOME",0,1200));
        assertEquals(-1,c.number(1200));
        assertEquals(DepartureCountdown.Cue.NONE,c.update("ACTIVE","ACTIVE",0,1300));
        c.update("COUNTDOWN","HOME",3,1400); c.reset(); assertEquals(-1,c.number(1400));
        assertEquals(DepartureCountdown.Cue.NONE,c.update("ACTIVE","HOME",0,1500));
    }
    @Test void lagNeverAdvancesTheServerCountdownLocally() {
        var c = new DepartureCountdown(); c.update("COUNTDOWN","HOME",4,1000);
        assertEquals(4,c.number(30000)); assertEquals(0,c.pulse(30000));
    }
    @Test void zeroDelayDoesNotPretendACompletedTeleport() {
        var c = new DepartureCountdown();
        assertEquals(DepartureCountdown.Cue.NONE,c.update("COUNTDOWN","HOME",0,1000));
        assertEquals(-1,c.number(1000));
        assertEquals(DepartureCountdown.Cue.NONE,c.update("ACTIVE","ACTIVE",0,1050));
    }
}
