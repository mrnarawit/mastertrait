package com.mastertrait.client;

import com.mastertrait.Config;
import com.mastertrait.MasterTrait;
import com.mastertrait.trait.ItemState;
import com.mastertrait.trait.Tier;
import com.mastertrait.trait.Trait;
import com.mastertrait.trait.TraitUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = MasterTrait.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z0-9_]+)(:min)?}");

    private static String text(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().is(net.minecraft.world.item.Items.HEART_OF_THE_SEA)) {
            event.getToolTip().add(Component.empty());
            event.getToolTip().add(Component.literal(text("tooltip.mastertrait.reroll_item")).withStyle(ChatFormatting.AQUA));
        }
        net.minecraft.world.item.component.CustomData cd = event.getItemStack().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (cd != null && cd.contains(com.mastertrait.event.CommonEvents.REROLL_FLAG)) {
            event.getToolTip().add(Component.empty());
            event.getToolTip().add(Component.literal(text("tooltip.mastertrait.reroll_hidden")).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.OBFUSCATED));
            event.getToolTip().add(Component.literal(text("tooltip.mastertrait.reroll_hidden_2")).withStyle(ChatFormatting.GRAY));
            return;
        }
        List<Trait> traits = new java.util.ArrayList<>(TraitUtil.getTraits(event.getItemStack(), false));
        if (traits.isEmpty()) return;
        traits.sort(java.util.Comparator.comparingInt(x -> x.tier().rank()));

        Minecraft mc = Minecraft.getInstance();
        Player viewer = mc.player;
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        boolean shift = Screen.hasShiftDown();

        List<Component> tip = event.getToolTip();
        tip.add(Component.empty());

        boolean secret = topTraitsHidden();
        for (Trait trait : traits) {
            boolean hide = secret && trait.tier().isTop();
            tip.add(header(trait, event.getItemStack(), hide));

            Component progress = trait.progress(event.getItemStack(), viewer, gameTime);
            if (progress != null) {
                for (String raw : progress.getString().split("\n")) {
                    for (String part : wrap(raw, 46)) {
                        tip.add(Component.literal("  " + part).withStyle(trait.progressColor(event.getItemStack())));
                    }
                }
            }

            if (trait.hasCooldown()) {
                long left = ItemState.cooldownEnd(event.getItemStack(), trait.id()) - gameTime;
                if (left <= 0) {
                    tip.add(Component.literal("  CD: Ready").withStyle(ChatFormatting.GREEN));
                } else {
                    tip.add(Component.literal("  CD: " + ItemState.formatTicks(left)).withStyle(ChatFormatting.RED));
                }
            }

            if (shift) {
                for (String line : description(trait).split("\n")) {
                    for (String part : wrap(line, 46)) {
                        MutableComponent c = Component.literal(part).withStyle(trait.descColor());
                        tip.add(hide ? c.withStyle(ChatFormatting.OBFUSCATED) : c);
                    }
                }
            }
        }

        if (!shift) {
            tip.add(Component.translatable("tooltip.mastertrait.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Divine, Heroic and Mythical traits are scrambled until the player has slain the Wither. */
    private static boolean topTraitsHidden() {
        try {
            if (!Config.HIDE_UNTIL_WITHER.get()) return false;
        } catch (RuntimeException e) {
            return false;
        }
        Player p = Minecraft.getInstance().player;
        return p == null || !com.mastertrait.ModAttachments.clientFlag(p);
    }

    private static MutableComponent scramble(MutableComponent c, boolean hide) {
        if (!hide) return c;
        for (Component sibling : c.getSiblings()) {
            if (sibling instanceof MutableComponent m) m.withStyle(ChatFormatting.OBFUSCATED);
        }
        return c.withStyle(ChatFormatting.OBFUSCATED);
    }

    private static Component header(Trait trait, ItemStack stack, boolean hide) {
        ChatFormatting nc = trait.nameColor(stack);
        Component name = Component.translatable(trait.nameKey());
        Tier tier = trait.tier();
        if (!trait.showLabel() && trait.rainbowName()) {
            return scramble(animated(name.getString(), 0f, true), hide);
        }
        switch (tier) {
            case MYTHICAL: {
                return mythical("✧ " + text("tooltip.mastertrait.mythical_trait") + ":", trait.mythicalColors())
                        .append(" ").append(scramble(mythical(name.getString(), trait.mythicalColors()), hide));
            }
            case HEROIC: {
                MutableComponent label = animated("☆ " + text("tooltip.mastertrait.heroic_trait") + ":", 0f, true);
                return label.append(" ").append(scramble(name.copy().withStyle(
                        nc != null ? nc : ChatFormatting.DARK_RED), hide));
            }
            case DIVINE: {
                MutableComponent label = animated("✦ " + text("tooltip.mastertrait.divine_trait") + ":", trait.divineHue(), false);
                MutableComponent n = nc != null
                        ? name.copy().withStyle(nc)
                        : animated(name.getString(), trait.divineHue(), false);
                return label.append(" ").append(scramble(n, hide));
            }
            case UNIQUE: {
                if (!trait.showLabel()) {
                    return name.copy().withStyle(nc != null ? nc : ChatFormatting.GREEN);
                }
                ChatFormatting nameColor = nc != null ? nc : ChatFormatting.GREEN;
                return Component.literal(text("tooltip.mastertrait.unique_trait") + ": ").withStyle(ChatFormatting.GREEN)
                        .append(name.copy().withStyle(nameColor));
            }
            default:
                return Component.literal("• " + text("tooltip.mastertrait.trait") + ": ")
                        .append(name).withStyle(ChatFormatting.GOLD);
        }
    }

    /** Raw lang text with {key} tokens replaced by config numbers; "?cataclysm|" lines only with Cataclysm. */
    private static String description(Trait trait) {
        String raw = text(trait.descKey());
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\n")) {
            if (line.startsWith("?")) {
                int bar = line.indexOf('|');
                if (bar < 0) continue;
                String modid = line.substring(1, bar);
                if (!ModList.get().isLoaded(modid)) continue;
                line = line.substring(bar + 1);
            }
            if (out.length() > 0) out.append('\n');
            out.append(replaceTokens(trait, line));
        }
        return out.toString();
    }

    private static String replaceTokens(Trait trait, String line) {
        Matcher m = TOKEN.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String repl;
            try {
                double v = Config.value(trait.paramsId(), m.group(1));
                repl = m.group(2) != null ? duration(v) : number(v);
            } catch (Throwable t) {
                repl = "?";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Word-wraps a line so long descriptions do not run off the screen. */
    private static List<String> wrap(String line, int width) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : line.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > width) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        out.add(cur.toString());
        return out;
    }

    /** 300 -> "5 min", 30 -> "30s". */
    private static String duration(double seconds) {
        long s = Math.round(seconds);
        return s >= 60 && s % 60 == 0 ? (s / 60) + " min" : s + "s";
    }

    private static String number(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0E-9) return Long.toString(Math.round(v));
        String s = String.format(Locale.ROOT, "%.2f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** Per-character colour animation: rainbow (heroic) or a shimmer around a base hue (divine). */
    private static MutableComponent animated(String text, float baseHue, boolean rainbow) {
        MutableComponent out = Component.empty();
        long t = System.currentTimeMillis() / 20L;
        for (int i = 0; i < text.length(); i++) {
            int rgb;
            if (rainbow) {
                rgb = Mth.hsvToRgb(((t + i * 25L) % 360L) / 360f, 0.85f, 1.0f);
            } else {
                float wave = (float) Math.sin((t + i * 12L) / 18.0);
                rgb = Mth.hsvToRgb(baseHue + 0.03f * wave, 0.55f + 0.25f * wave, 1.0f);
            }
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(rgb & 0xFFFFFF)));
        }
        return out;
    }

    private static final int[] MYTHICAL_COLORS = {0x0B1F9E, 0x4B0082, 0x8F2BFF};

    /** Colours flowing along the text (default deep blue -> indigo -> violet). */
    private static MutableComponent mythical(String text, int[] palette) {
        final int[] colors = palette != null && palette.length >= 2 ? palette : MYTHICAL_COLORS;
        MutableComponent out = Component.empty();
        double t = System.currentTimeMillis() / 700.0;
        int n = colors.length;
        for (int i = 0; i < text.length(); i++) {
            double pos = ((t - i * 0.18) % n + n) % n;
            int a = (int) pos;
            double f = pos - a;
            int c1 = colors[a % n];
            int c2 = colors[(a + 1) % n];
            int rgb = 0;
            for (int shift = 16; shift >= 0; shift -= 8) {
                int v1 = (c1 >> shift) & 0xFF;
                int v2 = (c2 >> shift) & 0xFF;
                rgb |= ((int) Math.round(v1 + (v2 - v1) * f)) << shift;
            }
            out.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(Style.EMPTY.withColor(rgb & 0xFFFFFF)));
        }
        return out;
    }

    private ClientEvents() {}
}
