package liveconfig

import (
	"encoding/json"
	"github.com/fsnotify/fsnotify"
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

type GameModeConfigController struct {
	Configs map[string]*GameModeConfig

	configUpdateListeners map[string][]func(update ConfigUpdate[GameModeConfig])
	globalUpdateListeners []func(update ConfigUpdate[GameModeConfig])
}

func NewGameModeConfigController() (*GameModeConfigController, error) {
	configs, err := loadGameModes()
	if err != nil {
		return nil, err
	}

	controller := &GameModeConfigController{
		Configs: configs,

		configUpdateListeners: make(map[string][]func(update ConfigUpdate[GameModeConfig])),
		globalUpdateListeners: make([]func(update ConfigUpdate[GameModeConfig]), 0),
	}

	err = controller.listenForChanges()
	if err != nil {
		return nil, err
	}

	return controller, nil
}

func (c *GameModeConfigController) AddConfigUpdateListener(id string, listener func(update ConfigUpdate[GameModeConfig])) {
	c.configUpdateListeners[id] = append(c.configUpdateListeners[id], listener)
}

func (c *GameModeConfigController) AddGlobalUpdateListener(listener func(update ConfigUpdate[GameModeConfig])) {
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

func (c *GameModeConfigController) listenForChanges() error {
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
				if event.Has(fsnotify.Write) {
					config, err := readGameMode(event.Name)
					if err != nil {
						log.Printf("Failed to parse game mode: %s", err)
						continue
					}

					c.modifyConfig(config)
				} else if event.Has(fsnotify.Create) {
					config, err := readGameMode(event.Name)
					if err != nil {
						log.Printf("Failed to parse game mode: %s", err)
						continue
					}

					c.createConfig(config)
				} else if event.Has(fsnotify.Remove) {
					config, err := readGameMode(event.Name)
					if err != nil {
						log.Printf("Failed to parse game mode: %s", err)
						continue
					}

					c.deleteConfig(config)
				}
			case err, ok := <-watcher.Errors:
				if !ok {
					return
				}
				log.Printf("Error watching for game mode changes: %s", err)
				// TODO handle?

			}
		}
	}()

	return nil
}

func (c *GameModeConfigController) createConfig(config *GameModeConfig) {
	update := ConfigUpdate[GameModeConfig]{
		Config:     config,
		UpdateType: UpdateTypeCreated,
	}

	c.Configs[config.Id] = config
	for _, listener := range c.globalUpdateListeners {
		listener(update)
	}
}

func (c *GameModeConfigController) deleteConfig(config *GameModeConfig) {
	update := ConfigUpdate[GameModeConfig]{
		Config:     config,
		UpdateType: UpdateTypeDeleted,
	}

	c.Configs[config.Id] = nil
	for _, listener := range c.globalUpdateListeners {
		listener(update)
	}
	for _, listener := range c.configUpdateListeners[config.Id] {
		listener(update)
	}
}

func (c *GameModeConfigController) modifyConfig(config *GameModeConfig) {
	update := ConfigUpdate[GameModeConfig]{
		Config:     config,
		UpdateType: UpdateTypeModified,
	}

	c.Configs[config.Id] = config
	for _, listener := range c.globalUpdateListeners {
		listener(update)
	}
	for _, listener := range c.configUpdateListeners[config.Id] {
		listener(update)
	}
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
