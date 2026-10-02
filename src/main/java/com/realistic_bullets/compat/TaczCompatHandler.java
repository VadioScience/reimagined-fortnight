package com.realistic_bullets.compat;

import com.realistic_bullets.RealisticBulletsMod;
import com.realistic_bullets.server.DamageSyncQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;

/**
 * Хук в TaCZ через рефлексию (в оригинале — тот же подход, класс TaczCompatHandler$EventAccessors).
 * Поддерживает TaCZ для 1.21.1 (NeoForge), имена геттеров перебираются на случай переименований.
 */
public final class TaczCompatHandler {
    private TaczCompatHandler() {}

    private static Class<?> gunHitBlockEvent;
    private static MethodHandle getLevel;
    private static MethodHandle getHitResult;
    private static MethodHandle getBullet;

    public static void init() {
        try {
            gunHitBlockEvent = Class.forName("com.tacz.guns.api.event.common.GunHitBlockEvent");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getLevel = findGetter(lookup, gunHitBlockEvent, "getLevel", "getWorld");
            getHitResult = findGetter(lookup, gunHitBlockEvent, "getHitResult", "getHit");
            try {
                getBullet = findGetter(lookup, gunHitBlockEvent, "getBullet");
            } catch (Throwable ignored) {
                getBullet = null; // не во всех версиях TaCZ есть
            }
            NeoForge.EVENT_BUS.addListener(TaczCompatHandler::onEvent);
            RealisticBulletsMod.LOGGER.info("TaCZ detected: voxel bullet damage enabled");
        } catch (Throwable t) {
            gunHitBlockEvent = null;
            RealisticBulletsMod.LOGGER.info("TaCZ not found: voxel bullet damage inactive");
        }
    }

    private static MethodHandle findGetter(MethodHandles.Lookup lookup, Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Class<?> ret = owner.getMethod(name).getReturnType();
                return lookup.findVirtual(owner, name, MethodType.methodType(ret));
            } catch (NoSuchMethodException | IllegalAccessException ignored) { }
        }
        throw new IllegalStateException("None of " + Arrays.toString(names) + " found on " + owner);
    }

    /** Зарегистрирован на все события — фильтр по классу первой строкой. */
    private static void onEvent(Event event) {
        if (gunHitBlockEvent == null || !gunHitBlockEvent.isInstance(event)) return;
        if (event instanceof ICancellableEvent ce && ce.isCanceled()) return;
        try {
            Object levelObj = getLevel.invoke(event);
            Object hitObj = getHitResult.invoke(event);
            if (!(levelObj instanceof ServerLevel level) || !(hitObj instanceof BlockHitResult hit)) return;
            if (hit.getType() != HitResult.Type.BLOCK) return;

            Vec3 motion = readMotion(event);
            double speed = motion != null ? motion.length() : 1.0;
            double radius = 3.2 + Math.min(speed * 0.15, 1.6); // подберите под вкус
            DamageSyncQueue.submit(level, hit.getBlockPos(), hit.getLocation(),
                    hit.getDirection(), radius, motion);
        } catch (Throwable ignored) { }
    }

    private static Vec3 readMotion(Event event) {
        try {
            Object bullet = getBullet != null ? getBullet.invoke(event) : null;
            if (bullet == null) return null;
            for (String name : new String[]{"getDeltaMovement", "getMotion"}) {
                try {
                    Object v = bullet.getClass().getMethod(name).invoke(bullet);
                    if (v instanceof Vec3 vec) return vec;
                } catch (ReflectiveOperationException ignored) { }
            }
        } catch (Throwable ignored) { }
        return null;
    }
}
