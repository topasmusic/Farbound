package de.topas.farbound.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class FarboundTextureTest {
    @Test void fullTitleUsesItsOwnPixelDimensionsAndDestinationSize() {
        var g=mock(GuiGraphicsExtractor.class);
        FarboundTheme.texture(g,FarboundTheme.TITLE,380,13,280,44,0,0,512,80,512,80);
        verify(g).blit(RenderPipelines.GUI_TEXTURED,FarboundTheme.TITLE,380,13,0f,0f,280,44,512,80,512,80);
        verifyNoMoreInteractions(g);
    }
    @Test void faceAndHatStayThirtyPixelsWideAndSampleSeparateEightPixelRegions() {
        var g=mock(GuiGraphicsExtractor.class);var skin=Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
        FarboundTheme.texture(g,skin,666,212,30,30,8,8,8,8,64,64);
        FarboundTheme.texture(g,skin,666,212,30,30,40,8,8,8,64,64);
        verify(g).blit(RenderPipelines.GUI_TEXTURED,skin,666,212,8f,8f,30,30,8,8,64,64);
        verify(g).blit(RenderPipelines.GUI_TEXTURED,skin,666,212,40f,8f,30,30,8,8,64,64);
        verifyNoMoreInteractions(g);
    }
}
