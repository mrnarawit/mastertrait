package com.mastertrait.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Optional Pehkui integration via reflection, so the mod compiles and runs without Pehkui installed.
 * Uses ScaleTypes.BASE.getScaleData(entity).getTargetScale()/setTargetScale(float).
 */
public final class PehkuiCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized;
    private static boolean available;
    private static Object baseType;
    private static Method getScaleData;
    private static Method getTargetScale;
    private static Method setTargetScale;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> types = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            baseType = types.getField("BASE").get(null);
            Class<?> scaleType = Class.forName("virtuoel.pehkui.api.ScaleType");
            getScaleData = scaleType.getMethod("getScaleData", Entity.class);
            Class<?> scaleData = Class.forName("virtuoel.pehkui.api.ScaleData");
            getTargetScale = scaleData.getMethod("getTargetScale");
            setTargetScale = scaleData.getMethod("setTargetScale", float.class);
            available = true;
        } catch (Throwable t) {
            LOGGER.warn("Pehkui not found or incompatible; size-based traits will not change size.");
        }
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    public static float getScale(Entity e) {
        if (!isAvailable()) return 1.0f;
        try {
            Object data = getScaleData.invoke(baseType, e);
            return (Float) getTargetScale.invoke(data);
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    public static void setScale(Entity e, float scale) {
        if (!isAvailable()) return;
        try {
            Object data = getScaleData.invoke(baseType, e);
            setTargetScale.invoke(data, scale);
        } catch (Throwable t) {
            LOGGER.warn("Failed to set Pehkui scale", t);
        }
    }

    /** Generic grow/restore for any entity, remembering its original size in its persistent data. */
    public static void applyScale(Entity e, boolean on, float growth) {
        if (!isAvailable()) return;
        CompoundTag d = e.getPersistentData();
        boolean scaled = d.getBoolean("ut_scaled");
        if (on && !scaled) {
            float cur = getScale(e);
            d.putFloat("ut_scale_orig", cur);
            setScale(e, cur * growth);
            d.putBoolean("ut_scaled", true);
        } else if (!on && scaled) {
            float orig = d.getFloat("ut_scale_orig");
            setScale(e, orig <= 0f ? 1.0f : orig);
            d.putBoolean("ut_scaled", false);
        }
    }

    private PehkuiCompat() {}
}
