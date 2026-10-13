package docker

import (
	"context"
	"errors"
)

var ErrNotImplemented = errors.New("docker 编排器尚未实现")

// Orchestrator 按 Lab 规格拉起 / 销毁仿真容器。一期仅占位。
type Orchestrator struct{}

func New() *Orchestrator { return &Orchestrator{} }

func (o *Orchestrator) Available() bool { return false }

func (o *Orchestrator) CreateLab(_ context.Context, _ string) error {
	return ErrNotImplemented
}

func (o *Orchestrator) DestroyLab(_ context.Context, _ string) error {
	return ErrNotImplemented
}
