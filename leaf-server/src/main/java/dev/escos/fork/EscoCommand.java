package dev.escos.fork;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class EscoCommand {
    private EscoCommand() {}
    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        for (final String name : new String[] {"escof", "esco"}) {
            dispatcher.register(Commands.literal(name).requires(Commands.hasPermission(Commands.LEVEL_ADMINS)).executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("EscoF 0.7.0 / 26.2 | Developed By Firesco | synchronous plugin APIs | snapshot compute workers=" + EscoConfig.WORKERS), false);
                EscoMetrics.snapshot().forEach((key, value) -> context.getSource().sendSuccess(() -> Component.literal(key + "=" + value), false));
                return 1;
            }));
        }
    }
}
