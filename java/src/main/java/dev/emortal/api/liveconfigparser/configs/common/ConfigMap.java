package dev.emortal.api.liveconfigparser.configs.common;

import org.jetbrains.annotations.NotNull;

public record ConfigMap(@NotNull String id, boolean enabled, @NotNull String friendlyName, int priority, @NotNull ConfigItem displayItem) {
}
