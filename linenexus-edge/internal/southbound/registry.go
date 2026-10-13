package southbound

import "sync"

type Factory func() Adapter

type Registry struct {
	mu        sync.RWMutex
	factories map[string]Factory
}

func NewRegistry() *Registry {
	return &Registry{factories: map[string]Factory{}}
}

func (r *Registry) Register(protocol string, factory Factory) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.factories[protocol] = factory
}

func (r *Registry) New(protocol string) (Adapter, error) {
	r.mu.RLock()
	factory, ok := r.factories[protocol]
	r.mu.RUnlock()
	if !ok {
		return nil, ErrUnknownProtocol
	}
	return factory(), nil
}

func (r *Registry) Protocols() []string {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]string, 0, len(r.factories))
	for p := range r.factories {
		out = append(out, p)
	}
	return out
}
