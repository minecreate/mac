/**
 * Editor command layer — P0 applies patches immediately;
 * history stack is stubbed so B-phase can fill undo/redo without rewriting the editor.
 */

/**
 * @typedef {'add'|'remove'|'update'|'reorder'|'replaceDocument'} CommandType
 */

/**
 * @typedef {Object} EditorCommand
 * @property {CommandType} type
 * @property {any} [payload]
 */

/**
 * @param {import('../model/types').HmiDocument} doc
 * @param {EditorCommand} cmd
 * @returns {import('../model/types').HmiDocument}
 */
export function applyPatch(doc, cmd) {
  if (!cmd || !cmd.type) return doc
  switch (cmd.type) {
    case 'replaceDocument':
      return structuredClone(cmd.payload)
    case 'add': {
      const el = cmd.payload
      return { ...doc, elements: [...doc.elements, el] }
    }
    case 'remove': {
      const id = cmd.payload?.id
      return { ...doc, elements: doc.elements.filter((e) => e.id !== id) }
    }
    case 'update': {
      const next = cmd.payload
      return {
        ...doc,
        elements: doc.elements.map((e) => (e.id === next.id ? { ...e, ...next, id: e.id } : e)),
      }
    }
    case 'reorder': {
      const order = cmd.payload?.ids
      if (!Array.isArray(order)) return doc
      const map = new Map(doc.elements.map((e) => [e.id, e]))
      const elements = order.map((id) => map.get(id)).filter(Boolean)
      for (const e of doc.elements) {
        if (!order.includes(e.id)) elements.push(e)
      }
      return { ...doc, elements }
    }
    default:
      return doc
  }
}

export function createCommandHistory() {
  /** @type {import('../model/types').HmiDocument[]} */
  const past = []
  /** @type {import('../model/types').HmiDocument[]} */
  const future = []
  let enabled = false // B-phase: set true to activate undo/redo

  return {
    /** Enable when implementing undo/redo UI (phase B). */
    setEnabled(v) {
      enabled = !!v
    },
    isEnabled() {
      return enabled
    },
    /**
     * @param {import('../model/types').HmiDocument} current
     * @param {EditorCommand} cmd
     */
    push(current, cmd) {
      const next = applyPatch(current, cmd)
      if (enabled) {
        past.push(structuredClone(current))
        if (past.length > 100) past.shift()
        future.length = 0
      }
      return next
    },
    /** @param {import('../model/types').HmiDocument} current */
    undo(current) {
      if (!enabled || !past.length) return current
      future.push(structuredClone(current))
      return past.pop()
    },
    /** @param {import('../model/types').HmiDocument} current */
    redo(current) {
      if (!enabled || !future.length) return current
      past.push(structuredClone(current))
      return future.pop()
    },
    canUndo() {
      return enabled && past.length > 0
    },
    canRedo() {
      return enabled && future.length > 0
    },
    clear() {
      past.length = 0
      future.length = 0
    },
  }
}
