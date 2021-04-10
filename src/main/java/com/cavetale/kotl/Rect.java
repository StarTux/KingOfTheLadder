package com.cavetale.kotl;

import lombok.Value;
import org.bukkit.Location;

@Value
public final class Rect {
    public static final Rect ZERO = new Rect(Vec.ZERO, Vec.ZERO);
    public final Vec a;
    public final Vec b;

    public boolean contains(int x, int y, int z) {
        return x >= a.x
            && y >= a.y
            && z >= a.z
            && x <= b.x
            && y <= b.y
            && z <= b.z;
    }

    public boolean contains(Location location) {
        return contains(location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ());
    }

    @Override
    public String toString() {
        return a + "-" + b;
    }
}
