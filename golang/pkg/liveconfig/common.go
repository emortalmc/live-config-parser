package liveconfig

type ConfigItem struct {
	Material string `json:"material"`
	Slot     int    `json:"slot"`

	Name string   `json:"name"`
	Lore []string `json:"lore"`
}

type ConfigNPC struct {
	EntityType string     `json:"entityType"`
	Titles     []string   `json:"titles"`
	Skin       ConfigSkin `json:"skin"`
}

type ConfigSkin struct {
	Texture   string `json:"texture"`
	Signature string `json:"signature"`
}
