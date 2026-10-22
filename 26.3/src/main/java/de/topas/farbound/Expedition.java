package de.topas.farbound;

import java.util.*;

public final class Expedition {
    public UUID id = UUID.randomUUID();
    public UUID leader;
    public String status = "DRAFT";
    public int revision = 1;
    public int distance = 10000;
    public boolean worldSpawn, homeward, netherBlocked, endBlocked;
    public long elapsedTicks;
    public int countdown = 10;
    public double originX, originZ;
    public Position target;
    public Map<UUID, Member> members = new LinkedHashMap<>();
    public Map<UUID, Long> invitations = new LinkedHashMap<>();
    public Boolean proposedNether, proposedEnd;
    public Set<UUID> votes = new HashSet<>();
    public boolean rulesChanged;
    public Expedition(UUID leader) { this.leader = leader; members.put(leader, new Member()); }
    public boolean untouchedPreparation() {
        return Set.of("DRAFT","SEARCHING","COUNTDOWN").contains(status) && !members.isEmpty()
            && members.values().stream().allMatch(m -> m.phase.equals("HOME") && m.home==null && m.nonce==null);
    }
    public void changed() {
        revision++;
        members.values().forEach(m -> m.ready = false);
        votes.clear();
    }
    public void propose(boolean nether, boolean end) {
        proposedNether = nether;
        proposedEnd = end;
        revision++;
        votes.clear();
    }
    public void vote(UUID player) {
        if (!members.containsKey(player) || proposedNether == null) throw new IllegalStateException("No valid rule proposal.");
        votes.add(player);
    }
    public boolean applyProposal(boolean occupiedForbiddenDimension) {
        if (proposedNether == null || occupiedForbiddenDimension) return false;
        if (members.entrySet().stream().anyMatch(entry -> !entry.getValue().phase.equals("FINISHED") && !votes.contains(entry.getKey()))) return false;
        netherBlocked = proposedNether;
        endBlocked = proposedEnd;
        proposedNether = proposedEnd = null;
        votes.clear();
        rulesChanged = true;
        revision++;
        return true;
    }
    public static class Member {
        public String phase = "HOME";
        public String inventory = "STORE";
        public boolean enderStore = true, xpStore = true, ready;
        public Position home;
        public Position pausedAt, homeReturn;
        public String nonce;
        public long finishedAt;
    }
    public record Position(String dimension, double x, double y, double z, float yaw, float pitch) {}
}

