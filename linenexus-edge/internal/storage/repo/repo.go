package repo

import (
	"context"
	"fmt"
	"time"

	"gorm.io/gorm"

	"linenexus-edge/internal/config"
	"linenexus-edge/internal/storage/model"
)

type Store struct {
	DB *gorm.DB
}

func New(db *gorm.DB) *Store {
	return &Store{DB: db}
}

func (s *Store) EnsureEdgeGateway(ctx context.Context, cfg config.EdgeGatewayConfig) (*model.EdgeGateway, error) {
	gw := model.EdgeGateway{
		ID:           cfg.ID,
		TenantID:     cfg.TenantID,
		Name:         cfg.Name,
		Status:       "online",
		Capabilities: model.MustJSON([]string{"opcua", "modbus", "mqtt"}),
	}

	var existing model.EdgeGateway
	err := s.DB.WithContext(ctx).First(&existing, "id = ?", cfg.ID).Error
	if err == gorm.ErrRecordNotFound {
		if err := s.DB.WithContext(ctx).Create(&gw).Error; err != nil {
			return nil, fmt.Errorf("创建边端网关: %w", err)
		}
		return &gw, nil
	}
	if err != nil {
		return nil, err
	}

	existing.Name = cfg.Name
	existing.TenantID = cfg.TenantID
	existing.Status = "online"
	existing.Capabilities = gw.Capabilities
	if err := s.DB.WithContext(ctx).Save(&existing).Error; err != nil {
		return nil, err
	}
	return &existing, nil
}

func (s *Store) GetEdgeGateway(ctx context.Context, id string) (*model.EdgeGateway, error) {
	var gw model.EdgeGateway
	if err := s.DB.WithContext(ctx).First(&gw, "id = ?", id).Error; err != nil {
		return nil, err
	}
	return &gw, nil
}

func (s *Store) ListDevices(ctx context.Context, gatewayID string) ([]model.Device, error) {
	var list []model.Device
	q := s.DB.WithContext(ctx).Preload("Points").Order("created_at asc")
	if gatewayID != "" {
		q = q.Where("gateway_id = ?", gatewayID)
	}
	if err := q.Find(&list).Error; err != nil {
		return nil, err
	}
	return list, nil
}

func (s *Store) CreateDevice(ctx context.Context, d *model.Device) error {
	return s.DB.WithContext(ctx).Create(d).Error
}

func (s *Store) GetDevice(ctx context.Context, id string) (*model.Device, error) {
	var d model.Device
	if err := s.DB.WithContext(ctx).Preload("Points").First(&d, "id = ?", id).Error; err != nil {
		return nil, err
	}
	return &d, nil
}

func (s *Store) ListPoints(ctx context.Context, deviceID string) ([]model.Point, error) {
	var list []model.Point
	q := s.DB.WithContext(ctx).Order("created_at asc")
	if deviceID != "" {
		q = q.Where("device_id = ?", deviceID)
	}
	if err := q.Find(&list).Error; err != nil {
		return nil, err
	}
	return list, nil
}

func (s *Store) CreatePoint(ctx context.Context, p *model.Point) error {
	return s.DB.WithContext(ctx).Create(p).Error
}

func (s *Store) UpdatePoint(ctx context.Context, p *model.Point) error {
	res := s.DB.WithContext(ctx).Model(&model.Point{}).Where("id = ?", p.ID).Updates(map[string]any{
		"name":      p.Name,
		"data_type": p.DataType,
		"rw":        p.RW,
		"address":   p.Address,
		"scale":     p.Scale,
		"enabled":   p.Enabled,
	})
	if res.Error != nil {
		return res.Error
	}
	if res.RowsAffected == 0 {
		return gorm.ErrRecordNotFound
	}
	return nil
}

func (s *Store) UpdateConnection(ctx context.Context, id string, conn model.JSON) error {
	return s.DB.WithContext(ctx).Model(&model.Device{}).Where("id = ?", id).Update("connection", conn).Error
}

func (s *Store) TouchHeartbeat(ctx context.Context, id string) error {
	now := time.Now()
	return s.DB.WithContext(ctx).Model(&model.Device{}).Where("id = ?", id).Update("last_heartbeat", now).Error
}

func (s *Store) GetPoint(ctx context.Context, id string) (*model.Point, error) {
	var p model.Point
	if err := s.DB.WithContext(ctx).First(&p, "id = ?", id).Error; err != nil {
		return nil, err
	}
	return &p, nil
}

// GetPointByDeviceAndName 按点名解析（Profile 可用 m0/t0 等稳定名，不必写 UUID）。
func (s *Store) GetPointByDeviceAndName(ctx context.Context, deviceID, name string) (*model.Point, error) {
	var p model.Point
	if err := s.DB.WithContext(ctx).
		Where("device_id = ? AND name = ?", deviceID, name).
		First(&p).Error; err != nil {
		return nil, err
	}
	return &p, nil
}

func (s *Store) AppendMessage(ctx context.Context, m *model.Message) error {
	return s.DB.WithContext(ctx).Create(m).Error
}

func (s *Store) ListMessages(ctx context.Context, deviceID string, limit int) ([]model.Message, error) {
	if limit <= 0 || limit > 200 {
		limit = 80
	}
	var list []model.Message
	q := s.DB.WithContext(ctx).Order("id desc").Limit(limit)
	if deviceID != "" {
		q = q.Where("device_id = ?", deviceID)
	}
	if err := q.Find(&list).Error; err != nil {
		return nil, err
	}
	return list, nil
}

func (s *Store) EnabledDevicesWithPoints(ctx context.Context, gatewayID string) ([]model.Device, error) {
	var list []model.Device
	err := s.DB.WithContext(ctx).
		Where("gateway_id = ? AND enabled = ?", gatewayID, true).
		Preload("Points", "enabled = ?", true).
		Find(&list).Error
	return list, err
}

func (s *Store) EnqueueTelemetry(ctx context.Context, rows []model.TelemetryOutbox) error {
	if len(rows) == 0 {
		return nil
	}
	return s.DB.WithContext(ctx).Create(&rows).Error
}

func (s *Store) PendingTelemetryCount(ctx context.Context) (int64, error) {
	var n int64
	err := s.DB.WithContext(ctx).Model(&model.TelemetryOutbox{}).Where("sent_at IS NULL").Count(&n).Error
	return n, err
}

func (s *Store) ListOutbox(ctx context.Context, limit int) ([]model.TelemetryOutbox, error) {
	if limit <= 0 || limit > 200 {
		limit = 40
	}
	var list []model.TelemetryOutbox
	err := s.DB.WithContext(ctx).Order("id desc").Limit(limit).Find(&list).Error
	return list, err
}

func (s *Store) DeleteDevice(ctx context.Context, id string) error {
	return s.DB.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("device_id = ?", id).Delete(&model.Point{}).Error; err != nil {
			return err
		}
		if err := tx.Where("device_id = ?", id).Delete(&model.Message{}).Error; err != nil {
			return err
		}
		res := tx.Where("id = ?", id).Delete(&model.Device{})
		if res.Error != nil {
			return res.Error
		}
		if res.RowsAffected == 0 {
			return gorm.ErrRecordNotFound
		}
		return nil
	})
}

func (s *Store) DeletePoint(ctx context.Context, id string) error {
	res := s.DB.WithContext(ctx).Where("id = ?", id).Delete(&model.Point{})
	if res.Error != nil {
		return res.Error
	}
	if res.RowsAffected == 0 {
		return gorm.ErrRecordNotFound
	}
	return nil
}
