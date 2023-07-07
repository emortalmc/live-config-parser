package dev.emortal.api.liveconfigparser;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import io.kubernetes.client.openapi.ApiException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ManualTestMain {
    public static void main(String[] args) throws IOException {
        final GameModeCollection collection = new GameModeCollection(Path.of("testfiles"));
        final List<GameModeConfig> configs = collection.getAllConfigs(update -> {
            System.out.println("Updated: " + update.getConfig().getId() + " (type: " + update.getType() + ")");
        });
        System.out.println("Loaded " + configs.size() + " configs");
    }
}