package dev.emortal.api.liveconfigparser.configs.gamemode;

import dev.emortal.api.liveconfigparser.configs.common.ConfigItem;
import dev.emortal.api.liveconfigparser.configs.common.ConfigMap;
import dev.emortal.api.liveconfigparser.configs.common.ConfigNPC;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;

@Data
public final class GameModeConfig {
    private Path path;
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
    private @Nullable TeamInfo teamInfo;
    private @Nullable Map<String, ConfigMap> maps;
    private MatchmakerInfo matchmakerInfo;

    @Data
    public static final class PartyRestrictions {
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
