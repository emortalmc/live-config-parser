package dev.emortal.api.liveconfigparser.configs.common;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ConfigItem(@NotNull String material, int slot, @NotNull String name, @NotNull List<String> lore) {
}
