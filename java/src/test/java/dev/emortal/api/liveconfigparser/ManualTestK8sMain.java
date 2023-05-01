package dev.emortal.api.liveconfigparser;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.util.List;

public class ManualTestK8sMain {
    public static void main(String[] args) throws IOException, ApiException {
        ApiClient apiClient = Config.defaultClient();

        final GameModeCollection collection = new GameModeCollection(apiClient, "emortalmc", "gamemodes");
        final List<GameModeConfig> configs = collection.getAllConfigs(update -> {
            System.out.println("Updated: " + update.getConfig().getId() + " (type: " + update.getType() + ")");
        });
        System.out.println("Loaded " + configs.size() + " configs");
    }
}
