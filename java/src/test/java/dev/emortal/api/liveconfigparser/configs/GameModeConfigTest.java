package dev.emortal.api.liveconfigparser.configs;

import com.google.gson.Gson;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class GameModeConfigTest {
    private static final Path TEST_FILES_PATH = Path.of("../testfiles");
    private static final Gson GSON = new Gson();

    @Test
    public void testLoading() throws IOException {
        try (Stream<Path> fileStream = Files.list(TEST_FILES_PATH)) {
            fileStream.forEach(path -> {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    GameModeConfig config = GSON.fromJson(reader, GameModeConfig.class);
                    config.setFileName(path.getFileName().toString());

                    System.out.println("Config (" + config.getId() + "): " + config);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    /**
     * @return The path to the temporary directory
     */
    private Path saveResourcesToTempDir() {
        return null; // TODO
    }
}
