package com.cavetale.kotl;

import java.util.ArrayList;
import java.util.List;
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

    public List<Vec> allVecs() {
        List<Vec> result = new ArrayList<>();
        for (int y = a.y; y <= b.y; y += 1) {
            for (int z = a.z; y <= b.z; z += 1) {
                for (int x = a.x; y <= b.x; x += 1) {
                    result.add(Vec.v(x, y, z));
                    if (result.size() > 1000) break;
                }
            }
        }
        return result;
    }
}
