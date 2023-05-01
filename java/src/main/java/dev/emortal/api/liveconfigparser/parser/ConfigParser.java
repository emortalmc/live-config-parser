package dev.emortal.api.liveconfigparser.parser;

import org.jetbrains.annotations.NotNull;

public interface ConfigParser<T> {

    @NotNull T parse(@NotNull String config) throws ConfigParseException;
}
