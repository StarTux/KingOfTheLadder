package com.cavetale.kotl;

import lombok.Value;
import org.bukkit.block.Block;

@Value
public final class Vec {
    public static final Vec ZERO = new Vec(0, 0, 0);
    public final int x;
    public final int y;
    public final int z;

    public static Vec v(int x, int y, int z) {
        return new Vec(x, y, z);
    }

    public boolean isBlock(Block block) {
        return x == block.getX()
            && y == block.getY()
            && z == block.getZ();
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ")";
    }
}
