package com.realistic_bullets.client;

import com.realistic_bullets.RealisticBulletsMod;
import com.realistic_bullets.client.model.DamagedBlockBakedModel;
import net.minecraft.client.renderer.block.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = RealisticBulletsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    /** Подменяем запечённую модель damaged_block на нашу обёртку. */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ModelResourceLocation key = ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(RealisticBulletsMod.MOD_ID, "damaged_block"));
        BakedModel original = event.getModels().get(key);
        if (original != null) {
            event.getModels().put(key, new DamagedBlockBakedModel(original));
        }
    }
}
