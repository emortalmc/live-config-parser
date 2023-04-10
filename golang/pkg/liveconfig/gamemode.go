package liveconfig

import (
	"encoding/json"
	"fmt"
	"github.com/fsnotify/fsnotify"
	"go.uber.org/zap"
	"io"
	"io/fs"
	"log"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"time"
)

type GameModeConfig struct {
	Id        string `json:"id"`
	Enabled   bool   `json:"enabled"`
	FleetName string `json:"fleetName"`

	// Priority determines the order for things such as the NPC and item display
	// Lower numbers are higher priority
	Priority int `json:"priority"`

	FriendlyName string `json:"friendlyName"`
	ActivityNoun string `json:"activityNoun"`

	MinPlayers int `json:"minPlayers"`
	MaxPlayers int `json:"maxPlayers"`

	// DisplayItem optional
	DisplayItem *ConfigItem `json:"displayItem"`
	// DisplayNpc optional
	DisplayNpc *ConfigNPC `json:"displayNpc"`

	PartyRestrictions *PartyRestrictions `json:"partyRestrictions"`

	Maps map[string]*ConfigMap `json:"maps"`

	MatchmakerInfo *MatchmakerInfo `json:"matchmakerInfo"`
}

type PartyRestrictions struct {
	MinSize int `json:"minSize"`
	// MaxSize optional
	MaxSize *int `json:"maxSize"`
}

type ConfigMap struct {
	Id      string `json:"id"`
	Enabled bool   `json:"enabled"`

	FriendlyName string `json:"friendlyName"`
	Priority     int    `json:"priority"`

	DisplayItem *ConfigItem `json:"displayItem"`
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

	// GetConfigs returns the live configs. It is not safe to modify the returned map.
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

	path string

	// configs is an internal cache of the configs.
	// it can only be accessed by the controller itself.
	configs map[string]*GameModeConfig

	configUpdateListeners map[string][]func(update ConfigUpdate[GameModeConfig])
	globalUpdateListeners []func(update ConfigUpdate[GameModeConfig])
}

func NewGameModeConfigController(logger *zap.SugaredLogger) (GameModeConfigController, error) {
	return NewGameModeConfigControllerWithPath(logger, "./config/gamemodes")
}

func NewGameModeConfigControllerWithPath(logger *zap.SugaredLogger, path string) (GameModeConfigController, error) {
	configs, err := loadGameModes(path)
	if err != nil {
		return nil, err
	}

	c := &gameModeConfigControllerImpl{
		logger: logger.Named("liveconfig"),

		path:    path,
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
		return nil, fmt.Errorf("failed to listen for changes: %w", err)
	}

	return c, nil
}
func (c *gameModeConfigControllerImpl) GetConfigs() map[string]*GameModeConfig {
	return c.configs
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

func loadGameModes(path string) (map[string]*GameModeConfig, error) {
	var configs = make(map[string]*GameModeConfig)

	err := filepath.Walk(path, func(path string, d fs.FileInfo, err error) error {
		if d == nil || d.IsDir() ||
			//strings.Contains(path, "/") || // ignore files in subdirectories, otherwise it'll load that in the original symlink dir
			// Removed for now because it was causing issues when loading subdirs, e.g. ./config/gamemodes/x.json
			strings.HasPrefix(path, ".") || // ignore hidden files
			!strings.HasSuffix(path, ".json") { // ignore non-json files
			return nil
		}

		file, err := os.Open(path)
		if err != nil {
			return fmt.Errorf("failed to open file (path: %s): %v", path, err)
		}
		defer func(file *os.File) {
			err := file.Close()
			if err != nil {
				log.Printf("Failed to close file(%s): %v", path, err)
			}
		}(file)

		config, err := parseGameMode(file)
		if err != nil {
			return fmt.Errorf("failed to parse config (path: %s): %v", path, err)
		}
		configs[config.Id] = config

		return nil
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

	err = watcher.Add(c.path)
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
		return nil, fmt.Errorf("failed to open file: %w", err)
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
		return nil, fmt.Errorf("failed to read file: %w", err)
	}

	err = json.Unmarshal(bytes, &config)

	return &config, err
}
