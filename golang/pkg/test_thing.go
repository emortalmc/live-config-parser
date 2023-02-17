package main

import (
	"github.com/emortalmc/live-config-parser/golang/pkg/liveconfig"
	"go.uber.org/zap"
	"log"
)

func main() {
	unsugared, err := zap.NewDevelopment()
	if err != nil {
		log.Fatalf("Failed to create logger: %s", err)
	}
	logger := unsugared.Sugar()
	defer func(logger *zap.SugaredLogger) {
		err := logger.Sync()
		if err != nil {
			log.Fatal(err)
		}
	}(logger)

	configController, err := liveconfig.NewGameModeConfigController(logger)
	if err != nil {
		logger.Errorw("Failed to create config controller", err)
	}

	configController.AddGlobalUpdateListener(func(update liveconfig.ConfigUpdate[liveconfig.GameModeConfig]) {
		logger.Infow("Global update", "type", liveconfig.UpdateTypeNames[update.UpdateType])
	})

	configs := configController.GetConfigs()
	logger.Infow("Initial Configs", "count", len(configs), "values", configs)

	// Wait forever
	select {}
}
