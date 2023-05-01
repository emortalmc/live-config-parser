package dev.emortal.api.liveconfigparser.configs.gamemode;

import dev.emortal.api.liveconfigparser.configs.Config;
import dev.emortal.api.liveconfigparser.configs.common.ConfigItem;
import dev.emortal.api.liveconfigparser.configs.common.ConfigMap;
import dev.emortal.api.liveconfigparser.configs.common.ConfigNPC;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public final class GameModeConfig extends Config {
    private String id;
    private boolean enabled;
    private String fleetName;
    private int priority;
    private String friendlyName;
    private String activityNoun;
    private int minPlayers;
    private int maxPlayers;
    private @Nullable ConfigItem displayItem;
    private @Nullable ConfigNPC displayNpc;
    private PartyRestrictions partyRestrictions;
    private @Nullable Map<String, ConfigMap> maps;
    private MatchmakerInfo matchmakerInfo;

    @Data
    public static final class PartyRestrictions {
        private int minSize;
        private int maxSize;
    }

    @Data
    public static final class MatchmakerInfo {
        private MatchMethod matchMethod;
        private SelectMethod selectMethod;
        // rate is in nanoseconds
        private long rate;
        private boolean backfill;

        public enum MatchMethod {
            INSTANT, COUNTDOWN
        }

        public enum SelectMethod {
            PLAYER_COUNT, AVAILABLE
        }
    }
}
