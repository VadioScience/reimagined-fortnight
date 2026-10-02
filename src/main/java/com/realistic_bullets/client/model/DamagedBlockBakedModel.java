package com.realistic_bullets.client.model;

import com.realistic_bullets.voxel.DamageModelData;
import com.realistic_bullets.voxel.VoxelVolume;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DamagedBlockBakedModel implements BakedModel {
    private final BakedModel placeholder; // cube_all/stone из assets

    public DamagedBlockBakedModel(BakedModel placeholder) {
        this.placeholder = placeholder;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return placeholder.getQuads(state, side, rand);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        BlockState original = data.get(DamageModelData.ORIGINAL_STATE);
        VoxelVolume volume = data.get(DamageModelData.VOXELS);
        if (original == null || volume == null || volume.isEmpty()) {
            return placeholder.getQuads(state, side, rand, data, renderType);
        }
        DamageQuadModelCache.CompiledModel compiled = DamageQuadModelCache.get(original, volume);
        return side == null ? compiled.general : compiled.bySide.get(side);
    }

    @Override
    public boolean useAmbientOcclusion() { return placeholder.useAmbientOcclusion(); }

    @Override
    public boolean useAmbientOcclusion(BlockState state, ModelData data) {
        BlockState original = data.get(DamageModelData.ORIGINAL_STATE);
        return original == null || Minecraft.getInstance().getBlockRenderer()
                .getBlockModelShaper().getBlockModel(original)
                .useAmbientOcclusion(original, data);
    }

    @Override
    public boolean isGui3d() { return placeholder.isGui3d(); }

    @Override
    public boolean usesBlockLight() { return placeholder.usesBlockLight(); }

    @Override
    public TextureAtlasSprite getParticleIcon() { return placeholder.getParticleIcon(); }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState original = data.get(DamageModelData.ORIGINAL_STATE);
        return original != null
                ? Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(original)
                : placeholder.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() { return placeholder.getTransforms(); }

    @Override
    public ItemOverrides getOverrides() { return placeholder.getOverrides(); }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        BlockState original = data.get(DamageModelData.ORIGINAL_STATE);
        if (original == null) return placeholder.getRenderTypes(state, rand, data);
        return Minecraft.getInstance().getBlockRenderer()
                .getBlockModelShaper().getBlockModel(original)
                .getRenderTypes(original, rand, data);
    }
}
