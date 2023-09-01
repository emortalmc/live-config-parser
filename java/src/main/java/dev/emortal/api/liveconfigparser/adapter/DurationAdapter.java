package dev.emortal.api.liveconfigparser.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;

public final class DurationAdapter extends TypeAdapter<Duration> {

    @Override
    public @Nullable Duration read(@NotNull JsonReader in) throws IOException {
        String duration = in.nextString();
        if (duration.isEmpty()) return null;
        return Duration.parse(duration);
    }

    @Override
    public void write(@NotNull JsonWriter out, @NotNull Duration value) throws IOException {
        out.value(value.toString());
    }
}
