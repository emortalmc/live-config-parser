package dev.emortal.api.liveconfigparser;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;

import java.nio.file.Path;

public class ManualTestMain {
    public static void main(String[] args) {
        try (var collection = new GameModeCollection(Path.of("testfiles"))) {
            var configs = collection.getAllConfigs(update -> System.out.println("Updated: " + update.newConfig().id() + " (type: " + update.type() + ")"));
            System.out.println("Loaded " + configs.size() + " configs");
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}