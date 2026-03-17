package net.luanvha2550_hash.Commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.luanvha2550_hash.Entity.createFakePlayer;
import net.luanvha2550_hash.GameAI.BotEventHandler;
import net.luanvha2550_hash.Entity.AutoFaceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registro simplificado de comandos do Clawbot-MC.
 * Comandos disponíveis:
 * - /aiplayer spawn <nome> - Spawna um novo bot
 * - /aiplayer remove - Remove o bot atual
 * - /clawbot - Abre o menu de configuração (em configCommand.java)
 */
public class modCommandRegistry {

    public static final Logger LOGGER = LoggerFactory.getLogger("mod-command-registry");
    public static String botName = "";
    public static boolean isTrainingMode = false;

    public static void register() {
        // Registrar comando de debug de ameaças
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ThreatDebugCommand.register(dispatcher);
        });

        // Registrar comando de debug /clawdev
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ClawdevCommand.register(dispatcher);
        });

        // Comando principal simplificado: /aiplayer
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            literal("aiplayer")
                // /aiplayer spawn <nome>
                .then(literal("spawn")
                    .then(CommandManager.argument("bot_name", StringArgumentType.string())
                        .executes(context -> {
                            String botName = StringArgumentType.getString(context, "bot_name");
                            spawnBot(context, botName);
                            return 1;
                        })
                    )
                )
                // /aiplayer remove
                .then(literal("remove")
                    .executes(context -> {
                        removeBot(context);
                        return 1;
                    })
                )
        ));
    }

    private static void spawnBot(CommandContext<ServerCommandSource> context, String botName) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getServer();

        if (BotEventHandler.bot != null) {
            source.sendMessage(Text.of("§c[Clawbot] Já existe um bot ativo! Remova-o primeiro com /aiplayer remove"));
            return;
        }

        modCommandRegistry.botName = botName;

        Vec3d playerPos = source.getPosition();
        double yaw = source.getRotation().y;
        double pitch = source.getRotation().x;

        // Spawnar o bot no mundo do jogador
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            RegistryKey<World> dimension = player.getWorld().getRegistryKey();
            createFakePlayer.createFake(botName, server, playerPos, yaw, pitch, dimension, GameMode.SURVIVAL, false);
            source.sendMessage(Text.of("§9[Clawbot] §7Bot §f" + botName + " §7spawnado com sucesso!"));
        }
    }

    private static void removeBot(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getServer();

        if (BotEventHandler.bot == null) {
            source.sendMessage(Text.of("§c[Clawbot] Nenhum bot ativo para remover!"));
            return;
        }

        String removedBotName = BotEventHandler.bot.getName().getString();

        // Desligar autonomia antes de remover
        BotEventHandler.shutdownAutonomy();

        // Remover o bot
        BotEventHandler.bot.kill();
        BotEventHandler.bot = null;

        source.sendMessage(Text.of("§9[Clawbot] §7Bot §f" + removedBotName + " §7removido com sucesso!"));
    }

    /**
     * Move o bot para frente.
     */
    public static void moveForward(MinecraftServer server, ServerCommandSource source, String botName) {
        server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move forward");
    }

    /**
     * Para o movimento do bot.
     */
    public static void stopMoving(MinecraftServer server, ServerCommandSource source, String botName) {
        server.getCommandManager().executeWithPrefix(source, "/player " + botName + " stop");
    }
}
