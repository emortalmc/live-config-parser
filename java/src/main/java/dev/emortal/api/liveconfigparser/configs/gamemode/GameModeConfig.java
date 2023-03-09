package dev.emortal.api.liveconfigparser.configs.gamemode;

import dev.emortal.api.liveconfigparser.configs.common.ConfigItem;
import dev.emortal.api.liveconfigparser.configs.common.ConfigMap;
import dev.emortal.api.liveconfigparser.configs.common.ConfigNPC;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Data
public final class GameModeConfig {
    private String id;
    private boolean enabled;
    private String fleetName;
    private int protocolVersion;
    private String friendlyName;
    private String activityNoun;
    private int minPlayers;
    private int maxPlayers;
    private ConfigItem displayItem;
    private ConfigNPC displayNpc;
    private int npcIndex;
    private PartyRestrictions partyRestrictions;
    private @Nullable TeamInfo teamInfo;
    private Map<String, ConfigMap> maps;
    private MatchmakerInfo matchmakerInfo;

    @Data
    public static final class PartyRestrictions {
        private boolean allowParties;
        private int minSize;
        private int maxSize;
    }

    @Data
    public static final class TeamInfo {
        private int teamSize;
        private int teamCount;
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
