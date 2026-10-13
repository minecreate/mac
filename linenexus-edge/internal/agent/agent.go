package agent

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"linenexus-edge/internal/cache"
	"linenexus-edge/internal/config"
	"linenexus-edge/internal/mapping"
	"linenexus-edge/internal/northbound"
	"linenexus-edge/internal/southbound"
	"linenexus-edge/internal/storage/model"
	"linenexus-edge/internal/storage/repo"
)

type Agent struct {
	cfg     *config.Config
	store   *repo.Store
	reg     *southbound.Registry
	outbox  *cache.Outbox
	pub     northbound.Publisher
	log     *slog.Logger
}

func New(cfg *config.Config, store *repo.Store, reg *southbound.Registry, outbox *cache.Outbox, pub northbound.Publisher, log *slog.Logger) *Agent {
	return &Agent{cfg: cfg, store: store, reg: reg, outbox: outbox, pub: pub, log: log}
}

func (a *Agent) Run(ctx context.Context) {
	ticker := time.NewTicker(a.cfg.Collect.Interval)
	defer ticker.Stop()
	hb := time.NewTicker(a.cfg.Cloud.HeartbeatInterval)
	defer hb.Stop()

	a.log.Info("agent 启动", "interval", a.cfg.Collect.Interval, "cloud", a.cfg.Cloud.APIBase)
	a.tick(ctx)
	a.registerCloud(ctx)

	for {
		select {
		case <-ctx.Done():
			a.log.Info("agent 停止")
			return
		case <-ticker.C:
			a.tick(ctx)
		case <-hb.C:
			a.registerCloud(ctx)
		}
	}
}

func (a *Agent) registerCloud(ctx context.Context) {
	cloud := a.cfg.Cloud
	if s, err := northbound.LoadSettings(northbound.PathFromSQLite(a.cfg.SQLite.Path), a.cfg.EdgeGateway.ID); err == nil {
		s = s.Normalize()
		if s.CloudAPIBase != "" {
			cloud.APIBase = s.CloudAPIBase
		}
	}
	if cloud.APIBase == "" {
		return
	}
	if err := northbound.RegisterToCloud(ctx, cloud, a.cfg.EdgeGateway); err != nil {
		a.log.Warn("云注册/心跳失败", "err", err)
		return
	}
	a.log.Debug("已向云注册", "api", cloud.APIBase, "edgeGateway", a.cfg.EdgeGateway.ID)
}

func (a *Agent) tick(ctx context.Context) {
	devices, err := a.store.EnabledDevicesWithPoints(ctx, a.cfg.EdgeGateway.ID)
	if err != nil {
		a.log.Error("加载设备失败", "err", err)
		return
	}
	for _, d := range devices {
		if len(d.Points) == 0 {
			continue
		}
		if err := a.collectDevice(ctx, d.ID, mapping.ProtocolOf(d)); err != nil && !errors.Is(err, southbound.ErrNotImplemented) {
			a.log.Warn("采集失败", "device", d.ID, "protocol", d.Protocol, "err", err)
		}
	}
}

func (a *Agent) collectDevice(ctx context.Context, deviceID, protocol string) error {
	adapter, err := a.reg.New(protocol)
	if err != nil {
		return err
	}
	defer adapter.Close()

	dev, err := a.store.GetDevice(ctx, deviceID)
	if err != nil {
		return err
	}
	conn, err := mapping.DeviceConnection(*dev)
	if err != nil {
		return err
	}
	if err := adapter.Connect(ctx, conn); err != nil {
		return err
	}
	refs, err := mapping.PointRefs(dev.Points)
	if err != nil {
		return err
	}
	samples, err := adapter.Read(ctx, refs)
	if err != nil {
		return err
	}
	if err := a.outbox.Append(ctx, deviceID, samples); err != nil {
		return err
	}
	_ = a.store.TouchHeartbeat(ctx, deviceID)
	names := map[string]string{}
	for _, p := range dev.Points {
		names[p.ID] = p.Name
	}
	for _, s := range samples {
		_ = a.store.AppendMessage(ctx, &model.Message{
			DeviceID:   dev.ID,
			DeviceName: dev.Name,
			PointID:    s.PointID,
			PointName:  names[s.PointID],
			Direction:  model.DirRx,
			Value:      fmt.Sprint(s.Value),
			Status:     model.MsgOK,
		})
	}
	batch := northbound.TelemetryBatch{
		GatewayID:  a.cfg.EdgeGateway.ID,
		DeviceID:   dev.ID,
		DeviceName: dev.Name,
		Points:     make([]northbound.TelemetryPoint, 0, len(samples)),
	}
	for _, s := range samples {
		batch.Points = append(batch.Points, northbound.TelemetryPoint{
			PointID:   s.PointID,
			PointName: names[s.PointID],
			Value:     s.Value,
			Quality:   s.Quality,
			TS:        s.TS,
		})
	}
	return a.pub.PublishTelemetry(ctx, batch)
}
