package model

import (
	"time"
)

const (
	ProtocolOPCUA  = "opcua"
	ProtocolModbus = "modbus"
	ProtocolMQTT   = "mqtt"
	ProtocolCODESYS = "codesys"

	ModeSimulated = "simulated"
	ModePhysical  = "physical"

	QualityGood = "good"
	QualityBad  = "bad"

	TypePLC    = "plc"
	TypeSensor = "sensor"
	TypeDrive  = "drive"
	TypeMeter  = "meter"
	TypeMQTT   = "mqtt"
	TypeOther  = "other"

	DirRx = "rx"
	DirTx = "tx"

	MsgOK  = "ok"
	MsgErr = "error"
)

// EdgeGateway 边端网关（现场 Go 进程），不是 Spring Cloud Gateway。
type EdgeGateway struct {
	ID           string         `gorm:"primaryKey;size:64" json:"id"`
	TenantID     string         `gorm:"size:64;index" json:"tenantId"`
	Name         string         `gorm:"size:128" json:"name"`
	Status       string         `gorm:"size:32" json:"status"`
	Capabilities JSON `gorm:"type:text" json:"capabilities"`
	CreatedAt    time.Time      `json:"createdAt"`
	UpdatedAt    time.Time      `json:"updatedAt"`
}

func (EdgeGateway) TableName() string { return "gateways" }

type Device struct {
	ID            string     `gorm:"primaryKey;size:64" json:"id"`
	GatewayID     string     `gorm:"size:64;index" json:"gatewayId"`
	Name          string     `gorm:"size:128" json:"name"`
	DeviceType    string     `gorm:"size:32;index" json:"deviceType"`
	Protocol      string     `gorm:"size:32;index" json:"protocol"`
	Mode          string     `gorm:"size:16" json:"mode"`
	Connection    JSON       `gorm:"type:text" json:"connection"`
	Enabled       bool       `gorm:"default:true" json:"enabled"`
	LastHeartbeat *time.Time `json:"lastHeartbeat,omitempty"`
	CreatedAt     time.Time  `json:"createdAt"`
	UpdatedAt     time.Time  `json:"updatedAt"`
	Points        []Point    `gorm:"foreignKey:DeviceID" json:"points,omitempty"`
	Online          bool   `gorm:"-" json:"online"`
	HeartbeatAgeSec *int64 `gorm:"-" json:"heartbeatAgeSec,omitempty"`
	Endpoint        string `gorm:"-" json:"endpoint"`
}

func (Device) TableName() string { return "devices" }

func DefaultDeviceType(protocol string) string {
	switch protocol {
	case ProtocolCODESYS, ProtocolOPCUA:
		return TypePLC
	case ProtocolMQTT:
		return TypeMQTT
	case ProtocolModbus:
		return TypeMeter
	default:
		return TypeOther
	}
}

func (d *Device) ApplyHeartbeat(now time.Time, offlineAfter time.Duration) {
	if d.DeviceType == "" {
		d.DeviceType = DefaultDeviceType(d.Protocol)
	}
	if d.LastHeartbeat == nil {
		d.Online = false
		return
	}
	age := now.Sub(*d.LastHeartbeat)
	sec := int64(age.Seconds())
	d.HeartbeatAgeSec = &sec
	d.Online = age >= 0 && age <= offlineAfter
}

type Point struct {
	ID        string         `gorm:"primaryKey;size:64" json:"id"`
	DeviceID  string         `gorm:"size:64;index" json:"deviceId"`
	Name      string         `gorm:"size:128" json:"name"`
	DataType  string         `gorm:"size:32" json:"dataType"`
	RW        string         `gorm:"size:8" json:"rw"`
	Address   JSON `gorm:"type:text" json:"address"`
	Scale     float64        `json:"scale"`
	Enabled   bool           `gorm:"default:true" json:"enabled"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
}

func (Point) TableName() string { return "points" }

// TelemetryOutbox 断网补传队列，北向通道接通后再刷。
type TelemetryOutbox struct {
	ID        uint64     `gorm:"primaryKey;autoIncrement" json:"id"`
	DeviceID  string     `gorm:"size:64;index" json:"deviceId"`
	PointID   string     `gorm:"size:64;index" json:"pointId"`
	Value     string     `gorm:"type:text" json:"value"`
	Quality   string     `gorm:"size:16" json:"quality"`
	SourceTS  time.Time  `json:"sourceTs"`
	CreatedAt time.Time  `json:"createdAt"`
	SentAt    *time.Time `gorm:"index" json:"sentAt,omitempty"`
}

func (TelemetryOutbox) TableName() string { return "telemetry_outbox" }

// Message 南向收发记录：rx=设备到网关，tx=网关到设备。
type Message struct {
	ID         uint64    `gorm:"primaryKey;autoIncrement" json:"id"`
	DeviceID   string    `gorm:"size:64;index" json:"deviceId"`
	DeviceName string    `gorm:"size:128" json:"deviceName"`
	PointID    string    `gorm:"size:64;index" json:"pointId"`
	PointName  string    `gorm:"size:128" json:"pointName"`
	Direction  string    `gorm:"size:8;index" json:"direction"`
	Value      string    `gorm:"type:text" json:"value"`
	Status     string    `gorm:"size:16" json:"status"`
	Error      string    `gorm:"type:text" json:"error,omitempty"`
	CreatedAt  time.Time `json:"createdAt"`
}

func (Message) TableName() string { return "messages" }
