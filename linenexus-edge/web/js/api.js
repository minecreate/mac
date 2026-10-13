async function api(path, opts) {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...opts,
  });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || res.statusText);
  return data;
}

function parseJSON(text, fallback) {
  const t = String(text || "").trim();
  if (!t) return fallback;
  return JSON.parse(t);
}

function fmtJSON(v) {
  try {
    return typeof v === "string" ? v : JSON.stringify(v);
  } catch {
    return String(v);
  }
}
