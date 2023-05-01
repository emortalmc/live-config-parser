package dev.emortal.api.liveconfigparser.parser;

import org.jetbrains.annotations.NotNull;

public class ConfigParseException extends Exception {

    public ConfigParseException(@NotNull String fileName, @NotNull String fileContent) {
        super("Failed to parse config file " + fileName + ":\n" + fileContent);
    }
}
