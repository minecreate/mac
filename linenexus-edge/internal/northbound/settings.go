package northbound

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"
	"google.golang.org/grpc"
	"google.golang.org/grpc/connectivity"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/credentials/insecure"
)

type Settings struct {
	Enabled      bool   `json:"enabled"`
	Broker       string `json:"broker"`
	ClientID     string `json:"clientId"`
	Username     string `json:"username"`
	Password     string `json:"password,omitempty"`
	TopicPrefix  string `json:"topicPrefix"`
	QoS          int    `json:"qos"`
	CloudAPIBase string `json:"cloudApiBase"`
	GRPCEnabled  bool   `json:"grpcEnabled"`
	GRPCTarget   string `json:"grpcTarget"`
	GRPCTLS      bool   `json:"grpcTls"`
	GRPCToken    string `json:"grpcToken,omitempty"`
}

func DefaultSettings(gatewayID string) Settings {
	if gatewayID == "" {
		gatewayID = "gw-local-01"
	}
	return Settings{
		Enabled:      false,
		Broker:       "tcp://127.0.0.1:1883",
		ClientID:     "linenexus-" + gatewayID,
		TopicPrefix:  "linenexus/" + gatewayID,
		QoS:          0,
		CloudAPIBase: "http://127.0.0.1:18080",
		GRPCEnabled:  false,
		GRPCTarget:   "127.0.0.1:9090",
	}
}

func PathFromSQLite(sqlitePath string) string {
	dir := filepath.Dir(sqlitePath)
	if dir == "" || dir == "." {
		dir = "./data"
	}
	return filepath.Join(dir, "northbound.json")
}

func LoadSettings(path string, gatewayID string) (Settings, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return DefaultSettings(gatewayID), nil
		}
		return Settings{}, err
	}
	s := DefaultSettings(gatewayID)
	if err := json.Unmarshal(raw, &s); err != nil {
		return Settings{}, err
	}
	return s, nil
}

func SaveSettings(path string, s Settings) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	raw, err := json.MarshalIndent(s, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, raw, 0o600)
}

func (s Settings) Redacted() Settings {
	if s.Password != "" {
		s.Password = "******"
	}
	if s.GRPCToken != "" {
		s.GRPCToken = "******"
	}
	return s
}

func (s Settings) Normalize() Settings {
	s.Broker = strings.TrimSpace(s.Broker)
	if s.Broker != "" && !strings.Contains(s.Broker, "://") {
		s.Broker = "tcp://" + s.Broker
	}
	if s.QoS < 0 || s.QoS > 2 {
		s.QoS = 0
	}
	s.ClientID = strings.TrimSpace(s.ClientID)
	s.TopicPrefix = strings.TrimRight(strings.TrimSpace(s.TopicPrefix), "/")
	s.CloudAPIBase = strings.TrimRight(strings.TrimSpace(s.CloudAPIBase), "/")
	s.GRPCTarget = normalizeGRPCTarget(s.GRPCTarget)
	return s
}

func normalizeGRPCTarget(s string) string {
	s = strings.TrimSpace(s)
	for _, p := range []string{"grpc://", "grpcs://", "http://", "https://", "tcp://"} {
		s = strings.TrimPrefix(s, p)
	}
	return s
}

func TestSettings(ctx context.Context, s Settings) error {
	s = s.Normalize()
	if s.Broker == "" {
		return fmt.Errorf("缺少 broker")
	}
	clientID := s.ClientID
	if clientID == "" {
		clientID = "linenexus-test"
	}
	clientID = fmt.Sprintf("%s-test-%d", clientID, time.Now().UnixNano()%1_000_000)

	opts := paho.NewClientOptions()
	opts.AddBroker(s.Broker)
	opts.SetClientID(clientID)
	opts.SetConnectTimeout(8 * time.Second)
	opts.SetAutoReconnect(false)
	opts.SetCleanSession(true)
	if s.Username != "" {
		opts.SetUsername(s.Username)
		opts.SetPassword(s.Password)
	}
	c := paho.NewClient(opts)
	token := c.Connect()
	if !token.WaitTimeout(8 * time.Second) {
		return fmt.Errorf("连接 %s 超时", s.Broker)
	}
	if err := token.Error(); err != nil {
		return fmt.Errorf("连接 %s: %w", s.Broker, err)
	}
	if err := ctx.Err(); err != nil {
		c.Disconnect(100)
		return err
	}
	topic := s.TopicPrefix
	if topic == "" {
		topic = "linenexus/test"
	}
	topic += "/uplink/ping"
	pub := c.Publish(topic, byte(s.QoS), false, `{"ok":true}`)
	if !pub.WaitTimeout(5 * time.Second) {
		c.Disconnect(100)
		return fmt.Errorf("已连接，但发布探测消息超时")
	}
	if err := pub.Error(); err != nil {
		c.Disconnect(100)
		return fmt.Errorf("已连接，但发布失败: %w", err)
	}
	c.Disconnect(200)
	return nil
}

func TestGRPC(ctx context.Context, s Settings) error {
	s = s.Normalize()
	if s.GRPCTarget == "" {
		return fmt.Errorf("缺少 gRPC 地址")
	}
	creds := grpc.WithTransportCredentials(insecure.NewCredentials())
	if s.GRPCTLS {
		creds = grpc.WithTransportCredentials(credentials.NewTLS(nil))
	}
	ctx, cancel := context.WithTimeout(ctx, 6*time.Second)
	defer cancel()
	conn, err := grpc.NewClient(s.GRPCTarget, creds)
	if err != nil {
		return fmt.Errorf("创建 gRPC 客户端: %w", err)
	}
	defer conn.Close()
	conn.Connect()
	state := conn.GetState()
	for state != connectivity.Ready {
		if !conn.WaitForStateChange(ctx, state) {
			return fmt.Errorf("连接 gRPC %s 超时（云控制面还没起来时会这样）", s.GRPCTarget)
		}
		state = conn.GetState()
		if state == connectivity.TransientFailure || state == connectivity.Shutdown {
			return fmt.Errorf("连接 gRPC %s 失败，对端没有控制面服务", s.GRPCTarget)
		}
	}
	return nil
}
