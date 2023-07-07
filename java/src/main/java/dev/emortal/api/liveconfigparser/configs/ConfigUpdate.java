package dev.emortal.api.liveconfigparser.configs;

import org.jetbrains.annotations.NotNull;

public record ConfigUpdate<T extends Config>(@NotNull T config, @NotNull Type type) {

    public enum Type {
        CREATE,
        MODIFY,
        DELETE
    }
}
