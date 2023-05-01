package dev.emortal.api.liveconfigparser.configs;

import org.jetbrains.annotations.NotNull;

public class ConfigUpdate<T extends Config> {
    private final @NotNull String fileName;
    private final @NotNull T config;

    private final @NotNull Type type;

    public ConfigUpdate(@NotNull String fileName, @NotNull T config, @NotNull Type type) {
        this.fileName = fileName;
        this.config = config;
        this.type = type;
    }

    public enum Type {
        CREATE,
        MODIFY,
        DELETE
    }

    public @NotNull String getFileName() {
        return this.fileName;
    }

    public @NotNull T getConfig() {
        return this.config;
    }

    public @NotNull Type getType() {
        return this.type;
    }
}
