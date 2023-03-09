package liveconfig

import (
	"encoding/json"
	"github.com/fsnotify/fsnotify"
	"go.uber.org/zap"
	"io"
	"log"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"time"
)

type GameModeConfig struct {
	Id              string `json:"id"`
	Enabled         bool   `json:"enabled"`
	FleetName       string `json:"fleetName"`
	ProtocolVersion int    `json:"protocolVersion"`

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

const (
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

type GameModeConfigController interface {

	// GetConfigs returns a copy of the configs. This copy is not
	// updates when a config is created/modified/deleted.
	GetConfigs() map[string]*GameModeConfig

	// GetCurrentConfig returns an up-to-date copy of the config.
	GetCurrentConfig(id string) *GameModeConfig

	// GetCurrentConfigList returns an up-to-date list of all configs.
	GetCurrentConfigList() []*GameModeConfig

	AddConfigUpdateListener(id string, listener func(update ConfigUpdate[GameModeConfig]))
	AddGlobalUpdateListener(listener func(update ConfigUpdate[GameModeConfig]))
}

type gameModeConfigControllerImpl struct {
	GameModeConfigController

	logger *zap.SugaredLogger

	// configs is an internal cache of the configs.
	// it can only be accessed by the controller itself.
	configs map[string]*GameModeConfig

	configUpdateListeners map[string][]func(update ConfigUpdate[GameModeConfig])
	globalUpdateListeners []func(update ConfigUpdate[GameModeConfig])
}

func NewGameModeConfigController(logger *zap.SugaredLogger) (GameModeConfigController, error) {
	configs, err := loadGameModes()
	if err != nil {
		return nil, err
	}

	c := &gameModeConfigControllerImpl{
		logger: logger.Named("liveconfig"),

		configs: configs,

		configUpdateListeners: make(map[string][]func(update ConfigUpdate[GameModeConfig])),
		globalUpdateListeners: make([]func(update ConfigUpdate[GameModeConfig]), 0),
	}

	c.AddGlobalUpdateListener(func(update ConfigUpdate[GameModeConfig]) {
		cfg := update.Config

		switch update.UpdateType {
		case UpdateTypeCreated:
			c.configs[cfg.Id] = cfg
		case UpdateTypeModified:
			oldCfg, ok := c.configs[cfg.Id]
			if ok && oldCfg.Id != cfg.Id {
				c.configs[oldCfg.Id] = nil
			}
			c.configs[cfg.Id] = cfg
		case UpdateTypeDeleted:
			c.configs[cfg.Id] = nil
		}
	})

	err = c.listenForChanges()
	if err != nil {
		return nil, err
	}

	return c, nil
}
func (c *gameModeConfigControllerImpl) GetConfigs() map[string]*GameModeConfig {
	copied := make(map[string]*GameModeConfig)
	for k, original := range c.configs {
		sCopy := *original
		copied[k] = &sCopy
	}
	return copied
}

func (c *gameModeConfigControllerImpl) GetCurrentConfig(id string) *GameModeConfig {
	// copy the config
	original := c.configs[id]
	if original == nil {
		return nil
	}

	copied := *original
	return &copied
}

func (c *gameModeConfigControllerImpl) GetCurrentConfigList() []*GameModeConfig {
	var configs = make([]*GameModeConfig, 0)
	for _, v := range c.configs {
		if v == nil {
			continue
		}
		configs = append(configs, v)
	}
	return configs
}

func (c *gameModeConfigControllerImpl) AddConfigUpdateListener(id string, listener func(update ConfigUpdate[GameModeConfig])) {
	c.configUpdateListeners[id] = append(c.configUpdateListeners[id], listener)
}

func (c *gameModeConfigControllerImpl) AddGlobalUpdateListener(listener func(update ConfigUpdate[GameModeConfig])) {
	c.globalUpdateListeners = append(c.globalUpdateListeners, listener)
}

func loadGameModes() (map[string]*GameModeConfig, error) {
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

func (c *gameModeConfigControllerImpl) listenForChanges() error {
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		return err
	}

	err = watcher.Add("./config/gamemodes")
	if err != nil {
		return err
	}

	go func() {
		for {
			select {
			case event, ok := <-watcher.Events:
				if !ok {
					return
				}
				if event.Name == "" || !strings.HasSuffix(event.Name, ".json") {
					continue
				}
				var updateType UpdateType
				if event.Op&fsnotify.Write == fsnotify.Write {
					updateType = UpdateTypeModified
				} else if event.Op&fsnotify.Create == fsnotify.Create {
					updateType = UpdateTypeCreated
				} else if event.Op&fsnotify.Remove == fsnotify.Remove {
					updateType = UpdateTypeDeleted
				} else {
					continue
				}

				c.logger.Debugw("Game mode config changed", "filePath", event.Name, "updateType", UpdateTypeNames[updateType])
				config, err := readGameMode(event.Name)
				if err != nil {
					c.logger.Errorw("Failed to parse game mode", err)
					continue
				}

				// Verify that there's actually a change. File system events are not always reliable
				// and editors will cause edits to double fire usually
				if updateType == UpdateTypeModified {
					oldConfig, ok := c.configs[config.Id]
					if !ok {
						c.logger.Warnw("failed to find old config", "configId", config.Id)
						continue
					}

					if reflect.DeepEqual(oldConfig, config) {
						c.logger.Debugw("no change in parsed config on system event", "configId", config.Id)
						continue
					}
				}

				update := ConfigUpdate[GameModeConfig]{
					UpdateType: updateType,
					Config:     config,
				}

				for _, listener := range c.globalUpdateListeners {
					listener(update)
				}
				for _, listener := range c.configUpdateListeners[config.Id] {
					listener(update)
				}
			case err, ok := <-watcher.Errors:
				if !ok {
					return
				}
				c.logger.Errorw("Error watching for game mode changes", err)
			}
		}
	}()

	return nil
}

func readGameMode(path string) (*GameModeConfig, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer func(file *os.File) {
		err := file.Close()
		if err != nil {
			log.Printf("Failed to close file: %s", err)
		}
	}(file)

	return parseGameMode(file)
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
