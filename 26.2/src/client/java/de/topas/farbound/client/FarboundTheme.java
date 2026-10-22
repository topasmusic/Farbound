package de.topas.farbound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Resolution-independent panel artwork; no baked text or screenshot background. */
final class FarboundTheme {
    static final int TEXT = 0xFFE0F5FF, MUTED = 0xFF95B8CE, CYAN = 0xFF68D6FF;
    static final Identifier TITLE = Identifier.fromNamespaceAndPath("farbound", "textures/gui/title.png");
    private FarboundTheme() {}
    /** Explicit destination sizes and source pixel rectangle; avoids the corner/UV overload. */
    static void texture(GuiGraphicsExtractor g, Identifier image, int x, int y, int w, int h,
            int u, int v, int regionW, int regionH, int textureW, int textureH) {
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, image, x, y,
            (float)u, (float)v, w, h, regionW, regionH, textureW, textureH);
    }
    static void text(GuiGraphicsExtractor g, String value, int x, int y, float size, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y); g.pose().scale(size / 9f);
        g.text(Minecraft.getInstance().font, value, 0, 0, color, false);
        g.pose().popMatrix();
    }
    static int textWidth(String value, float size) { return Math.round(Minecraft.getInstance().font.width(value) * size / 9f); }
    static void centered(GuiGraphicsExtractor g, String value, int x, int y, float size, int color) {
        text(g, value, x - textWidth(value, size) / 2, y, size, color);
    }
    static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        if (y0 == y1) { g.fill(Math.min(x0,x1),y0,Math.max(x0,x1)+1,y0+1,color); return; }
        if (x0 == x1) { g.fill(x0,Math.min(y0,y1),x0+1,Math.max(y0,y1)+1,color); return; }
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (n == 0) return;
        for (int i = 0; i <= n; i++) { int x = x0 + (x1 - x0) * i / n, y = y0 + (y1 - y0) * i / n; g.fill(x, y, x + 1, y + 1, color); }
    }
    static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int edge, int cut) {
        g.fill(x,y+cut,x+w,y+h-cut,fill);
        for (int row = 0; row < cut; row++) {
            int inset = cut-row;
            g.fill(x+inset,y+row,x+w-inset,y+row+1,fill);
            g.fill(x+inset,y+h-row-1,x+w-inset,y+h-row,fill);
        }
        line(g,x+cut,y,x+w-cut,y,edge); line(g,x+cut,y+h-1,x+w-cut,y+h-1,edge);
        line(g,x,y+cut,x,y+h-cut-1,edge); line(g,x+w-1,y+cut,x+w-1,y+h-cut-1,edge);
        line(g,x,y+cut,x+cut,y,edge); line(g,x+w-cut-1,y,x+w-1,y+cut,edge);
        line(g,x,y+h-cut-1,x+cut,y+h-1,edge); line(g,x+w-cut-1,y+h-1,x+w-1,y+h-cut-1,edge);
    }
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, String title) {
        frame(g,x,y,w,h,0x40041522,0xB03C7F9D,6);
        g.fill(x+7,y+1,x+w-7,y+45,0x300C2638);
        g.horizontalLine(x+1,x+w-2,y+46,0x604784A1);
        text(g,title,x+26,y+17,21,CYAN);
    }
    static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for(int dy=-r;dy<=r;dy++){int dx=(int)Math.sqrt(r*r-dy*dy);g.fill(cx-dx,cy+dy,cx+dx+1,cy+dy+1,color);}
    }
    static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on, boolean green, boolean enabled) {
        int c = !enabled ? 0xFF334E60 : on ? green ? 0xFF319E72 : 0xFF218EC4 : 0xFF2B4251;
        circle(g,x+12,y+12,12,c);circle(g,x+34,y+12,12,c);g.fill(x+12,y,x+35,y+25,c);
        int thumb = on ? x+34 : x+12;
        circle(g,thumb,y+12,9, enabled ? 0xFFE1F8FF : 0xFF7995A7);
        circle(g,thumb,y+12,6,on ? green ? 0xFF83E6BD : 0xFF88D9FF : 0xFF8FA9BA);
    }
    static void chevron(GuiGraphicsExtractor g, int x, int y, int color) { line(g,x,y,x+7,y+7,color);line(g,x+7,y+7,x+14,y,color); }
}
