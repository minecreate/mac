package modbus

import (
	"context"
	"encoding/binary"
	"fmt"
	"math"
	"strconv"
	"strings"
	"sync"
	"time"

	gomodbus "github.com/goburrow/modbus"

	"linenexus-edge/internal/southbound"
	"linenexus-edge/internal/storage/model"
)

type Adapter struct {
	mu      sync.Mutex
	handler *gomodbus.TCPClientHandler
	client  gomodbus.Client
}

func New() southbound.Adapter { return &Adapter{} }

func (a *Adapter) Protocol() string { return model.ProtocolModbus }

func (a *Adapter) Connect(_ context.Context, conn southbound.Connection) error {
	host := str(conn["host"])
	if host == "" {
		return fmt.Errorf("缺少 Modbus 主机")
	}
	port := intOf(conn["port"], 502)
	unit := byte(intOf(conn["unitId"], 1))

	h := gomodbus.NewTCPClientHandler(fmt.Sprintf("%s:%d", host, port))
	h.Timeout = 5 * time.Second
	h.SlaveId = unit
	if err := h.Connect(); err != nil {
		return fmt.Errorf("连接 Modbus %s:%d: %w", host, port, err)
	}

	a.mu.Lock()
	if a.handler != nil {
		_ = a.handler.Close()
	}
	a.handler = h
	a.client = gomodbus.NewClient(h)
	a.mu.Unlock()
	return nil
}

func (a *Adapter) Read(_ context.Context, points []southbound.PointRef) ([]southbound.Sample, error) {
	c, err := a.live()
	if err != nil {
		return nil, err
	}
	out := make([]southbound.Sample, 0, len(points))
	now := time.Now()
	for _, p := range points {
		sample := southbound.Sample{PointID: p.ID, Quality: model.QualityGood, TS: now}
		fn, offset, typ, err := parseAddr(p.Address)
		if err != nil {
			sample.Quality, sample.Value = model.QualityBad, err.Error()
			out = append(out, sample)
			continue
		}
		raw, err := readRegs(c, fn, offset, typ)
		if err != nil {
			sample.Quality, sample.Value = model.QualityBad, err.Error()
			out = append(out, sample)
			continue
		}
		sample.Value = raw
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
		fn, offset, typ, err := parseAddr(w.Address)
		if err != nil {
			ack.OK, ack.Err = false, err.Error()
			acks = append(acks, ack)
			continue
		}
		if err := writeRegs(c, fn, offset, typ, w.Value); err != nil {
			ack.OK, ack.Err = false, err.Error()
		}
		acks = append(acks, ack)
	}
	return acks, nil
}

func (a *Adapter) Browse(_ context.Context, _ string, max int) ([]southbound.BrowseNode, error) {
	c, err := a.live()
	if err != nil {
		return nil, err
	}
	if max <= 0 || max > 40 {
		max = 16
	}
	out := make([]southbound.BrowseNode, 0, max)
	for i := 0; i < max && i < 16; i++ {
		item := southbound.BrowseNode{
			Name:  fmt.Sprintf("HR%d", i),
			Path:  fmt.Sprintf("holding[%d]", i),
			Class: "Holding",
			Address: map[string]any{
				"func":   3,
				"offset": i,
				"type":   "int16",
			},
		}
		if raw, err := c.ReadHoldingRegisters(uint16(i), 1); err == nil && len(raw) >= 2 {
			item.Value = fmt.Sprint(int16(binary.BigEndian.Uint16(raw)))
		}
		item.NodeID = fmt.Sprintf("hr:%d", i)
		out = append(out, item)
	}
	return out, nil
}

func (a *Adapter) Close() error {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.handler == nil {
		return nil
	}
	err := a.handler.Close()
	a.handler = nil
	a.client = nil
	return err
}

func (a *Adapter) live() (gomodbus.Client, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.client == nil {
		return nil, fmt.Errorf("尚未连接 Modbus")
	}
	return a.client, nil
}

func parseAddr(addr map[string]any) (fn int, offset uint16, typ string, err error) {
	fn = intOf(addr["func"], 3)
	if fn < 1 || fn > 4 {
		return 0, 0, "", fmt.Errorf("不支持的功能码 %d，读用 1/2/3/4", fn)
	}
	off := intOf(addr["offset"], -1)
	if off < 0 {
		return 0, 0, "", fmt.Errorf("点地址缺少 offset")
	}
	typ = str(addr["type"])
	if typ == "" {
		if fn == 1 || fn == 2 {
			typ = "bool"
		} else {
			typ = "int16"
		}
	}
	return fn, uint16(off), typ, nil
}

func readRegs(c gomodbus.Client, fn int, offset uint16, typ string) (any, error) {
	n := qty(typ)
	var raw []byte
	var err error
	switch fn {
	case 1:
		raw, err = c.ReadCoils(offset, n)
	case 2:
		raw, err = c.ReadDiscreteInputs(offset, n)
	case 3:
		raw, err = c.ReadHoldingRegisters(offset, n)
	case 4:
		raw, err = c.ReadInputRegisters(offset, n)
	default:
		return nil, fmt.Errorf("不支持读功能码 %d", fn)
	}
	if err != nil {
		return nil, err
	}
	if fn == 1 || fn == 2 {
		if len(raw) == 0 {
			return false, nil
		}
		return raw[0]&0x01 == 1, nil
	}
	return decode(raw, typ)
}

func writeRegs(c gomodbus.Client, fn int, offset uint16, typ string, value any) error {
	if fn == 1 || fn == 2 {
		on := toBool(value)
		var coil uint16
		if on {
			coil = 0xFF00
		}
		_, err := c.WriteSingleCoil(offset, coil)
		return err
	}
	if fn != 3 {
		return fmt.Errorf("功能码 %d 只读", fn)
	}
	raw, err := encode(value, typ)
	if err != nil {
		return err
	}
	if len(raw) == 2 {
		_, err = c.WriteSingleRegister(offset, binary.BigEndian.Uint16(raw))
		return err
	}
	regs := make([]byte, len(raw))
	copy(regs, raw)
	_, err = c.WriteMultipleRegisters(offset, uint16(len(raw)/2), regs)
	return err
}

func qty(typ string) uint16 {
	switch strings.ToLower(typ) {
	case "int32", "uint32", "float32":
		return 2
	case "int64", "uint64", "float64":
		return 4
	default:
		return 1
	}
}

func decode(b []byte, typ string) (any, error) {
	need := int(qty(typ)) * 2
	if len(b) < need {
		return nil, fmt.Errorf("返回长度不足")
	}
	switch strings.ToLower(typ) {
	case "uint16":
		return binary.BigEndian.Uint16(b), nil
	case "uint32":
		return binary.BigEndian.Uint32(b), nil
	case "int32":
		return int32(binary.BigEndian.Uint32(b)), nil
	case "float32":
		return math.Float32frombits(binary.BigEndian.Uint32(b)), nil
	default:
		return int16(binary.BigEndian.Uint16(b)), nil
	}
}

func encode(v any, typ string) ([]byte, error) {
	switch strings.ToLower(typ) {
	case "uint16":
		b := make([]byte, 2)
		binary.BigEndian.PutUint16(b, uint16(toInt(v)))
		return b, nil
	case "uint32":
		b := make([]byte, 4)
		binary.BigEndian.PutUint32(b, uint32(toInt(v)))
		return b, nil
	case "int32":
		b := make([]byte, 4)
		binary.BigEndian.PutUint32(b, uint32(int32(toInt(v))))
		return b, nil
	case "float32":
		b := make([]byte, 4)
		binary.BigEndian.PutUint32(b, math.Float32bits(float32(toFloat(v))))
		return b, nil
	default:
		b := make([]byte, 2)
		binary.BigEndian.PutUint16(b, uint16(int16(toInt(v))))
		return b, nil
	}
}

func toBool(v any) bool {
	switch t := v.(type) {
	case bool:
		return t
	case string:
		s := strings.ToLower(strings.TrimSpace(t))
		return s == "1" || s == "true" || s == "on"
	default:
		return toInt(v) != 0
	}
}

func toInt(v any) int64 {
	switch t := v.(type) {
	case int:
		return int64(t)
	case int64:
		return t
	case float64:
		return int64(t)
	case string:
		n, _ := strconv.ParseInt(strings.TrimSpace(t), 10, 64)
		return n
	default:
		n, _ := strconv.ParseInt(fmt.Sprint(v), 10, 64)
		return n
	}
}

func toFloat(v any) float64 {
	switch t := v.(type) {
	case float64:
		return t
	case float32:
		return float64(t)
	case int:
		return float64(t)
	case int64:
		return float64(t)
	case string:
		n, _ := strconv.ParseFloat(strings.TrimSpace(t), 64)
		return n
	default:
		n, _ := strconv.ParseFloat(fmt.Sprint(v), 64)
		return n
	}
}

func intOf(v any, def int) int {
	switch t := v.(type) {
	case int:
		return t
	case int64:
		return int(t)
	case float64:
		return int(t)
	case string:
		n, err := strconv.Atoi(strings.TrimSpace(t))
		if err == nil {
			return n
		}
	}
	return def
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
