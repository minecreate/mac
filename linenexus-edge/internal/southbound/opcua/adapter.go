package opcua

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/gopcua/opcua"
	"github.com/gopcua/opcua/ua"

	"linenexus-edge/internal/southbound"
	"linenexus-edge/internal/storage/model"
)

type Adapter struct {
	mu     sync.Mutex
	client *opcua.Client
}

func New() southbound.Adapter { return &Adapter{} }

func (a *Adapter) Protocol() string { return model.ProtocolOPCUA }

func (a *Adapter) Connect(ctx context.Context, conn southbound.Connection) error {
	url := str(conn["url"])
	if url == "" {
		return fmt.Errorf("缺少 opc.tcp 地址")
	}
	user := str(conn["username"])
	pass := str(conn["password"])
	if user == "" {
		return fmt.Errorf("OPC UA 需要用户名")
	}
	if pass == "" {
		return fmt.Errorf("OPC UA 需要密码")
	}

	policy := str(conn["securityPolicy"])
	if policy == "" {
		policy = "Basic256Sha256"
	}
	mode := ua.MessageSecurityModeSignAndEncrypt
	if str(conn["securityMode"]) == "Sign" {
		mode = ua.MessageSecurityModeSign
	}

	certDir := str(conn["certDir"])
	if certDir == "" {
		certDir = "./data/opcua"
	}
	certFile, keyFile, err := ensureClientCert(certDir)
	if err != nil {
		return err
	}

	opts := []opcua.Option{
		opcua.SecurityPolicy(policy),
		opcua.SecurityMode(mode),
		opcua.CertificateFile(certFile),
		opcua.PrivateKeyFile(keyFile),
		opcua.AuthUsername(user, pass),
		opcua.RequestTimeout(15 * time.Second),
		opcua.DialTimeout(10 * time.Second),
	}

	// 只用发现结果拿服务器证书和策略，不改用户填的地址。
	// CODESYS 常把 EndpointURL 写成本机 IPv6（fe80::...%WLAN），改过去会连错。
	if eps, err := opcua.GetEndpoints(ctx, url); err == nil {
		if ep, err := opcua.SelectEndpoint(eps, policy, mode); err == nil && ep != nil {
			opts = append(opts, opcua.SecurityFromEndpoint(ep, ua.UserTokenTypeUserName))
		}
	}

	c, err := opcua.NewClient(url, opts...)
	if err != nil {
		return fmt.Errorf("创建 OPC UA 客户端: %w", err)
	}
	if err := c.Connect(ctx); err != nil {
		_ = c.Close(ctx)
		return fmt.Errorf("连接 OPC UA: %w", err)
	}

	a.mu.Lock()
	if a.client != nil {
		_ = a.client.Close(ctx)
	}
	a.client = c
	a.mu.Unlock()
	return nil
}

func (a *Adapter) Read(ctx context.Context, points []southbound.PointRef) ([]southbound.Sample, error) {
	c, err := a.live()
	if err != nil {
		return nil, err
	}
	out := make([]southbound.Sample, 0, len(points))
	now := time.Now()
	for _, p := range points {
		id, err := nodeIDOf(p.Address)
		if err != nil {
			out = append(out, southbound.Sample{PointID: p.ID, Quality: model.QualityBad, Value: err.Error(), TS: now})
			continue
		}
		v, err := c.Node(id).Value(ctx)
		if err != nil {
			out = append(out, southbound.Sample{PointID: p.ID, Quality: model.QualityBad, Value: err.Error(), TS: now})
			continue
		}
		sample := southbound.Sample{PointID: p.ID, Quality: model.QualityGood, TS: now}
		if v != nil {
			sample.Value = v.Value()
		}
		out = append(out, sample)
	}
	return out, nil
}

func (a *Adapter) Write(ctx context.Context, writes []southbound.WriteReq) ([]southbound.WriteAck, error) {
	c, err := a.live()
	if err != nil {
		return nil, err
	}
	acks := make([]southbound.WriteAck, 0, len(writes))
	for _, w := range writes {
		ack := southbound.WriteAck{PointID: w.PointID, OK: true}
		id, err := nodeIDOf(w.Address)
		if err != nil {
			ack.OK, ack.Err = false, err.Error()
			acks = append(acks, ack)
			continue
		}
		variant, err := ua.NewVariant(normalizeWrite(w.Value))
		if err != nil {
			ack.OK, ack.Err = false, err.Error()
			acks = append(acks, ack)
			continue
		}
		resp, err := c.Write(ctx, &ua.WriteRequest{
			NodesToWrite: []*ua.WriteValue{{
				NodeID:      id,
				AttributeID: ua.AttributeIDValue,
				Value: &ua.DataValue{
					EncodingMask: ua.DataValueValue,
					Value:        variant,
				},
			}},
		})
		if err != nil {
			ack.OK, ack.Err = false, err.Error()
		} else if len(resp.Results) > 0 && resp.Results[0] != ua.StatusOK {
			ack.OK, ack.Err = false, resp.Results[0].Error()
		}
		acks = append(acks, ack)
	}
	return acks, nil
}

func (a *Adapter) Close() error {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.client == nil {
		return nil
	}
	err := a.client.Close(context.Background())
	a.client = nil
	return err
}

func (a *Adapter) live() (*opcua.Client, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.client == nil {
		return nil, fmt.Errorf("尚未连接 OPC UA")
	}
	return a.client, nil
}

func nodeIDOf(addr map[string]any) (*ua.NodeID, error) {
	raw := str(addr["nodeId"])
	if raw == "" {
		return nil, fmt.Errorf("点地址缺少 nodeId")
	}
	return ua.ParseNodeID(raw)
}

func normalizeWrite(v any) any {
	switch t := v.(type) {
	case string:
		return t
	case bool:
		return t
	case float64:
		if t == float64(int64(t)) {
			return int64(t)
		}
		return t
	default:
		return v
	}
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
