package dev.emortal.api.liveconfigparser.configs.common;

import lombok.Data;

import java.util.List;

@Data
public class ConfigNPC {
    private String entityType;
    private List<String> titles;
    private ConfigSkin skin;
}
