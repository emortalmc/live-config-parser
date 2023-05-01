package dev.emortal.api.liveconfigparser.configs;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public abstract class Config {
    private String fileName;

    public @NotNull String getFileName() {
        return fileName;
    }
}
