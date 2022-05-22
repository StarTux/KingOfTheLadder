package com.cavetale.kotl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Data;

@Data
public final class Game {
    protected State state;
    protected String world;
    protected Rect area;
    protected Rect goal;
    protected Set<Vec> spawnBlocks;
    protected List<UUID> winners = new ArrayList<>();
    protected Map<UUID, Integer> progress = new HashMap<>();
    protected int timeLeft;
    protected boolean event;
    protected Map<UUID, Integer> score = new HashMap<>();

    public int getScore(UUID uuid) {
        return score.getOrDefault(uuid, 0);
    }

    public void addScore(UUID uuid, int value) {
        score.put(uuid, Math.max(0, getScore(uuid) + value));
    }
}
