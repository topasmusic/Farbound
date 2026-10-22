package de.topas.farbound;

/** Only server updates change the digit. Wall time animates it, never advances the teleport. */
public final class DepartureCountdown {
    public enum Cue { NONE, TICK, ARRIVAL }
    private boolean counting;
    private int number = -1;
    private long changedAt, zeroUntil;
    public Cue update(String status, String phase, int remaining, long now) {
        if (status.equals("COUNTDOWN")) {
            if (remaining <= 0) { reset(); return Cue.NONE; }
            zeroUntil = 0;
            boolean changed = !counting || number != Math.max(0, remaining);
            counting = true; number = Math.max(0, remaining);
            if (changed) { changedAt = now; return Cue.TICK; }
            return Cue.NONE;
        }
        if (counting && status.equals("ACTIVE") && phase.equals("ACTIVE")) {
            counting = false; number = 0; changedAt = now; zeroUntil = now + 700;
            return Cue.ARRIVAL;
        }
        counting = false;
        if (!status.equals("ACTIVE") || !phase.equals("ACTIVE") || now >= zeroUntil) { number = -1; zeroUntil = 0; }
        return Cue.NONE;
    }
    public int number(long now) { return counting || now < zeroUntil ? number : -1; }
    public float pulse(long now) { return Math.max(0, 1 - Math.max(0, now - changedAt) / 280f); }
    public void reset() { counting = false; number = -1; zeroUntil = 0; }
}
