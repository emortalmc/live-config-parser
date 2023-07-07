package dev.emortal.api.liveconfigparser.configs.gamemode;

import dev.emortal.api.liveconfigparser.configs.Config;
import dev.emortal.api.liveconfigparser.configs.common.ConfigItem;
import dev.emortal.api.liveconfigparser.configs.common.ConfigMap;
import dev.emortal.api.liveconfigparser.configs.common.ConfigNPC;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record GameModeConfig(@NotNull String id, boolean enabled, @NotNull String fleetName, int priority, @NotNull String friendlyName,
                             @NotNull String activityNoun, int minPlayers, int maxPlayers, @Nullable ConfigItem displayItem,
                             @Nullable ConfigNPC displayNpc, @NotNull PartyRestrictions partyRestrictions,
                             @Nullable Map<String, ConfigMap> maps, @NotNull MatchmakerInfo matchmakerInfo) implements Config {

    public record PartyRestrictions(int minSize, int maxSize) {
    }

    // rate is in nanoseconds
    public record MatchmakerInfo(@NotNull MatchMethod matchMethod, @NotNull SelectMethod selectMethod, long rate, boolean backfill) {

        public enum MatchMethod {
            INSTANT, COUNTDOWN
        }

        public enum SelectMethod {
            PLAYER_COUNT, AVAILABLE
        }
    }
}
