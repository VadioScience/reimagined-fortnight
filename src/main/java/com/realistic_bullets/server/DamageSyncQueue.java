package com.realistic_bullets.server;

import com.realistic_bullets.RealisticBulletsMod;
import com.realistic_bullets.block.DamagedBlock;
import com.realistic_bullets.block.entity.DamagedBlockEntity;
import com.realistic_bullets.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

/** TaCZ считает траектории вне главного потока — применяем всё на ServerTickEvent.Post. */
@EventBusSubscriber(modid = RealisticBulletsMod.MOD_ID)
public final class DamageSyncQueue {
    private DamageSyncQueue() {}

    /** Тег data/realistic_bullets/tags/blocks/no_voxel_damage.json */
    public static final TagKey<Block> NO_VOXEL_DAMAGE =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(RealisticBulletsMod.MOD_ID, "no_voxel_damage"));

    private record Pending(ServerLevel level, BlockPos pos, Vec3 hit, Direction face,
                           double radius, Vec3 motion) {}

    private static final ConcurrentLinkedQueue<Pending> QUEUE = new ConcurrentLinkedQueue<>();

    public static void submit(ServerLevel level, BlockPos pos, Vec3 hit, Direction face,
                              double radius, Vec3 motion) {
        QUEUE.add(new Pending(level, pos, hit, face, radius, motion));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Pending p;
        while ((p = QUEUE.poll()) != null) {
            try { apply(p); } catch (Throwable t) {
                RealisticBulletsMod.LOGGER.error("Failed to apply bullet damage", t);
            }
        }
    }

    private static void apply(Pending p) {
        ServerLevel level = p.level();
        BlockPos pos = p.pos();
        if (!level.isLoaded(pos)) return;

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        if (!(state.getBlock() instanceof DamagedBlock)) {
            if (isProtected(level, pos, state)) return;
            convert(level, pos, state);
        }

        if (level.getBlockEntity(pos) instanceof DamagedBlockEntity be) {
            Vec3 local = p.hit().subtract(Vec3.atLowerCornerOf(pos)).scale(16.0);
            be.applyDamage(local, p.face(), p.radius(), p.motion());
        }
    }

    private static boolean isProtected(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getDestroySpeed(level, pos) < 0) return true;      // бедрок и т.п.
        if (state.hasBlockEntity()) return true;                     // сундуки, печи...
        if (!state.getFluidState().isEmpty()) return true;           // жидкости
        return state.is(NO_VOXEL_DAMAGE);                            // стёкла и пр.
    }

    private static void convert(ServerLevel level, BlockPos pos, BlockState original) {
        boolean water = level.getFluidState(pos).getType() == Fluids.WATER;
        BlockState damaged = ModBlocks.DAMAGED_BLOCK.get().defaultBlockState()
                .setValue(DamagedBlock.WATERLOGGED, water);
        level.setBlock(pos, damaged, 3);
        if (level.getBlockEntity(pos) instanceof DamagedBlockEntity be) {
            be.setOriginalState(original);
        }
    }
}
