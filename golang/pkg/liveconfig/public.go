package liveconfig

type UpdateType uint8

const (
	UpdateTypeCreated UpdateType = iota
	UpdateTypeDeleted
	UpdateTypeModified
)

var UpdateTypeNames = map[UpdateType]string{
	UpdateTypeCreated:  "created",
	UpdateTypeDeleted:  "deleted",
	UpdateTypeModified: "modified",
}

type ConfigUpdate[T any] struct {
	Config     *T
	UpdateType UpdateType
}
