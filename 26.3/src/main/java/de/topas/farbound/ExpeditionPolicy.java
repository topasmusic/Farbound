package de.topas.farbound;

public final class ExpeditionPolicy {
    private ExpeditionPolicy() {}
    public static boolean dimensionAllowed(boolean member, boolean netherBlocked, boolean endBlocked, String dimension) {
        return !member || !(netherBlocked && dimension.equals("minecraft:the_nether") || endBlocked && dimension.equals("minecraft:the_end"));
    }
    public static boolean canRestore(boolean inventorySaved, boolean enderSaved, boolean inventoryEmpty, boolean enderEmpty) {
        return (!inventorySaved || inventoryEmpty) && (!enderSaved || enderEmpty);
    }
    public static String reconcile(String journal, String player) {
        if (!java.util.Set.of("HOME", "PREPARED", "ACTIVE", "RESTORING", "FINISHED", "PAUSED", "PAUSING", "RESUMING", "ABANDONING").contains(journal)) return "RECOVERY_REQUIRED";
        if (journal.equals(player)) return journal;
        if(journal.equals("ABANDONING")&&java.util.Set.of("PAUSED","FINISHED").contains(player))return journal;
        if(journal.equals("PAUSING")&&java.util.Set.of("ACTIVE","PAUSING","PAUSED").contains(player))return journal;
        if(journal.equals("RESUMING")&&java.util.Set.of("PAUSED","RESUMING","ACTIVE").contains(player))return journal;
        if (journal.equals("RESTORING") && player.equals("ACTIVE")) return "RESTORING";
        if (journal.equals("RESTORING") && player.equals("FINISHED")) return "FINISHED";
        if (journal.equals("PREPARED") && player.equals("ACTIVE")) return "ACTIVE";
        if (journal.equals("PREPARED") && player.equals("HOME")) return "HOME";
        return "RECOVERY_REQUIRED";
    }
    public static boolean validTarget(double ox, double oz, double x, double z, double distance, double minX, double maxX, double minZ, double maxZ, double margin) {
        double d = Math.hypot(x - ox, z - oz);
        return Double.isFinite(d) && distance >= 1 && d >= distance && d <= distance * 1.1
            && x > minX + margin && x < maxX - margin && z > minZ + margin && z < maxZ - margin;
    }
    public static boolean acceptRevision(int supplied, int current) { return supplied == current; }
}

