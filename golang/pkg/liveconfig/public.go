package liveconfig

type UpdateType uint8

const (
	UpdateTypeCreated UpdateType = iota
	UpdateTypeDeleted
	UpdateTypeModified
)

type ConfigUpdate[T any] struct {
	Config     *T
	UpdateType UpdateType
}
