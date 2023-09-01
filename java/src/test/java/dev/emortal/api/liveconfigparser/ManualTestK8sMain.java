package dev.emortal.api.liveconfigparser;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.util.Collection;

public final class ManualTestK8sMain {

    public static void main(String[] args) throws IOException {
        ApiClient apiClient = Config.defaultClient();

        try (GameModeCollection collection = GameModeCollection.fromKubernetes(apiClient, "emortalmc", "gamemodes")) {
            collection.addGlobalUpdateListener(update -> System.out.println("Got update for config file: " + update));

            Collection<GameModeConfig> configs = collection.allConfigs();
            System.out.println("Loaded " + configs.size() + " configs");

            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}
