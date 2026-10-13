package southbound

import (
	"context"
	"errors"
	"time"
)

var (
	ErrNotImplemented = errors.New("适配器尚未实现")
	ErrUnknownProtocol = errors.New("未知协议")
)

type Connection map[string]any

type PointRef struct {
	ID      string
	Address map[string]any
}

type Sample struct {
	PointID string
	Value   any
	Quality string
	TS      time.Time
}

type WriteReq struct {
	PointID string
	Address map[string]any
	Value   any
}

type WriteAck struct {
	PointID string
	OK      bool
	Err     string
}

type BrowseNode struct {
	NodeID  string         `json:"nodeId"`
	Name    string         `json:"name"`
	Path    string         `json:"path"`
	Class   string         `json:"class"`
	Value   string         `json:"value,omitempty"`
	Address map[string]any `json:"address,omitempty"`
}

type Adapter interface {
	Protocol() string
	Connect(ctx context.Context, conn Connection) error
	Read(ctx context.Context, points []PointRef) ([]Sample, error)
	Write(ctx context.Context, writes []WriteReq) ([]WriteAck, error)
	Close() error
}

type Browser interface {
	Browse(ctx context.Context, start string, max int) ([]BrowseNode, error)
}
