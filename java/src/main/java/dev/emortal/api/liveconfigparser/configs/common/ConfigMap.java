package dev.emortal.api.liveconfigparser.configs.common;

import lombok.Data;

@Data
public class ConfigMap {
    private String id;
    private boolean enabled;

    private String friendlyName;
    private int priority;

    private ConfigItem displayItem;
}
