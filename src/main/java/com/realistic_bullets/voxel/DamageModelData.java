package com.realistic_bullets.voxel;

import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class DamageModelData {
    private DamageModelData() {}

    public static final ModelProperty<BlockState> ORIGINAL_STATE = new ModelProperty<>();
    public static final ModelProperty<VoxelVolume> VOXELS = new ModelProperty<>();
}
