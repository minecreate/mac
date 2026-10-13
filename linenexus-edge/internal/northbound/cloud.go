package northbound

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"linenexus-edge/internal/config"
)

type CloudRegistration struct {
	ID             string `json:"id"`
	Name           string `json:"name"`
	TenantID       string `json:"tenantId"`
	Status         string `json:"status"`
	ManagementURL  string `json:"managementUrl"`
}

func RegisterToCloud(ctx context.Context, cloud config.CloudConfig, gw config.EdgeGatewayConfig) error {
	base := strings.TrimRight(strings.TrimSpace(cloud.APIBase), "/")
	if base == "" {
		return fmt.Errorf("缺少 cloud.api_base")
	}
	path := cloud.RegisterPath
	if path == "" {
		path = "/api/assets/edge-gateways/register"
	}
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	url := base + path

	body, err := json.Marshal(CloudRegistration{
		ID:            gw.ID,
		Name:          gw.Name,
		TenantID:      gw.TenantID,
		Status:        "online",
		ManagementURL: strings.TrimRight(strings.TrimSpace(cloud.ManagementURL), "/"),
	})
	if err != nil {
		return err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	if gw.TenantID != "" {
		req.Header.Set("X-Tenant-Id", gw.TenantID)
	}

	client := &http.Client{Timeout: 8 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("请求云 %s: %w", url, err)
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		msg := strings.TrimSpace(string(raw))
		if msg == "" {
			msg = resp.Status
		}
		return fmt.Errorf("云注册失败 %s: %s", url, msg)
	}
	return nil
}
