package httpapi

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"

	"github.com/gin-gonic/gin"

	"linenexus-edge/internal/config"
	"linenexus-edge/internal/northbound"
	"linenexus-edge/internal/southbound"
	"linenexus-edge/internal/storage/repo"
)

func NewRouter(cfg *config.Config, store *repo.Store, reg *southbound.Registry, bus *northbound.Bus) *gin.Engine {
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	r.Use(gin.Recovery(), gin.Logger())

	h := &handler{cfg: cfg, store: store, reg: reg, bus: bus}

	r.GET("/healthz", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "ok"})
	})
	r.GET("/readyz", h.ready)

	v1 := r.Group("/api/v1")
	{
		v1.GET("/edge-gateway", h.getEdgeGateway)
		v1.GET("/northbound", h.getNorthbound)
		v1.PUT("/northbound", h.saveNorthbound)
		v1.POST("/northbound/test", h.testNorthbound)
		v1.POST("/northbound/send", h.sendNorthbound)
		v1.GET("/northbound/messages", h.listNorthboundMessages)
		v1.POST("/northbound/grpc/test", h.testNorthboundGRPC)
		v1.POST("/northbound/cloud/test", h.testNorthboundCloud)
		v1.GET("/adapters", h.listAdapters)
		v1.GET("/outbox", h.listOutbox)
		v1.GET("/messages", h.listMessages)
		v1.POST("/messages", h.sendMessage)
		v1.GET("/devices", h.listDevices)
		v1.POST("/devices", h.createDevice)
		v1.GET("/devices/:id", h.getDevice)
		v1.DELETE("/devices/:id", h.deleteDevice)
		v1.POST("/devices/:id/connect", h.connectDevice)
		v1.POST("/devices/:id/browse", h.browseDevice)
		v1.POST("/devices/:id/read", h.readDevice)
		v1.GET("/devices/:id/points", h.listDevicePoints)
		v1.POST("/devices/:id/points", h.createPoint)
		v1.PUT("/points/:id", h.updatePoint)
		v1.GET("/points", h.listPoints)
		v1.DELETE("/points/:id", h.deletePoint)
	}

	mountWeb(r, cfg.HTTP.WebDir)
	return r
}

func mountWeb(r *gin.Engine, webDir string) {
	indexPath := filepath.Join(webDir, "index.html")
	r.GET("/", func(c *gin.Context) {
		data, err := os.ReadFile(indexPath)
		if err != nil {
			c.String(http.StatusInternalServerError, fmt.Sprintf("读取调试页失败: %v", err))
			return
		}
		c.Data(http.StatusOK, "text/html; charset=utf-8", data)
	})
	r.Static("/css", filepath.Join(webDir, "css"))
	r.Static("/js", filepath.Join(webDir, "js"))
}
