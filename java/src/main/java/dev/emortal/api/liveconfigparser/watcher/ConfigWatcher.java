package dev.emortal.api.liveconfigparser.watcher;

import java.io.IOException;

public interface ConfigWatcher extends AutoCloseable {

    @Override
    void close() throws IOException;
}
