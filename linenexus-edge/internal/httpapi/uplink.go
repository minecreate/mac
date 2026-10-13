package httpapi

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	"linenexus-edge/internal/northbound"
)

func (h *handler) settingsPath() string {
	return northbound.PathFromSQLite(h.cfg.SQLite.Path)
}

func (h *handler) getNorthbound(c *gin.Context) {
	s, err := northbound.LoadSettings(h.settingsPath(), h.cfg.EdgeGateway.ID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	s = s.Normalize()
	if s.CloudAPIBase == "" {
		s.CloudAPIBase = strings.TrimRight(h.cfg.Cloud.APIBase, "/")
	}
	telemetry, command, ack := h.bus.Topics()
	online, busErr := h.bus.Status()
	c.JSON(http.StatusOK, gin.H{
		"config":    s.Redacted(),
		"edgeGatewayId": h.cfg.EdgeGateway.ID,
		"cloud": gin.H{
			"apiBase":       s.CloudAPIBase,
			"registerPath":  h.cfg.Cloud.RegisterPath,
			"managementUrl": h.cfg.Cloud.ManagementURL,
		},
		"mqtt": gin.H{
			"online":    online,
			"error":     busErr,
			"telemetry": telemetry,
			"command":   command,
			"ack":       ack,
		},
		"messages": h.bus.Messages(40),
	})
}

func (h *handler) saveNorthbound(c *gin.Context) {
	var req northbound.Settings
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	cur, err := northbound.LoadSettings(h.settingsPath(), h.cfg.EdgeGateway.ID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	if req.Password == "" || req.Password == "******" {
		req.Password = cur.Password
	}
	if req.GRPCToken == "" || req.GRPCToken == "******" {
		req.GRPCToken = cur.GRPCToken
	}
	req = req.Normalize()
	if req.CloudAPIBase == "" {
		req.CloudAPIBase = strings.TrimRight(h.cfg.Cloud.APIBase, "/")
	}
	if err := northbound.SaveSettings(h.settingsPath(), req); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	if err := h.bus.Apply(req); err != nil {
		c.JSON(http.StatusOK, gin.H{"config": req.Redacted(), "warning": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"config": req.Redacted()})
}

func (h *handler) testNorthbound(c *gin.Context) {
	var req northbound.Settings
	_ = c.ShouldBindJSON(&req)
	cur, err := northbound.LoadSettings(h.settingsPath(), h.cfg.EdgeGateway.ID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	if req.Broker == "" {
		req = cur
	} else {
		if req.Password == "" || req.Password == "******" {
			req.Password = cur.Password
		}
		if req.GRPCToken == "" || req.GRPCToken == "******" {
			req.GRPCToken = cur.GRPCToken
		}
		req = req.Normalize()
	}
	if err := northbound.TestSettings(c.Request.Context(), req); err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true, "broker": req.Normalize().Broker})
}

func (h *handler) testNorthboundGRPC(c *gin.Context) {
	var req northbound.Settings
	_ = c.ShouldBindJSON(&req)
	cur, err := northbound.LoadSettings(h.settingsPath(), h.cfg.EdgeGateway.ID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	if req.GRPCTarget == "" {
		req = cur
	} else {
		if req.GRPCToken == "" || req.GRPCToken == "******" {
			req.GRPCToken = cur.GRPCToken
		}
		req = req.Normalize()
	}
	if err := northbound.TestGRPC(c.Request.Context(), req); err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true, "target": req.Normalize().GRPCTarget})
}

func (h *handler) testNorthboundCloud(c *gin.Context) {
	var req northbound.Settings
	_ = c.ShouldBindJSON(&req)
	cur, err := northbound.LoadSettings(h.settingsPath(), h.cfg.EdgeGateway.ID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	cloud := h.cfg.Cloud
	apiBase := strings.TrimSpace(req.CloudAPIBase)
	if apiBase == "" {
		apiBase = cur.CloudAPIBase
	}
	if apiBase == "" {
		apiBase = cloud.APIBase
	}
	cloud.APIBase = strings.TrimRight(apiBase, "/")
	if err := northbound.RegisterToCloud(c.Request.Context(), cloud, h.cfg.EdgeGateway); err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"ok":      true,
		"apiBase": cloud.APIBase,
		"path":    cloud.RegisterPath,
	})
}

func (h *handler) sendNorthbound(c *gin.Context) {
	var req struct {
		Payload string `json:"payload"`
	}
	_ = c.ShouldBindJSON(&req)
	topic, err := h.bus.PublishTest(req.Payload)
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true, "topic": topic})
}

func (h *handler) listNorthboundMessages(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"messages": h.bus.Messages(80)})
}
