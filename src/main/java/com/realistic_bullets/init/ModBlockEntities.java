package com.realistic_bullets.init;

import com.realistic_bullets.RealisticBulletsMod;
import com.realistic_bullets.block.entity.DamagedBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RealisticBulletsMod.MOD_ID);

    public static final Supplier<BlockEntityType<DamagedBlockEntity>> DAMAGED_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("damaged_block", () ->
                    BlockEntityType.Builder.of(DamagedBlockEntity::new, ModBlocks.DAMAGED_BLOCK.get())
                            .build(null));
}
