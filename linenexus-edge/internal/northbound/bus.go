package northbound

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"sync"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"
)

type Traffic struct {
	Time      time.Time `json:"time"`
	Direction string    `json:"direction"`
	Topic     string    `json:"topic"`
	Payload   string    `json:"payload"`
}

type Bus struct {
	mu      sync.Mutex
	client  paho.Client
	cfg     Settings
	online  bool
	err     string
	traffic []Traffic
}

func NewBus() *Bus {
	return &Bus{traffic: make([]Traffic, 0, 80)}
}

func (b *Bus) Topics() (telemetry, command, ack string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	prefix := b.cfg.TopicPrefix
	if prefix == "" {
		prefix = "linenexus/gw-local-01"
	}
	return prefix + "/telemetry", prefix + "/command", prefix + "/ack"
}

func (b *Bus) Status() (online bool, err string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.online && b.client != nil && b.client.IsConnected(), b.err
}

func (b *Bus) Messages(limit int) []Traffic {
	b.mu.Lock()
	defer b.mu.Unlock()
	if limit <= 0 || limit > len(b.traffic) {
		limit = len(b.traffic)
	}
	out := make([]Traffic, limit)
	copy(out, b.traffic[:limit])
	return out
}

func (b *Bus) Apply(s Settings) error {
	s = s.Normalize()
	b.Close()
	b.mu.Lock()
	b.cfg = s
	b.err = ""
	b.mu.Unlock()
	if !s.Enabled {
		return nil
	}
	return b.connect()
}

func (b *Bus) connect() error {
	paho.ERROR = log.New(io.Discard, "", 0)
	paho.CRITICAL = log.New(io.Discard, "", 0)
	paho.WARN = log.New(io.Discard, "", 0)
	b.mu.Lock()
	s := b.cfg
	b.mu.Unlock()
	if s.Broker == "" {
		return fmt.Errorf("缺少 broker")
	}
	clientID := s.ClientID
	if clientID == "" {
		clientID = "linenexus-edge"
	}
	opts := paho.NewClientOptions()
	opts.AddBroker(s.Broker)
	opts.SetClientID(clientID)
	opts.SetConnectTimeout(8 * time.Second)
	opts.SetKeepAlive(20 * time.Second)
	opts.SetAutoReconnect(true)
	opts.SetCleanSession(true)
	if s.Username != "" {
		opts.SetUsername(s.Username)
		opts.SetPassword(s.Password)
	}
	opts.SetOnConnectHandler(func(c paho.Client) {
		b.mu.Lock()
		b.online = true
		b.err = ""
		prefix := b.cfg.TopicPrefix
		b.mu.Unlock()
		if prefix == "" {
			prefix = "linenexus/gw-local-01"
		}
		cmd := prefix + "/command"
		if token := c.Subscribe(cmd, byte(s.QoS), b.onCommand); token.Wait() && token.Error() != nil {
			b.setErr(token.Error().Error())
		}
	})
	opts.SetConnectionLostHandler(func(_ paho.Client, err error) {
		b.mu.Lock()
		b.online = false
		if err != nil {
			b.err = err.Error()
		}
		b.mu.Unlock()
	})

	c := paho.NewClient(opts)
	token := c.Connect()
	if !token.WaitTimeout(8 * time.Second) {
		b.setErr("连接超时")
		return fmt.Errorf("连接 MQTT %s 超时", s.Broker)
	}
	if err := token.Error(); err != nil {
		b.setErr(err.Error())
		return fmt.Errorf("连接 MQTT %s: %w", s.Broker, err)
	}
	b.mu.Lock()
	b.client = c
	b.online = true
	b.mu.Unlock()
	return nil
}

func (b *Bus) onCommand(_ paho.Client, msg paho.Message) {
	payload := string(msg.Payload())
	b.record("rx", msg.Topic(), payload)
	ack := map[string]any{"ok": true, "echo": payload}
	var in map[string]any
	if json.Unmarshal(msg.Payload(), &in) == nil {
		if id, ok := in["id"]; ok {
			ack["id"] = id
		}
	}
	raw, _ := json.Marshal(ack)
	_, _ = b.PublishAck(string(raw))
}

func (b *Bus) PublishTelemetry(_ context.Context, batch TelemetryBatch) error {
	if len(batch.Points) == 0 {
		return nil
	}
	raw, err := json.Marshal(batch)
	if err != nil {
		return err
	}
	topic, _, _ := b.Topics()
	return b.publish(topic, string(raw))
}

func (b *Bus) PublishTest(payload string) (string, error) {
	if payload == "" {
		payload = `{"type":"ping","from":"linenexus-edge"}`
	}
	topic, _, _ := b.Topics()
	return topic, b.publish(topic, payload)
}

func (b *Bus) PublishAck(payload string) (string, error) {
	_, _, topic := b.Topics()
	return topic, b.publish(topic, payload)
}

func (b *Bus) publish(topic, payload string) error {
	b.mu.Lock()
	c := b.client
	qos := byte(b.cfg.QoS)
	online := b.online && c != nil && c.IsConnected()
	b.mu.Unlock()
	if !online {
		return fmt.Errorf("MQTT 未连接。先勾选「启用上报」并保存")
	}
	token := c.Publish(topic, qos, false, payload)
	if !token.WaitTimeout(5 * time.Second) {
		return fmt.Errorf("发布超时")
	}
	if err := token.Error(); err != nil {
		return err
	}
	b.record("tx", topic, payload)
	return nil
}

func (b *Bus) record(dir, topic, payload string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	item := Traffic{Time: time.Now(), Direction: dir, Topic: topic, Payload: payload}
	b.traffic = append([]Traffic{item}, b.traffic...)
	if len(b.traffic) > 80 {
		b.traffic = b.traffic[:80]
	}
}

func (b *Bus) setErr(s string) {
	b.mu.Lock()
	b.err = s
	b.online = false
	b.mu.Unlock()
}

func (b *Bus) Close() error {
	b.mu.Lock()
	c := b.client
	b.client = nil
	b.online = false
	b.mu.Unlock()
	if c != nil && c.IsConnected() {
		c.Disconnect(200)
	}
	return nil
}
