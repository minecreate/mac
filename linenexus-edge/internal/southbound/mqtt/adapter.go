package mqtt

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"strings"
	"sync"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"

	"linenexus-edge/internal/southbound"
	"linenexus-edge/internal/storage/model"
)

func init() {
	paho.ERROR = log.New(io.Discard, "", 0)
	paho.CRITICAL = log.New(io.Discard, "", 0)
	paho.WARN = log.New(io.Discard, "", 0)
}

type Adapter struct {
	mu     sync.Mutex
	client paho.Client
	cache  map[string]string
}

func New() southbound.Adapter { return &Adapter{cache: map[string]string{}} }

func (a *Adapter) Protocol() string { return model.ProtocolMQTT }

func (a *Adapter) Connect(ctx context.Context, conn southbound.Connection) error {
	broker := normalizeBroker(str(conn["broker"]))
	if broker == "" {
		return fmt.Errorf("缺少 MQTT broker")
	}
	clientID := str(conn["clientId"])
	if clientID == "" {
		clientID = "linenexus-edge"
	}
	clientID = fmt.Sprintf("%s-%d", clientID, time.Now().UnixNano()%1_000_000_000)

	opts := paho.NewClientOptions()
	opts.AddBroker(broker)
	opts.SetClientID(clientID)
	opts.SetConnectTimeout(8 * time.Second)
	opts.SetKeepAlive(20 * time.Second)
	opts.SetPingTimeout(5 * time.Second)
	opts.SetAutoReconnect(false)
	opts.SetCleanSession(true)
	if user := str(conn["username"]); user != "" {
		opts.SetUsername(user)
		opts.SetPassword(str(conn["password"]))
	}
	opts.SetDefaultPublishHandler(func(_ paho.Client, msg paho.Message) {
		a.remember(msg.Topic(), string(msg.Payload()))
	})

	c := paho.NewClient(opts)
	token := c.Connect()
	if !token.WaitTimeout(8 * time.Second) {
		return fmt.Errorf("连接 MQTT %s: 超时", broker)
	}
	if err := token.Error(); err != nil {
		return fmt.Errorf("连接 MQTT %s: %w", broker, err)
	}
	if err := ctx.Err(); err != nil {
		c.Disconnect(100)
		return err
	}

	a.mu.Lock()
	if a.client != nil && a.client.IsConnected() {
		a.client.Disconnect(100)
	}
	a.client = c
	if a.cache == nil {
		a.cache = map[string]string{}
	}
	a.mu.Unlock()
	return nil
}

func (a *Adapter) Read(ctx context.Context, points []southbound.PointRef) ([]southbound.Sample, error) {
	c, err := a.live()
	if err != nil {
		return nil, err
	}
	topics := make([]string, 0, len(points))
	for _, p := range points {
		if t := str(p.Address["topic"]); t != "" {
			topics = append(topics, t)
		}
	}
	if err := a.subscribe(c, topics); err != nil {
		return nil, err
	}
	a.wait(ctx, 800*time.Millisecond)

	now := time.Now()
	out := make([]southbound.Sample, 0, len(points))
	for _, p := range points {
		sample := southbound.Sample{PointID: p.ID, Quality: model.QualityGood, TS: now}
		topic := str(p.Address["topic"])
		if topic == "" {
			sample.Quality, sample.Value = model.QualityBad, "点地址缺少 topic"
			out = append(out, sample)
			continue
		}
		payload, ok := a.cached(topic)
		if !ok {
			sample.Quality, sample.Value = model.QualityBad, "该主题还没有消息（可先写一条，或等设备发布）"
			out = append(out, sample)
			continue
		}
		sample.Value = extract(payload, str(p.Address["jsonPath"]))
		out = append(out, sample)
	}
	return out, nil
}

func (a *Adapter) Write(_ context.Context, writes []southbound.WriteReq) ([]southbound.WriteAck, error) {
	c, err := a.live()
	if err != nil {
		return nil, err
	}
	acks := make([]southbound.WriteAck, 0, len(writes))
	for _, w := range writes {
		ack := southbound.WriteAck{PointID: w.PointID, OK: true}
		topic := str(w.Address["topic"])
		if topic == "" {
			ack.OK, ack.Err = false, "点地址缺少 topic"
			acks = append(acks, ack)
			continue
		}
		payload := encodeWrite(w.Value, str(w.Address["jsonPath"]))
		token := c.Publish(topic, 0, true, payload)
		if !token.WaitTimeout(5 * time.Second) {
			ack.OK, ack.Err = false, "发布超时"
		} else if err := token.Error(); err != nil {
			ack.OK, ack.Err = false, err.Error()
		} else {
			a.remember(topic, payload)
		}
		acks = append(acks, ack)
	}
	return acks, nil
}

func (a *Adapter) Browse(ctx context.Context, _ string, max int) ([]southbound.BrowseNode, error) {
	c, err := a.live()
	if err != nil {
		return nil, err
	}
	if max <= 0 || max > 80 {
		max = 40
	}
	_ = a.subscribe(c, []string{"lab/#", "#"})
	a.wait(ctx, 1200*time.Millisecond)

	a.mu.Lock()
	defer a.mu.Unlock()
	out := make([]southbound.BrowseNode, 0, len(a.cache))
	for topic, val := range a.cache {
		if len(out) >= max {
			break
		}
		name := topic
		if i := strings.LastIndex(topic, "/"); i >= 0 && i+1 < len(topic) {
			name = topic[i+1:]
		}
		out = append(out, southbound.BrowseNode{
			NodeID: topic,
			Name:   name,
			Path:   topic,
			Class:  "Topic",
			Value:  val,
			Address: map[string]any{
				"topic": topic,
			},
		})
	}
	if len(out) == 0 {
		out = append(out, southbound.BrowseNode{
			Name:  "status",
			Path:  "lab/line1/status",
			Class: "Topic",
			Address: map[string]any{
				"topic": "lab/line1/status",
			},
		})
	}
	return out, nil
}

func (a *Adapter) Close() error {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.client != nil && a.client.IsConnected() {
		a.client.Disconnect(200)
	}
	a.client = nil
	return nil
}

func (a *Adapter) live() (paho.Client, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.client == nil || !a.client.IsConnected() {
		return nil, fmt.Errorf("尚未连接 MQTT")
	}
	return a.client, nil
}

func (a *Adapter) subscribe(c paho.Client, topics []string) error {
	seen := map[string]bool{}
	for _, t := range topics {
		if t == "" || seen[t] {
			continue
		}
		seen[t] = true
		token := c.Subscribe(t, 0, func(_ paho.Client, msg paho.Message) {
			a.remember(msg.Topic(), string(msg.Payload()))
		})
		if !token.WaitTimeout(3 * time.Second) {
			return fmt.Errorf("订阅 %s 超时", t)
		}
		if err := token.Error(); err != nil && t == "#" {
			continue
		} else if err != nil {
			return fmt.Errorf("订阅 %s: %w", t, err)
		}
	}
	return nil
}

func (a *Adapter) remember(topic, payload string) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.cache == nil {
		a.cache = map[string]string{}
	}
	a.cache[topic] = payload
}

func (a *Adapter) cached(topic string) (string, bool) {
	a.mu.Lock()
	defer a.mu.Unlock()
	v, ok := a.cache[topic]
	return v, ok
}

func (a *Adapter) wait(ctx context.Context, d time.Duration) {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
	case <-t.C:
	}
}

func normalizeBroker(s string) string {
	s = strings.TrimSpace(s)
	if s == "" {
		return ""
	}
	if strings.Contains(s, "://") {
		return s
	}
	return "tcp://" + s
}

func extract(payload, path string) string {
	path = strings.TrimSpace(path)
	if path == "" || path == "$" {
		return payload
	}
	if path == "$.value" {
		var obj map[string]any
		if json.Unmarshal([]byte(payload), &obj) == nil {
			if v, ok := obj["value"]; ok {
				return fmt.Sprint(v)
			}
		}
	}
	return payload
}

func encodeWrite(v any, path string) string {
	s := strings.TrimSpace(fmt.Sprint(v))
	if strings.TrimSpace(path) == "$.value" {
		b, err := json.Marshal(map[string]any{"value": v})
		if err == nil {
			return string(b)
		}
	}
	return s
}

func str(v any) string {
	if v == nil {
		return ""
	}
	s, ok := v.(string)
	if ok {
		return s
	}
	return fmt.Sprint(v)
}
