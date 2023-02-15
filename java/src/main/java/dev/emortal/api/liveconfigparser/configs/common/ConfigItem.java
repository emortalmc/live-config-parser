package dev.emortal.api.liveconfigparser.configs.common;

import lombok.Data;

import java.util.List;

@Data
public class ConfigItem {
    private String material;
    private int slot;
    private String name;
    private List<String> lore;
}
