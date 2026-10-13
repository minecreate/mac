package app

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"linenexus-edge/internal/agent"
	"linenexus-edge/internal/cache"
	"linenexus-edge/internal/config"
	"linenexus-edge/internal/httpapi"
	"linenexus-edge/internal/northbound"
	"linenexus-edge/internal/southbound"
	"linenexus-edge/internal/southbound/modbus"
	"linenexus-edge/internal/southbound/mqtt"
	"linenexus-edge/internal/southbound/opcua"
	"linenexus-edge/internal/storage"
	"linenexus-edge/internal/storage/model"
	"linenexus-edge/internal/storage/repo"
)

func Run(cfgPath string) error {
	cfg, err := config.Load(cfgPath)
	if err != nil {
		return err
	}

	log := newLogger(cfg.Log.Level)
	slog.SetDefault(log)

	db, err := storage.Open(cfg.SQLite)
	if err != nil {
		return err
	}
	sqlDB, err := db.DB()
	if err != nil {
		return err
	}
	defer sqlDB.Close()

	store := repo.New(db)
	if _, err := store.EnsureEdgeGateway(context.Background(), cfg.EdgeGateway); err != nil {
		return err
	}

	reg := southbound.NewRegistry()
	reg.Register(model.ProtocolOPCUA, opcua.New)
	reg.Register(model.ProtocolCODESYS, opcua.New)
	reg.Register(model.ProtocolModbus, modbus.New)
	reg.Register(model.ProtocolMQTT, mqtt.New)

	outbox := cache.NewOutbox(store)
	bus := northbound.NewBus()
	if s, err := northbound.LoadSettings(northbound.PathFromSQLite(cfg.SQLite.Path), cfg.EdgeGateway.ID); err == nil {
		if err := bus.Apply(s); err != nil {
			log.Warn("北向 MQTT 未连上", "err", err)
		}
	}
	ag := agent.New(cfg, store, reg, outbox, bus, log)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go ag.Run(ctx)

	srv := &http.Server{
		Addr:              cfg.HTTP.Addr,
		Handler:           httpapi.NewRouter(cfg, store, reg, bus),
		ReadHeaderTimeout: 5 * time.Second,
	}

	errCh := make(chan error, 1)
	go func() {
		log.Info("HTTP 监听", "addr", cfg.HTTP.Addr, "web", cfg.HTTP.WebDir)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			errCh <- err
		}
		close(errCh)
	}()

	select {
	case <-ctx.Done():
	case err := <-errCh:
		if err != nil {
			return err
		}
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	_ = bus.Close()
	return srv.Shutdown(shutdownCtx)
}

func newLogger(level string) *slog.Logger {
	var lv slog.Level
	switch strings.ToLower(level) {
	case "debug":
		lv = slog.LevelDebug
	case "warn":
		lv = slog.LevelWarn
	case "error":
		lv = slog.LevelError
	default:
		lv = slog.LevelInfo
	}
	return slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: lv}))
}
