package cache

import (
	"context"
	"fmt"
	"time"

	"linenexus-edge/internal/southbound"
	"linenexus-edge/internal/storage/model"
	"linenexus-edge/internal/storage/repo"
)

type Outbox struct {
	store *repo.Store
}

func NewOutbox(store *repo.Store) *Outbox {
	return &Outbox{store: store}
}

func (o *Outbox) Append(ctx context.Context, deviceID string, samples []southbound.Sample) error {
	rows := make([]model.TelemetryOutbox, 0, len(samples))
	now := time.Now()
	for _, s := range samples {
		quality := s.Quality
		if quality == "" {
			quality = model.QualityGood
		}
		ts := s.TS
		if ts.IsZero() {
			ts = now
		}
		rows = append(rows, model.TelemetryOutbox{
			DeviceID: deviceID,
			PointID:  s.PointID,
			Value:    fmt.Sprint(s.Value),
			Quality:  quality,
			SourceTS: ts,
		})
	}
	return o.store.EnqueueTelemetry(ctx, rows)
}
