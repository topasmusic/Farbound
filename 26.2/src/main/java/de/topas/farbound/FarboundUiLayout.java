package de.topas.farbound;

/** Coordinates measured from the approved 1040x780 panel, independent of GUI scale. */
public record FarboundUiLayout(double left, double top, double scale) {
    public static final int WIDTH = 1040, HEIGHT = 780;
    public static FarboundUiLayout fit(int screenWidth, int screenHeight) {
        double s = Math.max(.01, Math.min(Math.min(420.0 / WIDTH,
            Math.min(screenWidth * .64 / WIDTH, screenHeight * .84 / HEIGHT)),
            Math.min((screenWidth - 24.0) / WIDTH, (screenHeight - 24.0) / HEIGHT)));
        return new FarboundUiLayout((screenWidth - WIDTH * s) / 2, (screenHeight - HEIGHT * s) / 2, s);
    }
    public double x(double screenX) { return (screenX - left) / scale; }
    public double y(double screenY) { return (screenY - top) / scale; }
    public double screenX(double x) { return left + x * scale; }
    public double screenY(double y) { return top + y * scale; }
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
}

