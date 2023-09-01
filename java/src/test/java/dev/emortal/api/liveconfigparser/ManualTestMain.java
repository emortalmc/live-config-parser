package dev.emortal.api.liveconfigparser;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public class ManualTestMain {

    public static void main(String[] args) {
        try (GameModeCollection collection = GameModeCollection.fromLocalPath(Path.of("testfiles"))) {
            collection.addGlobalUpdateListener(update -> System.out.println("Got update for config file: " + update));
            Collection<GameModeConfig> configs = collection.allConfigs();
            System.out.println("Loaded " + configs.size() + " configs");
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}