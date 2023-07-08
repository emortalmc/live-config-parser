package dev.emortal.api.liveconfigparser.configs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

/**
 * Only <b>one</b> of the oldConfig and newConfig fields will be null, depending on the type of update.
 *
 * <ul>
 *     <li>CREATE: oldConfig will be <b>null</b> and newConfig will be <b>not null</b>.</li>
 *     <li>REMOVE: oldConfig will be <b>not null</b> and newConfig will be <b>null</b>.</li>
 *     <li>MODIFY: both oldConfig and newConfig will be <b>not null</b>.</li>
 * </ul>
 */
public record ConfigUpdate<T extends Config>(@UnknownNullability T oldConfig, @UnknownNullability T newConfig, @NotNull Type type) {

    public enum Type {
        CREATE,
        MODIFY,
        DELETE
    }
}
