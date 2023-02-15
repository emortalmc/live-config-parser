package liveconfig

import (
	"encoding/json"
	"io"
	"log"
	"os"
	"path/filepath"
	"time"
)

type GameModeConfig struct {
	Id      string `json:"id"`
	Enabled bool   `json:"enabled"`

	FriendlyName string `json:"friendlyName"`
	ActivityNoun string `json:"activityNoun"`

	MinPlayers int `json:"minPlayers"`
	MaxPlayers int `json:"maxPlayers"`

	DisplayItem ConfigItem `json:"displayItem"`
	DisplayNpc  ConfigNPC  `json:"displayNpc"`
	NpcIndex    int        `json:"npcIndex"`

	PartyRestrictions PartyRestrictions `json:"partyRestrictions"`
	TeamInfo          TeamInfo          `json:"teamInfo"`

	Maps []ConfigMap `json:"maps"`

	MatchmakerInfo MatchmakerInfo `json:"matchmakerInfo"`
	KubernetesInfo KubernetesInfo `json:"kubernetesInfo"`
}

type PartyRestrictions struct {
	AllowParties bool `json:"allowParties"`
	MinSize      int  `json:"minSize"`
	MaxSize      int  `json:"maxSize"`
}

type TeamInfo struct {
	TeamSize  int `json:"teamSize"`
	TeamCount int `json:"teamCount"`
}

type ConfigMap struct {
	Id      string `json:"id"`
	Enabled bool   `json:"enabled"`

	FriendlyName   string            `json:"friendlyName"`
	DisplayItem    ConfigItem        `json:"displayItem"`
	MatchmakerInfo MapMatchmakerInfo `json:"matchmakerInfo"`
}

type MapMatchmakerInfo struct {
	Chance float32 `json:"chance"`
}

type MatchMethod string
type SelectMethod string

var (
	MatchMethodInstant   MatchMethod = "INSTANT"
	MatchMethodCountdown MatchMethod = "COUNTDOWN"

	SelectMethodPlayerCount SelectMethod = "PLAYER_COUNT"
	SelectMethodAvailable   SelectMethod = "AVAILABLE"
)

type MatchmakerInfo struct {
	MatchMethod  MatchMethod  `json:"matchMethod"`
	SelectMethod SelectMethod `json:"selectMethod"`

	Rate     time.Duration `json:"rate"`
	Backfill bool          `json:"backfill"`
}

type KubernetesInfo struct {
	FleetName string `json:"fleetName"`
}

func LoadGameModes() (map[string]*GameModeConfig, error) {
	var configs = make(map[string]*GameModeConfig)

	err := filepath.Walk("./config/gamemodes", func(path string, info os.FileInfo, err error) error {
		if info.IsDir() {
			return nil
		}

		file, err := os.Open(path)
		if err != nil {
			return err
		}
		defer func(file *os.File) {
			err := file.Close()
			if err != nil {
				log.Printf("Failed to close file: %s", err)
			}
		}(file)

		config, err := parseGameMode(file)
		configs[config.Id] = config

		return err
	})
	if err != nil {
		return nil, err
	}

	return configs, nil
}

func parseGameMode(reader io.Reader) (*GameModeConfig, error) {
	var config GameModeConfig

	bytes, err := io.ReadAll(reader)
	if err != nil {
		return nil, err
	}

	err = json.Unmarshal(bytes, &config)

	return &config, err
}
