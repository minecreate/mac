package storage

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/glebarez/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"linenexus-edge/internal/config"
	"linenexus-edge/internal/storage/model"
)

// Open 使用纯 Go SQLite（modernc），不依赖 CGO / 动态库。
func Open(cfg config.SQLiteConfig) (*gorm.DB, error) {
	if err := os.MkdirAll(filepath.Dir(cfg.Path), 0o755); err != nil {
		return nil, fmt.Errorf("创建数据目录: %w", err)
	}

	db, err := gorm.Open(sqlite.Open(cfg.Path), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Warn),
	})
	if err != nil {
		return nil, fmt.Errorf("打开 sqlite: %w", err)
	}

	sqlDB, err := db.DB()
	if err != nil {
		return nil, err
	}
	sqlDB.SetMaxOpenConns(1)

	pragmas := []string{
		"PRAGMA foreign_keys = ON",
		"PRAGMA busy_timeout = 5000",
		"PRAGMA journal_mode = WAL",
	}
	for _, p := range pragmas {
		if err := db.Exec(p).Error; err != nil {
			return nil, fmt.Errorf("执行 %s: %w", p, err)
		}
	}

	if err := db.AutoMigrate(
		&model.EdgeGateway{},
		&model.Device{},
		&model.Point{},
		&model.TelemetryOutbox{},
		&model.Message{},
	); err != nil {
		return nil, fmt.Errorf("migrate: %w", err)
	}

	return db, nil
}
