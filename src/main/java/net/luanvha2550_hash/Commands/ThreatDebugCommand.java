package net.luanvha2550_hash.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.luanvha2550_hash.Overlay.ThreatDebugManager;

/**
 * Command to toggle threat analysis debug overlay
 */
public class ThreatDebugCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("threatdebug")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ThreatDebugCommand::toggleDebug)
                .then(CommandManager.literal("on")
                    .executes(ctx -> setDebug(ctx, true)))
                .then(CommandManager.literal("off")
                    .executes(ctx -> setDebug(ctx, false)))
                .then(CommandManager.literal("clear")
                    .executes(ThreatDebugCommand::clearDebug))
        );
    }

    private static int toggleDebug(CommandContext<ServerCommandSource> ctx) {
        ThreatDebugManager.toggleDebug();
        boolean enabled = ThreatDebugManager.isDebugEnabled();

        Text message = Text.literal("Debug de Análise de Ameaças: ")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(enabled ? "ATIVADO" : "DESATIVADO")
                .formatted(enabled ? Formatting.GREEN : Formatting.RED));

        ctx.getSource().sendFeedback(() -> message, true);

        if (enabled) {
            ctx.getSource().sendFeedback(() ->
                Text.literal("Os cálculos de ameaça serão exibidos acima das entidades.")
                    .formatted(Formatting.GRAY), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int setDebug(CommandContext<ServerCommandSource> ctx, boolean enable) {
        ThreatDebugManager.setDebugEnabled(enable);

        Text message = Text.literal("Debug de Análise de Ameaças: ")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(enable ? "ATIVADO" : "DESATIVADO")
                .formatted(enable ? Formatting.GREEN : Formatting.RED));

        ctx.getSource().sendFeedback(() -> message, true);

        if (enable) {
            ctx.getSource().sendFeedback(() ->
                Text.literal("Os cálculos de ameaça serão exibidos acima das entidades.")
                    .formatted(Formatting.GRAY), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int clearDebug(CommandContext<ServerCommandSource> ctx) {
        ThreatDebugManager.clear();

        ctx.getSource().sendFeedback(() ->
            Text.literal("Dados de debug de ameaças limpos.")
                .formatted(Formatting.GREEN), true);

        return Command.SINGLE_SUCCESS;
    }
}

