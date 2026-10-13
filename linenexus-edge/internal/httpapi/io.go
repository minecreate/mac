package httpapi

import (
	"context"
	"errors"
	"fmt"
	"net/http"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"

	"linenexus-edge/internal/mapping"
	"linenexus-edge/internal/southbound"
	"linenexus-edge/internal/storage/model"
)

type sendReq struct {
	DeviceID string `json:"deviceId" binding:"required"`
	PointID  string `json:"pointId" binding:"required"`
	Value    any    `json:"value"`
}

type connectReq struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

func (h *handler) listMessages(c *gin.Context) {
	list, err := h.store.ListMessages(c.Request.Context(), c.Query("deviceId"), 80)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

func (h *handler) sendMessage(c *gin.Context) {
	var req sendReq
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	dev, pt, err := h.loadDevicePoint(c, req.DeviceID, req.PointID)
	if err != nil {
		return
	}

	msg := model.Message{
		DeviceID:   dev.ID,
		DeviceName: dev.Name,
		PointID:    pt.ID,
		PointName:  pt.Name,
		Direction:  model.DirTx,
		Value:      fmt.Sprint(req.Value),
		Status:     model.MsgOK,
	}

	if err := h.writePoint(c.Request.Context(), *dev, *pt, req.Value); err != nil {
		msg.Status = model.MsgErr
		msg.Error = err.Error()
	} else {
		_ = h.store.TouchHeartbeat(c.Request.Context(), dev.ID)
	}
	if err := h.store.AppendMessage(c.Request.Context(), &msg); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	if msg.Status == model.MsgErr {
		c.JSON(http.StatusBadGateway, msg)
		return
	}
	c.JSON(http.StatusOK, msg)
}

func (h *handler) connectDevice(c *gin.Context) {
	dev, err := h.store.GetDevice(c.Request.Context(), c.Param("id"))
	if errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "设备不存在"})
		return
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	var req connectReq
	_ = c.ShouldBindJSON(&req)
	conn, err := mapping.DeviceConnection(*dev)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	if req.Username != "" || req.Password != "" {
		conn = mapping.MergeAuth(conn, req.Username, req.Password)
		if err := h.store.UpdateConnection(c.Request.Context(), dev.ID, model.MustJSON(conn)); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		dev.Connection = model.MustJSON(conn)
	}

	endpoint := mapping.EndpointOf(*dev)
	msg := model.Message{
		DeviceID:   dev.ID,
		DeviceName: dev.Name,
		PointName:  "连接",
		Direction:  model.DirTx,
		Value:      endpoint,
		Status:     model.MsgOK,
	}
	adapter, _, err := h.openAdapter(c.Request.Context(), *dev)
	if err != nil {
		msg.Status = model.MsgErr
		msg.Error = err.Error()
	} else {
		_ = adapter.Close()
		_ = h.store.TouchHeartbeat(c.Request.Context(), dev.ID)
	}
	if err := h.store.AppendMessage(c.Request.Context(), &msg); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	if msg.Status == model.MsgErr {
		c.JSON(http.StatusBadGateway, msg)
		return
	}
	c.JSON(http.StatusOK, msg)
}

func (h *handler) browseDevice(c *gin.Context) {
	dev, err := h.store.GetDevice(c.Request.Context(), c.Param("id"))
	if errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "设备不存在"})
		return
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	adapter, _, err := h.openAdapter(c.Request.Context(), *dev)
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": err.Error()})
		return
	}
	defer adapter.Close()

	browser, ok := adapter.(southbound.Browser)
	if !ok {
		c.JSON(http.StatusBadRequest, gin.H{"error": "该协议还不支持浏览节点"})
		return
	}
	nodes, err := browser.Browse(c.Request.Context(), c.Query("nodeId"), 80)
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": err.Error()})
		return
	}
	_ = h.store.TouchHeartbeat(c.Request.Context(), dev.ID)
	c.JSON(http.StatusOK, gin.H{"items": nodes})
}

func (h *handler) readDevice(c *gin.Context) {
	dev, err := h.store.GetDevice(c.Request.Context(), c.Param("id"))
	if errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "设备不存在"})
		return
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	if len(dev.Points) == 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "这台设备还没有点"})
		return
	}

	samples, readErr := h.readPoints(c.Request.Context(), *dev, dev.Points)
	logged := make([]model.Message, 0, len(dev.Points))
	if readErr != nil {
		for _, pt := range dev.Points {
			msg := model.Message{
				DeviceID:   dev.ID,
				DeviceName: dev.Name,
				PointID:    pt.ID,
				PointName:  pt.Name,
				Direction:  model.DirRx,
				Status:     model.MsgErr,
				Error:      readErr.Error(),
			}
			_ = h.store.AppendMessage(c.Request.Context(), &msg)
			logged = append(logged, msg)
		}
		c.JSON(http.StatusBadGateway, gin.H{"error": readErr.Error(), "items": logged})
		return
	}

	byID := map[string]southbound.Sample{}
	for _, s := range samples {
		byID[s.PointID] = s
	}
	okCount := 0
	for _, pt := range dev.Points {
		msg := model.Message{
			DeviceID:   dev.ID,
			DeviceName: dev.Name,
			PointID:    pt.ID,
			PointName:  pt.Name,
			Direction:  model.DirRx,
			Status:     model.MsgOK,
		}
		if s, ok := byID[pt.ID]; ok {
			if s.Quality == model.QualityBad {
				msg.Status = model.MsgErr
				msg.Error = fmt.Sprint(s.Value)
			} else {
				msg.Value = fmt.Sprint(s.Value)
				okCount++
			}
		}
		_ = h.store.AppendMessage(c.Request.Context(), &msg)
		logged = append(logged, msg)
	}
	if okCount == 0 {
		c.JSON(http.StatusBadGateway, gin.H{"error": "读取失败", "items": logged})
		return
	}
	_ = h.store.TouchHeartbeat(c.Request.Context(), dev.ID)
	c.JSON(http.StatusOK, gin.H{"items": logged})
}

func (h *handler) loadDevicePoint(c *gin.Context, deviceID, pointID string) (*model.Device, *model.Point, error) {
	dev, err := h.store.GetDevice(c.Request.Context(), deviceID)
	if errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "设备不存在"})
		return nil, nil, err
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return nil, nil, err
	}
	pt, err := h.store.GetPoint(c.Request.Context(), pointID)
	if errors.Is(err, gorm.ErrRecordNotFound) {
		// 兼容：Profile 里写点名（如 m0 / t0），按设备内 name 解析
		pt, err = h.store.GetPointByDeviceAndName(c.Request.Context(), deviceID, pointID)
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusNotFound, gin.H{"error": "点不存在（可用点 UUID 或点名）"})
		return nil, nil, err
	}
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return nil, nil, err
	}
	if pt.DeviceID != dev.ID {
		c.JSON(http.StatusBadRequest, gin.H{"error": "点和设备不匹配"})
		return nil, nil, errors.New("mismatch")
	}
	return dev, pt, nil
}

func (h *handler) writePoint(ctx context.Context, dev model.Device, pt model.Point, value any) error {
	adapter, conn, err := h.openAdapter(ctx, dev)
	if err != nil {
		return err
	}
	defer adapter.Close()

	refs, err := mapping.PointRefs([]model.Point{pt})
	if err != nil {
		return err
	}
	acks, err := adapter.Write(ctx, []southbound.WriteReq{{
		PointID: pt.ID,
		Address: refs[0].Address,
		Value:   value,
	}})
	if err != nil {
		return err
	}
	if len(acks) > 0 && !acks[0].OK {
		if acks[0].Err != "" {
			return errors.New(acks[0].Err)
		}
		return errors.New("写入失败")
	}
	_ = conn
	return nil
}

func (h *handler) readPoints(ctx context.Context, dev model.Device, points []model.Point) ([]southbound.Sample, error) {
	adapter, _, err := h.openAdapter(ctx, dev)
	if err != nil {
		return nil, err
	}
	defer adapter.Close()
	refs, err := mapping.PointRefs(points)
	if err != nil {
		return nil, err
	}
	return adapter.Read(ctx, refs)
}

func (h *handler) openAdapter(ctx context.Context, dev model.Device) (southbound.Adapter, southbound.Connection, error) {
	adapter, err := h.reg.New(mapping.ProtocolOf(dev))
	if err != nil {
		return nil, nil, err
	}
	conn, err := mapping.DeviceConnection(dev)
	if err != nil {
		adapter.Close()
		return nil, nil, err
	}
	if err := adapter.Connect(ctx, conn); err != nil {
		adapter.Close()
		return nil, nil, err
	}
	return adapter, conn, nil
}
