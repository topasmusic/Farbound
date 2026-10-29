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
    public static int[] coordinates(String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 2) throw new IllegalArgumentException("Enter two whole Overworld coordinates: X Z. Height is selected automatically.");
        int[] result = new int[2];
        try {
            for (int i = 0; i < 2; i++) {
                if (!parts[i].matches("[+-]?[0-9]{1,8}")) throw new NumberFormatException();
                result[i] = Integer.parseInt(parts[i]);
                if (Math.abs((long) result[i]) > 29999984) throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("X and Z must be whole numbers between -29999984 and 29999984."); }
        return result;
    }
    public static boolean withinCustomRadius(int targetX, int targetZ, int x, int z) {
        return Math.hypot((double)x - targetX, (double)z - targetZ) <= 100;
    }
}

