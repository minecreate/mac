package model

import (
	"database/sql/driver"
	"encoding/json"
	"fmt"
)

// JSON 以 TEXT 存 JSON，避免引入 gorm.io/datatypes（会拖进其它驱动）。
type JSON []byte

func (j JSON) MarshalJSON() ([]byte, error) {
	if len(j) == 0 {
		return []byte("null"), nil
	}
	return j, nil
}

func (j *JSON) UnmarshalJSON(data []byte) error {
	if j == nil {
		return fmt.Errorf("JSON: UnmarshalJSON on nil pointer")
	}
	*j = append((*j)[0:0], data...)
	return nil
}

func (j JSON) Value() (driver.Value, error) {
	if len(j) == 0 {
		return "{}", nil
	}
	return string(j), nil
}

func (j *JSON) Scan(value any) error {
	if value == nil {
		*j = JSON("{}")
		return nil
	}
	switch v := value.(type) {
	case []byte:
		*j = append(JSON(nil), v...)
	case string:
		*j = JSON(v)
	default:
		return fmt.Errorf("无法将 %T 转为 JSON", value)
	}
	return nil
}

func MustJSON(v any) JSON {
	b, err := json.Marshal(v)
	if err != nil {
		return JSON("[]")
	}
	return JSON(b)
}
