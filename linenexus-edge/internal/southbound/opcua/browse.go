package opcua

import (
	"context"
	"fmt"

	"github.com/gopcua/opcua"
	"github.com/gopcua/opcua/id"
	"github.com/gopcua/opcua/ua"

	"linenexus-edge/internal/southbound"
)

func (a *Adapter) Browse(ctx context.Context, start string, max int) ([]southbound.BrowseNode, error) {
	c, err := a.live()
	if err != nil {
		return nil, err
	}
	if max <= 0 || max > 200 {
		max = 80
	}
	root := ua.NewNumericNodeID(0, id.ObjectsFolder)
	if start != "" {
		parsed, err := ua.ParseNodeID(start)
		if err != nil {
			return nil, fmt.Errorf("nodeId 无效: %w", err)
		}
		root = parsed
	}

	out := make([]southbound.BrowseNode, 0, max)
	walkBrowse(ctx, c.Node(root), "", 0, max, &out)
	if len(out) == 0 {
		return nil, fmt.Errorf("地址空间里没有可读变量，换一个起始 nodeId 再试")
	}
	return out, nil
}

func walkBrowse(ctx context.Context, n *opcua.Node, path string, depth, max int, out *[]southbound.BrowseNode) {
	if n == nil || len(*out) >= max || depth > 8 {
		return
	}
	if skipBrowseNode(n.ID) {
		return
	}

	name := n.ID.String()
	if bn, err := n.BrowseName(ctx); err == nil && bn != nil && bn.Name != "" {
		name = bn.Name
	}
	if dn, err := n.DisplayName(ctx); err == nil && dn != nil && dn.Text != "" {
		name = dn.Text
	}
	full := name
	if path != "" {
		full = path + "." + name
	}

	cls, err := n.NodeClass(ctx)
	if err != nil {
		return
	}
	if cls == ua.NodeClassVariable {
		item := southbound.BrowseNode{
			NodeID: n.ID.String(),
			Name:   name,
			Path:   full,
			Class:  "Variable",
		}
		if v, err := n.Value(ctx); err == nil && v != nil && v.Value() != nil {
			item.Value = fmt.Sprint(v.Value())
		}
		*out = append(*out, item)
	}

	children, err := n.Children(ctx, 0, ua.NodeClassObject|ua.NodeClassVariable)
	if err != nil {
		return
	}
	for _, ch := range children {
		if len(*out) >= max {
			return
		}
		walkBrowse(ctx, ch, full, depth+1, max, out)
	}
}

func skipBrowseNode(nid *ua.NodeID) bool {
	if nid == nil || nid.Namespace() != 0 {
		return false
	}
	switch nid.IntID() {
	case id.Server, id.TypesFolder, id.ViewsFolder, id.Aliases:
		return true
	default:
		return false
	}
}
