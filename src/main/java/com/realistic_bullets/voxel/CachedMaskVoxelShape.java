package com.realistic_bullets.voxel;

import net.minecraft.world.phys.shapes.VoxelShape;

/** Лениво пересчитывает VoxelShape при изменении версии VoxelVolume. */
public final class CachedMaskVoxelShape {
    private final VoxelVolume volume;
    private VoxelShape cached;
    private int cachedVersion = -1;

    public CachedMaskVoxelShape(VoxelVolume volume) { this.volume = volume; }

    public VoxelShape get() {
        int v = volume.version();
        if (cached == null || cachedVersion != v) {
            cached = volume.buildShape();
            cachedVersion = v;
        }
        return cached;
    }
}
