package dev.emortal.api.liveconfigparser;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;

import java.io.IOException;

public final class ManualTestK8sMain {
    public static void main(String[] args) throws IOException {
        ApiClient apiClient = Config.defaultClient();

        try (var collection = new GameModeCollection(apiClient, "emortalmc", "gamemodes")) {
            var configs = collection.getAllConfigs(update -> System.out.println("Updated: " + update.config().id() + " (type: " + update.type() + ")"));
            System.out.println("Loaded " + configs.size() + " configs");

            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
