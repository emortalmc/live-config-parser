package dev.emortal.api.liveconfigparser.adapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.time.Duration;

public class DurationAdapter implements JsonDeserializer<Duration> {

    @Override
    public Duration deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (!json.isJsonPrimitive()) throw new JsonParseException("Duration must be a string");

        String duration = json.getAsString();
        if (duration.isEmpty()) return null;
        return Duration.parse(duration);
    }
}
