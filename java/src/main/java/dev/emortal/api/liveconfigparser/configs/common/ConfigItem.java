package dev.emortal.api.liveconfigparser.configs.common;

import java.util.List;
import org.jetbrains.annotations.NotNull;

public record ConfigItem(@NotNull String material, int slot, @NotNull String name, @NotNull List<String> lore) {
}
