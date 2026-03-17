package net.luanvha2550_hash.PlayerUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;


public class turnTool {
    public static void turn(ServerCommandSource botSource, String direction) {

        MinecraftServer server = botSource.getServer();
        String botName = botSource.getName();
        server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " turn " + direction);

    }
}
