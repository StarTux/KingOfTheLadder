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
    State state;
    String world;
    Rect area;
    Rect goal;
    Set<Vec> spawnBlocks;
    List<UUID> winners = new ArrayList<>();
    Map<UUID, Integer> scores = new HashMap<>();
    int timeLeft;
}
