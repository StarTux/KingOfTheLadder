package com.cavetale.kotl;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public final class SaveTag implements Serializable {
    private boolean event;
    private boolean pause;
    private Map<UUID, Integer> score = new HashMap<>();

    public int getScore(UUID uuid) {
        return score.getOrDefault(uuid, 0);
    }

    public void addScore(UUID uuid, int value) {
        score.put(uuid, Math.max(0, getScore(uuid) + value));
    }
}
