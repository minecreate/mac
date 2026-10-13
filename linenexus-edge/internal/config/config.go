package config

import (
	"fmt"
	"os"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
)

type Config struct {
	HTTP        HTTPConfig        `yaml:"http"`
	SQLite      SQLiteConfig      `yaml:"sqlite"`
	EdgeGateway EdgeGatewayConfig `yaml:"edge_gateway"`
	Cloud       CloudConfig       `yaml:"cloud"`
	Collect     CollectConfig     `yaml:"collect"`
	Log         LogConfig         `yaml:"log"`

	// 兼容旧配置键 gateway:
	LegacyGateway *EdgeGatewayConfig `yaml:"gateway"`
}

type HTTPConfig struct {
	Addr   string `yaml:"addr"`
	WebDir string `yaml:"web_dir"`
}

type SQLiteConfig struct {
	Path string `yaml:"path"`
}

// EdgeGatewayConfig 边端网关自身身份（不是 Spring Cloud Gateway）。
type EdgeGatewayConfig struct {
	ID       string `yaml:"id"`
	Name     string `yaml:"name"`
	TenantID string `yaml:"tenant_id"`
}

type CloudConfig struct {
	APIBase           string        `yaml:"api_base"`
	RegisterPath      string        `yaml:"register_path"`
	ManagementURL     string        `yaml:"management_url"`
	HeartbeatInterval time.Duration `yaml:"heartbeat_interval"`
}

type CollectConfig struct {
	Interval     time.Duration `yaml:"interval"`
	OfflineAfter time.Duration `yaml:"offline_after"`
}

type LogConfig struct {
	Level string `yaml:"level"`
}

func Load(path string) (*Config, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取配置 %s: %w", path, err)
	}

	cfg := defaultConfig()
	if err := yaml.Unmarshal(raw, cfg); err != nil {
		return nil, fmt.Errorf("解析配置: %w", err)
	}
	if cfg.EdgeGateway.ID == "" && cfg.LegacyGateway != nil {
		cfg.EdgeGateway = *cfg.LegacyGateway
	}
	cfg.LegacyGateway = nil
	if err := cfg.validate(); err != nil {
		return nil, err
	}
	return cfg, nil
}

func defaultConfig() *Config {
	return &Config{
		HTTP: HTTPConfig{Addr: ":8080", WebDir: "./web"},
		SQLite: SQLiteConfig{Path: "./data/edge.db"},
		EdgeGateway: EdgeGatewayConfig{ID: "gw-local-01", Name: "linenexus-edge", TenantID: "tenant-dev"},
		Cloud: CloudConfig{
			APIBase:           "http://127.0.0.1:18080",
			RegisterPath:      "/api/assets/edge-gateways/register",
			ManagementURL:     "http://127.0.0.1:8081",
			HeartbeatInterval: 10 * time.Second,
		},
		Collect: CollectConfig{Interval: 5 * time.Second, OfflineAfter: 15 * time.Second},
		Log:     LogConfig{Level: "info"},
	}
}

func (c *Config) validate() error {
	if c.HTTP.Addr == "" {
		return fmt.Errorf("http.addr 不能为空")
	}
	if c.HTTP.WebDir == "" {
		c.HTTP.WebDir = "./web"
	}
	if c.SQLite.Path == "" {
		return fmt.Errorf("sqlite.path 不能为空")
	}
	if c.EdgeGateway.ID == "" {
		return fmt.Errorf("edge_gateway.id 不能为空")
	}
	if c.EdgeGateway.TenantID == "" {
		c.EdgeGateway.TenantID = "tenant-dev"
	}
	if c.Cloud.APIBase == "" {
		c.Cloud.APIBase = "http://127.0.0.1:18080"
	}
	if c.Cloud.RegisterPath == "" {
		c.Cloud.RegisterPath = "/api/assets/edge-gateways/register"
	}
	// 旧路径兼容
	if c.Cloud.RegisterPath == "/api/assets/gateways/register" {
		c.Cloud.RegisterPath = "/api/assets/edge-gateways/register"
	}
	if c.Cloud.ManagementURL == "" {
		c.Cloud.ManagementURL = deriveManagementURL(c.HTTP.Addr)
	}
	if c.Cloud.HeartbeatInterval <= 0 {
		c.Cloud.HeartbeatInterval = 10 * time.Second
	}
	if c.Collect.Interval <= 0 {
		c.Collect.Interval = 5 * time.Second
	}
	if c.Collect.OfflineAfter <= 0 {
		c.Collect.OfflineAfter = 3 * c.Collect.Interval
	}
	return nil
}

func deriveManagementURL(addr string) string {
	addr = strings.TrimSpace(addr)
	if addr == "" {
		return "http://127.0.0.1:8081"
	}
	if strings.HasPrefix(addr, ":") {
		return "http://127.0.0.1" + addr
	}
	if strings.HasPrefix(addr, "http://") || strings.HasPrefix(addr, "https://") {
		return strings.TrimRight(addr, "/")
	}
	return "http://" + addr
}
