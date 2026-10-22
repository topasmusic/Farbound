package de.topas.farbound;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FarboundAssetTest {
    @Test void platformIconIsAtMost64PixelsWithRealTransparency() throws Exception {
        try(var input=getClass().getResourceAsStream("/assets/farbound/icon.png")) {
            assertNotNull(input); var image=ImageIO.read(input);
            assertEquals(64,image.getWidth()); assertEquals(64,image.getHeight());
            assertTrue(image.getColorModel().hasAlpha()); assertEquals(0,image.getRGB(0,0)>>>24);
            assertTrue((image.getRGB(16,32)>>>24)>0,"FB lettering must not be empty");
        }
    }
    @Test void titleIsACompactTransparentTextureNotABakedScreen() throws Exception {
        try(var input=getClass().getResourceAsStream("/assets/farbound/textures/gui/title.png")) {
            assertNotNull(input); var image=ImageIO.read(input);
            assertEquals(512,image.getWidth()); assertEquals(80,image.getHeight());
            assertTrue(image.getColorModel().hasAlpha()); assertEquals(0,image.getRGB(0,0)>>>24);
        }
    }
}
