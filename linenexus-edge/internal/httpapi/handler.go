package httpapi

import (
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"gorm.io/gorm"

	"linenexus-edge/internal/config"
	"linenexus-edge/internal/mapping"
	"linenexus-edge/internal/northbound"
	"linenexus-edge/internal/southbound"
	"linenexus-edge/internal/storage/model"
	"linenexus-edge/internal/storage/repo"
)

type handler struct {
	cfg   *config.Config
	store *repo.Store
	reg   *southbound.Registry
	bus   *northbound.Bus
}

type deviceReq struct {
	Name       string          `json:"name"`
	DeviceType string          `json:"deviceType"`
	Protocol   string          `json:"protocol" binding:"required"`
	Address    string          `json:"address"`
	Username   string          `json:"username"`
	Password   string          `json:"password"`
	Mode       string          `json:"mode"`
	Connection json.RawMessage `json:"connection"`
	Enabled    *bool           `json:"enabled"`
}

type pointReq struct {
	Name     string          `json:"name" binding:"required"`
	DataType string          `json:"dataType"`
	RW       string          `json:"rw"`
	Address  json.RawMessage `json:"address"`
	Scale    float64         `json:"scale"`
	Enabled  *bool           `json:"enabled"`
}

func (h *handler) ready(c *gin.Context) {
	pending, err := h.store.PendingTelemetryCount(c.Request.Context())
	if err != nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"status": "not_ready", "error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"status":         "ready",
		"pendingOutbox":  pending,
		"adapters":       h.reg.Protocols(),
	})
}

func (h *handler) getEdgeGateway(c *gin.Context) {
	gw, err := h.store.GetEdgeGateway(c.Request.Context(), h.cfg.EdgeGateway.ID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gw)
}

func (h *handler) listAdapters(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"protocols": h.reg.Protocols()})
}

func (h *handler) listDevices(c *gin.Context) {
	list, err := h.store.ListDevices(c.Request.Context(), h.cfg.EdgeGateway.ID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	h.decorateDevices(list)
	c.JSON(http.StatusOK, gin.H{"items": list})
}

func (h *handler) decorateDevices(list []model.Device) {
	now := time.Now()
	for i := range list {
		list[i].Endpoint = mapping.EndpointOf(list[i])
		list[i].ApplyHeartbeat(now, h.cfg.Collect.OfflineAfter)
		mapping.RedactSecrets(&list[i])
	}
}

func (h *handler) decorateDevice(d *model.Device) {
	if d == nil {
		return
	}
	d.Endpoint = mapping.EndpointOf(*d)
	d.ApplyHeartbeat(time.Now(), h.cfg.Collect.OfflineAfter)
	mapping.RedactSecrets(d)
}

func (h *handler) createDevice(c *gin.Context) {
	var req deviceReq
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	if _, err := h.reg.New(req.Protocol); err != nil && req.Protocol != model.ProtocolCODESYS {
		c.JSON(http.StatusBadRequest, gin.H{"error": "不支持的协议: " + req.Protocol})
		return
	}

	mode := req.Mode
	if mode == "" {
		mode = model.ModePhysical
	}
	deviceType := req.DeviceType
	if deviceType == "" {
		deviceType = model.DefaultDeviceType(req.Protocol)
	}
	enabled := true
	if req.Enabled != nil {
		enabled = *req.Enabled
	}
	conn := req.Connection
	if len(conn) == 0 {
		built, err := mapping.ConnectionFromEndpoint(req.Protocol, req.Address, req.Username, req.Password)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
		conn = json.RawMessage(built)
	}
	name := req.Name
	if name == "" {
		name = req.Address
	}
	if name == "" {
		name = req.Protocol
	}

	d := &model.Device{
		ID:         uuid.NewString(),
		GatewayID:  h.cfg.EdgeGateway.ID,
		Name:       name,
		DeviceType: deviceType,
		Protocol:   req.Protocol,
		Mode:       mode,
		Connection: model.JSON(conn),
		Enabled:    enabled,
	}
	if err := h.store.CreateDevice(c.Request.Context(), d); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	h.decorateDevice(d)
	c.JSON(http.StatusCreated, d)
}

func (h *handler) getDevice(c *gin.Context) {
	d, err := h.store.GetDevice(c.Request.Context(), c.Param("id"))
	if errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "设备不存在"})
		return
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	h.decorateDevice(d)
	c.JSON(http.StatusOK, d)
}

func (h *handler) listDevicePoints(c *gin.Context) {
	if _, err := h.store.GetDevice(c.Request.Context(), c.Param("id")); errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "设备不存在"})
		return
	} else if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	list, err := h.store.ListPoints(c.Request.Context(), c.Param("id"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

func (h *handler) createPoint(c *gin.Context) {
	if _, err := h.store.GetDevice(c.Request.Context(), c.Param("id")); errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "设备不存在"})
		return
	} else if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	var req pointReq
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	rw := req.RW
	if rw == "" {
		rw = "rw"
	}
	enabled := true
	if req.Enabled != nil {
		enabled = *req.Enabled
	}
	addr := req.Address
	if len(addr) == 0 {
		addr = []byte("{}")
	}
	dt := req.DataType
	if dt == "" {
		dt = "float"
	}

	p := &model.Point{
		ID:       uuid.NewString(),
		DeviceID: c.Param("id"),
		Name:     req.Name,
		DataType: dt,
		RW:       rw,
		Address:  model.JSON(addr),
		Scale:    req.Scale,
		Enabled:  enabled,
	}
	if err := h.store.CreatePoint(c.Request.Context(), p); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusCreated, p)
}

func (h *handler) updatePoint(c *gin.Context) {
	existing, err := h.store.GetPoint(c.Request.Context(), c.Param("id"))
	if errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "点不存在"})
		return
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	var req pointReq
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	if req.Name != "" {
		existing.Name = req.Name
	}
	if req.DataType != "" {
		existing.DataType = req.DataType
	}
	if req.RW != "" {
		existing.RW = req.RW
	}
	if len(req.Address) > 0 {
		existing.Address = model.JSON(req.Address)
	}
	existing.Scale = req.Scale
	if req.Enabled != nil {
		existing.Enabled = *req.Enabled
	}

	if err := h.store.UpdatePoint(c.Request.Context(), existing); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	updated, err := h.store.GetPoint(c.Request.Context(), existing.ID)
	if err != nil {
		c.JSON(http.StatusOK, existing)
		return
	}
	c.JSON(http.StatusOK, updated)
}

func (h *handler) listPoints(c *gin.Context) {
	list, err := h.store.ListPoints(c.Request.Context(), c.Query("deviceId"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

func (h *handler) deleteDevice(c *gin.Context) {
	err := h.store.DeleteDevice(c.Request.Context(), c.Param("id"))
	if errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "设备不存在"})
		return
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.Status(http.StatusNoContent)
}

func (h *handler) deletePoint(c *gin.Context) {
	err := h.store.DeletePoint(c.Request.Context(), c.Param("id"))
	if errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "点不存在"})
		return
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.Status(http.StatusNoContent)
}

func (h *handler) listOutbox(c *gin.Context) {
	list, err := h.store.ListOutbox(c.Request.Context(), 40)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}
