package com.mastertrait.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mastertrait.ModComponents;
import com.mastertrait.MasterTrait;
import com.mastertrait.event.CommonEvents;
import com.mastertrait.trait.Trait;
import com.mastertrait.trait.TraitUtil;
import com.mastertrait.trait.Traits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Test/admin command: /mastertrait set|add|reroll|clear|list */
@EventBusSubscriber(modid = MasterTrait.MODID)
public final class TraitCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mastertrait")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("trait", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Traits.ids(), b))
                                .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "trait"), false))))
                .then(Commands.literal("add")
                        .then(Commands.argument("trait", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Traits.ids(), b))
                                .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "trait"), true))))
                .then(Commands.literal("reroll").executes(ctx -> reroll(ctx.getSource())))
                .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource()))));
    }

    private static ItemStack heldWeapon(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (!TraitUtil.isMeleeWeapon(stack) && !TraitUtil.isShield(stack) && !TraitUtil.isRangedWeapon(stack)) {
            src.sendFailure(Component.literal("Hold a sword, axe, mace, trident, bow, crossbow or shield in your main hand."));
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static int set(CommandSourceStack src, String id, boolean extra) throws CommandSyntaxException {
        ItemStack stack = heldWeapon(src);
        if (stack.isEmpty()) return 0;
        if (Traits.get(id) == null) {
            src.sendFailure(Component.literal("Unknown trait: " + id));
            return 0;
        }
        List<String> ids = new ArrayList<>(extra ? TraitUtil.traitIds(stack) : List.<String>of());
        if (!ids.contains(id)) ids.add(id);
        TraitUtil.setTraits(stack, ids);
        Traits.get(id).onRolled(stack);
        src.sendSuccess(() -> Component.literal((extra ? "Added " : "Trait set to ") + id), false);
        return 1;
    }

    private static int reroll(CommandSourceStack src) throws CommandSyntaxException {
        ItemStack stack = heldWeapon(src);
        if (stack.isEmpty()) return 0;
        stack.remove(ModComponents.TRAITS.get());
        CommonEvents.roll(stack, src.getPlayerOrException().getRandom());
        src.sendSuccess(() -> Component.literal("Rerolled."), false);
        return 1;
    }

    private static int clear(CommandSourceStack src) throws CommandSyntaxException {
        ItemStack stack = heldWeapon(src);
        if (stack.isEmpty()) return 0;
        TraitUtil.setTraits(stack, List.of());
        src.sendSuccess(() -> Component.literal("Traits removed."), false);
        return 1;
    }

    private static int list(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal(String.join(", ", Traits.ids())), false);
        return 1;
    }

    private TraitCommand() {}
}
