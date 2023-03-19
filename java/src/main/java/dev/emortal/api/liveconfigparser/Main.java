package dev.emortal.api.liveconfigparser;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        final GameModeCollection collection = new GameModeCollection();
        final List<GameModeConfig> configs = collection.getAllConfigs(update -> {
            System.out.println("Updated: " + update.getConfigId() + " (type: " + update.getType() + ")");
        });
        System.out.println("Loaded " + configs.size() + " configs");
    }
}