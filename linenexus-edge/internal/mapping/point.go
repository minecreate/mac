package mapping

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"

	"linenexus-edge/internal/southbound"
	"linenexus-edge/internal/storage/model"
)

func DeviceConnection(d model.Device) (southbound.Connection, error) {
	if len(d.Connection) == 0 {
		return southbound.Connection{}, nil
	}
	var conn southbound.Connection
	if err := json.Unmarshal(d.Connection, &conn); err != nil {
		return nil, err
	}
	return conn, nil
}

func PointRefs(points []model.Point) ([]southbound.PointRef, error) {
	refs := make([]southbound.PointRef, 0, len(points))
	for _, p := range points {
		addr := map[string]any{}
		if len(p.Address) > 0 {
			if err := json.Unmarshal(p.Address, &addr); err != nil {
				return nil, err
			}
		}
		refs = append(refs, southbound.PointRef{ID: p.ID, Address: addr})
	}
	return refs, nil
}

func ProtocolOf(d model.Device) string {
	if d.Protocol == model.ProtocolCODESYS {
		return model.ProtocolOPCUA
	}
	return d.Protocol
}

func EndpointOf(d model.Device) string {
	conn, err := DeviceConnection(d)
	if err != nil {
		return ""
	}
	switch d.Protocol {
	case model.ProtocolOPCUA, model.ProtocolCODESYS:
		return asString(conn["url"])
	case model.ProtocolModbus:
		host := asString(conn["host"])
		port := asString(conn["port"])
		if host == "" {
			return ""
		}
		if port == "" {
			return host
		}
		return host + ":" + port
	case model.ProtocolMQTT:
		return asString(conn["broker"])
	default:
		return ""
	}
}

func ConnectionFromEndpoint(protocol, endpoint, username, password string) (model.JSON, error) {
	endpoint = strings.TrimSpace(endpoint)
	if endpoint == "" {
		return nil, fmt.Errorf("地址为空")
	}
	var conn southbound.Connection
	switch protocol {
	case model.ProtocolOPCUA, model.ProtocolCODESYS:
		if username == "" && protocol == model.ProtocolCODESYS {
			username = "mplc"
		}
		conn = southbound.Connection{
			"url":            endpoint,
			"username":       username,
			"password":       password,
			"securityPolicy": "Basic256Sha256",
			"securityMode":   "SignAndEncrypt",
		}
	case model.ProtocolModbus:
		host, port := endpoint, 502
		if h, p, ok := strings.Cut(endpoint, ":"); ok {
			host = h
			n, err := strconv.Atoi(p)
			if err != nil {
				return nil, fmt.Errorf("Modbus 端口无效: %s", p)
			}
			port = n
		}
		conn = southbound.Connection{"host": host, "port": port, "unitId": 1}
	case model.ProtocolMQTT:
		if !strings.Contains(endpoint, "://") {
			endpoint = "tcp://" + endpoint
		}
		conn = southbound.Connection{
			"broker":   endpoint,
			"clientId": "linenexus-edge",
			"username": username,
			"password": password,
		}
	default:
		return nil, fmt.Errorf("未知协议")
	}
	return model.MustJSON(conn), nil
}

func MergeAuth(conn southbound.Connection, username, password string) southbound.Connection {
	if conn == nil {
		conn = southbound.Connection{}
	}
	if username != "" {
		conn["username"] = username
	}
	if password != "" {
		conn["password"] = password
	}
	return conn
}

func RedactSecrets(d *model.Device) {
	if d == nil {
		return
	}
	conn, err := DeviceConnection(*d)
	if err != nil || len(conn) == 0 {
		return
	}
	if asString(conn["password"]) == "" {
		return
	}
	conn["password"] = "******"
	d.Connection = model.MustJSON(conn)
}

func asString(v any) string {
	if v == nil {
		return ""
	}
	switch t := v.(type) {
	case string:
		return t
	case float64:
		if t == float64(int64(t)) {
			return strconv.FormatInt(int64(t), 10)
		}
		return fmt.Sprint(t)
	default:
		return fmt.Sprint(t)
	}
}
