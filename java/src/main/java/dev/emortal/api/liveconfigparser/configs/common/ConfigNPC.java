package dev.emortal.api.liveconfigparser.configs.common;

import java.util.List;
import org.jetbrains.annotations.NotNull;

public record ConfigNPC(@NotNull String entityType, @NotNull List<String> titles, @NotNull ConfigSkin skin) {
}
