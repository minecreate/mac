package northbound

import (
	"context"
	"log/slog"
	"time"
)

type TelemetryPoint struct {
	PointID   string    `json:"pointId"`
	PointName string    `json:"pointName"`
	Value     any       `json:"value"`
	Quality   string    `json:"quality"`
	TS        time.Time `json:"ts"`
}

type TelemetryBatch struct {
	GatewayID  string           `json:"gatewayId"`
	DeviceID   string           `json:"deviceId"`
	DeviceName string           `json:"deviceName"`
	Points     []TelemetryPoint `json:"points"`
}

type Publisher interface {
	PublishTelemetry(ctx context.Context, batch TelemetryBatch) error
	Close() error
}

type LogPublisher struct {
	log *slog.Logger
}

func NewLogPublisher(log *slog.Logger) *LogPublisher {
	return &LogPublisher{log: log}
}

func (p *LogPublisher) PublishTelemetry(_ context.Context, batch TelemetryBatch) error {
	if len(batch.Points) == 0 {
		return nil
	}
	p.log.Debug("northbound telemetry", "gateway", batch.GatewayID, "device", batch.DeviceID, "n", len(batch.Points))
	return nil
}

func (p *LogPublisher) Close() error { return nil }
