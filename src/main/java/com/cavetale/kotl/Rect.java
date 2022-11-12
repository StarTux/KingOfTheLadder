package com.cavetale.kotl;

import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec3i;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.bukkit.Location;

@Value @RequiredArgsConstructor
public final class Rect {
    public static final Rect ZERO = new Rect(Vec3i.ZERO, Vec3i.ZERO);
    public final Vec3i a;
    public final Vec3i b;

    public Rect(final Cuboid cuboid) {
        this(cuboid.getMin(), cuboid.getMax());
    }

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

    public List<Vec3i> allVecs() {
        List<Vec3i> result = new ArrayList<>();
        OUTER: for (int y = a.y; y <= b.y; y += 1) {
            for (int z = a.z; z <= b.z; z += 1) {
                for (int x = a.x; x <= b.x; x += 1) {
                    result.add(Vec3i.of(x, y, z));
                    if (result.size() > 1000) break OUTER;
                }
            }
        }
        return result;
    }
}
