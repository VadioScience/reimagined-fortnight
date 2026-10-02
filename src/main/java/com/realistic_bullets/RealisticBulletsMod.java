package com.realistic_bullets;

import com.realistic_bullets.compat.TaczCompatHandler;
import com.realistic_bullets.init.ModBlockEntities;
import com.realistic_bullets.init.ModBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(RealisticBulletsMod.MOD_ID)
public final class RealisticBulletsMod {
    public static final String MOD_ID = "realistic_bullets";
    public static final Logger LOGGER = LogManager.getLogger("RealisticBullets");

    public RealisticBulletsMod(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        TaczCompatHandler.init(); // безопасен, если TaCZ нет
    }
}
