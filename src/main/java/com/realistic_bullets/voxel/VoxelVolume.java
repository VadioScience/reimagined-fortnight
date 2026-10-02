package com.realistic_bullets.voxel;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;

/** Сетка 16x16x16 внутри одного блока. true = выбито (дырка). */
public final class VoxelVolume {
    public static final int SIZE = 16;
    private final long[] bits = new long[SIZE * SIZE * SIZE / 64];
    private int version;

    public int version() { return version; }

    public boolean isEmpty() {
        for (long l : bits) if (l != 0L) return false;
        return true;
    }

    public boolean isDamaged(int x, int y, int z) {
        if ((x | y | z) < 0 || x >= SIZE || y >= SIZE || z >= SIZE) return false;
        int i = index(x, y, z);
        return (bits[i >>> 6] >>> (i & 63) & 1L) != 0L;
    }

    private static int index(int x, int y, int z) { return x + (z << 4) + (y << 8); }

    private void setDamaged(int x, int y, int z) {
        int i = index(x, y, z);
        bits[i >>> 6] |= 1L << (i & 63);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(SIZE - 1, v)); }

    /** Сферическая выбоина. local — точка в пространстве блока (0..16). */
    public void carve(Vec3 local, Direction face, double radius) {
        Vec3 center = local.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.45));
        double r2 = radius * radius;
        int x0 = clamp((int) Math.floor(center.x - radius)), x1 = clamp((int) Math.ceil(center.x + radius));
        int y0 = clamp((int) Math.floor(center.y - radius)), y1 = clamp((int) Math.ceil(center.y + radius));
        int z0 = clamp((int) Math.floor(center.z - radius)), z1 = clamp((int) Math.ceil(center.z + radius));
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    double dx = x + 0.5 - center.x, dy = y + 0.5 - center.y, dz = z + 0.5 - center.z;
                    if (dx * dx + dy * dy + dz * dz <= r2) setDamaged(x, y, z);
                }
        version++;
    }

    public long[] toLongArray() { return bits.clone(); }

    public void fromLongArray(long[] data) {
        if (data != null && data.length == bits.length) {
            System.arraycopy(data, 0, bits, 0, bits.length);
            version++;
        }
    }

    /** Форма из целых (невыбитых) ячеек — коллизия и рендер-фрустум. */
    public VoxelShape buildShape() {
        VoxelShape shape = Shapes.empty();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int run = -1;
                for (int y = 0; y <= SIZE; y++) {
                    boolean solid = y < SIZE && !isDamaged(x, y, z);
                    if (solid && run < 0) run = y;
                    if (!solid && run >= 0) {
                        shape = Shapes.or(shape, Shapes.box(x, run, z, x + 1, y, z + 1));
                        run = -1;
                    }
                }
            }
        }
        return shape;
    }

    /** DDA: параметр t в [0,1] входа отрезка в первую дыру, или null. */
    public Double clip(Vec3 start, Vec3 end) {
        double dx = end.x - start.x, dy = end.y - start.y, dz = end.z - start.z;
        int x = (int) Math.floor(start.x), y = (int) Math.floor(start.y), z = (int) Math.floor(start.z);
        if (isDamaged(x, y, z)) return 0.0;
        int sx = sign(dx), sy = sign(dy), sz = sign(dz);
        double tMaxX = intbound(start.x, dx), tMaxY = intbound(start.y, dy), tMaxZ = intbound(start.z, dz);
        double tDx = sx != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double tDy = sy != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double tDz = sz != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;
        double t = 0;
        while (t <= 1.0) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ)      { x += sx; t = tMaxX; tMaxX += tDx; }
            else if (tMaxY < tMaxZ)                  { y += sy; t = tMaxY; tMaxY += tDy; }
            else                                     { z += sz; t = tMaxZ; tMaxZ += tDz; }
            if (t > 1.0) break;
            if (isDamaged(x, y, z)) return t;
        }
        return null;
    }

    private static double intbound(double s, double ds) {
        if (ds > 0) return (Math.ceil(s) - s) / ds;
        if (ds < 0) return (s - Math.floor(s)) / -ds;
        return Double.POSITIVE_INFINITY;
    }

    private static int sign(double v) { return v > 0 ? 1 : v < 0 ? -1 : 0; }

    @Override public int hashCode() { return Arrays.hashCode(bits); }
}
