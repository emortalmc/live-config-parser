package dev.emortal.api.liveconfigparser.configs;

import com.google.gson.Gson;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class GameModeConfigTest {
    private static final Path TEST_FILES_PATH = Path.of("../testfiles");
    private static final Gson GSON = new Gson();

    @Test
    public void testLoading() throws IOException {
        try (Stream<Path> fileStream = Files.list(TEST_FILES_PATH)) {
            fileStream.filter(Files::isRegularFile)
                    .filter(path -> path.toString()
                            .endsWith(".json"))
                    .forEach(path -> {
                        try (BufferedReader reader = Files.newBufferedReader(path)) {
                            GameModeConfig config = GSON.fromJson(reader, GameModeConfig.class);
                            System.out.println("Config (" + config.id() + "): " + config);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        }
    }
}
