package de.topas.farbound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FarboundUiLayoutTest {
    @Test void entirePanelFitsAtCommonGuiScalesAndInputHitsTheSameControl() {
        for (int[] size : new int[][]{{320,240},{427,240},{640,360},{960,540},{1920,1080}}) {
            var l = FarboundUiLayout.fit(size[0],size[1]);
            assertTrue(l.left() >= 11); assertTrue(l.top() >= 11);
            assertTrue(l.screenX(1040) <= size[0]-11); assertTrue(l.screenY(780) <= size[1]-11);
            var start = new FarboundUiLayout.Rect(764,716,250,46);
            assertTrue(start.contains(l.x(l.screenX(800)),l.y(l.screenY(740))));
            assertFalse(start.contains(l.x(l.screenX(600)),l.y(l.screenY(740))));
        }
    }
    @Test void defaultWindowWidthIsSeventyPercentOfPreviousMaximum() {
        var l = FarboundUiLayout.fit(960,540);
        assertEquals(420,l.screenX(1040)-l.left(),.001);
        assertEquals(315,l.screenY(780)-l.top(),.001);
    }
    @Test void highGuiScaleNeverExpandsTheDashboardToFillTheScreen() {
        var l=FarboundUiLayout.fit(464,325);
        assertTrue(l.screenX(1040)-l.left() <= 464*.64+.001);
        assertTrue(l.screenY(780)-l.top() <= 325*.84+.001);
        assertTrue(l.left() >= 464*.17);
    }
}
