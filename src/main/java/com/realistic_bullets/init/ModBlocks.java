package com.realistic_bullets.init;

import com.realistic_bullets.RealisticBulletsMod;
import com.realistic_bullets.block.DamagedBlock;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(RealisticBulletsMod.MOD_ID);

    public static final DeferredBlock<DamagedBlock> DAMAGED_BLOCK =
            BLOCKS.register("damaged_block", DamagedBlock::new);
}
