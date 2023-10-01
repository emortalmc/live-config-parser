package dev.emortal.api.liveconfigparser.adapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class NullListToEmptyFactory implements TypeAdapterFactory {
    public static final NullListToEmptyFactory INSTANCE = new NullListToEmptyFactory();

    private NullListToEmptyFactory() {
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<?> rawType = type.getRawType();

        // Only handle List and ArrayList; let other factories handle different types
        if (rawType != List.class && rawType != ArrayList.class) {
            return null;
        }

        // Delegate which handles deserialization of non-null values, and serialization
        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();

                    // Safe due to check at beginning of `create` method
                    @SuppressWarnings("unchecked")
                    T t = (T) new ArrayList<>();
                    return t;
                } else {
                    return delegate.read(in);
                }
            }
        };
    }
}
