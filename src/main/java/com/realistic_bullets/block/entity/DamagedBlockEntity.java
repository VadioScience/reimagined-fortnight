package com.realistic_bullets.block.entity;

import com.realistic_bullets.init.ModBlockEntities;
import com.realistic_bullets.voxel.CachedMaskVoxelShape;
import com.realistic_bullets.voxel.DamageModelData;
import com.realistic_bullets.voxel.VoxelVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class DamagedBlockEntity extends BlockEntity {
    private BlockState originalState = Blocks.STONE.defaultBlockState();
    private final VoxelVolume volume = new VoxelVolume();
    private final CachedMaskVoxelShape shapeCache = new CachedMaskVoxelShape(volume);

    public DamagedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DAMAGED_BLOCK_ENTITY.get(), pos, state);
    }

    public BlockState getOriginalState() { return originalState; }
    public void setOriginalState(BlockState state) { this.originalState = state; }
    public VoxelVolume getVolume() { return volume; }
    public VoxelShape getVoxelShape() { return shapeCache.get(); }

    /**
     * Сервер: применить попадание. local — точка в пространстве блока (0..16),
     * motion — направление пули (для сквозной выбоины на выходе).
     */
    public void applyDamage(Vec3 local, Direction face, double radius, @Nullable Vec3 motion) {
        volume.carve(local, face, radius);

        Vec3 dir = motion != null && motion.lengthSqr() > 1e-6
                ? motion.normalize()
                : Vec3.atLowerCornerOf(face.getNormal());
        double t = exitDistance(local, dir);
        if (t > 0 && t < 16) {
            Vec3 exit = local.add(dir.scale(t));
            volume.carve(exit, faceOf(exit), radius * 0.85);
        }

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private static double exitDistance(Vec3 p, Vec3 d) {
        double t = Double.MAX_VALUE;
        t = axis(t, p.x, d.x); t = axis(t, p.y, d.y); t = axis(t, p.z, d.z);
        return t;
    }

    private static double axis(double best, double s, double d) {
        double t = d > 0 ? (16 - s) / d : d < 0 ? (0 - s) / d : Double.MAX_VALUE;
        return t > 0 ? Math.min(best, t) : best;
    }

    private static Direction faceOf(Vec3 p) {
        double dx0 = p.x, dx1 = 16 - p.x, dy0 = p.y, dy1 = 16 - p.y, dz0 = p.z, dz1 = 16 - p.z;
        double m = Math.min(Math.min(dx0, dx1), Math.min(Math.min(dy0, dy1), Math.min(dz0, dz1)));
        if (m == dx0) return Direction.WEST;
        if (m == dx1) return Direction.EAST;
        if (m == dy0) return Direction.DOWN;
        if (m == dy1) return Direction.UP;
        if (m == dz0) return Direction.NORTH;
        return Direction.SOUTH;
    }

    // ---------- NBT (1.21.x: через HolderLookup.Provider) ----------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Original", BlockState.CODEC.encodeStart(
                registries.createSerializationContext(NbtOps.INSTANCE), originalState)
                .result().orElseThrow());
        tag.putLongArray("Voxels", volume.toLongArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Original", Tag.TAG_COMPOUND)) {
            BlockState.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),
                            tag.getCompound("Original"))
                    .result().ifPresent(s -> originalState = s);
        }
        if (tag.contains("Voxels", Tag.TAG_LONG_ARRAY)) {
            volume.fromLongArray(tag.getLongArray("Voxels"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
        if (level != null && level.isClientSide) {
            requestModelDataUpdate(); // пересборка чанк-секции с новым ModelData
        }
    }

    /** Вызывается только на клиенте. */
    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(DamageModelData.ORIGINAL_STATE, originalState)
                .with(DamageModelData.VOXELS, volume)
                .build();
    }
}
