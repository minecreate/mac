const PROTOCOL_LABEL = {
  opcua: "OPC UA",
  codesys: "OPC UA",
  modbus: "Modbus",
  mqtt: "MQTT",
};

const RW_LABEL = { r: "只读", w: "只写", rw: "读写" };

const state = { devices: [], selectedId: null, points: [], messages: [], reading: false, page: "devices" };
const $ = (id) => document.getElementById(id);

function toast(msg, bad) {
  const el = $("toast");
  el.hidden = false;
  el.textContent = msg;
  el.classList.toggle("bad", !!bad);
  clearTimeout(toast._t);
  toast._t = setTimeout(() => { el.hidden = true; }, 2800);
}

function escapeHtml(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[c]));
}

function applyPreset() {
  const p = PRESETS[$("device-protocol").value];
  $("device-address").value = p.endpoint;
  $("device-username").value = p.username || "";
  $("point-address").value = JSON.stringify(p.address, null, 2);
}

function heartbeatText(d) {
  if (d.heartbeatAgeSec == null) return "无心跳";
  const n = Number(d.heartbeatAgeSec);
  if (n < 5) return "刚刚";
  if (n < 60) return `${n} 秒前`;
  return `${Math.floor(n / 60)} 分前`;
}

function fmtTime(s) {
  if (!s) return "";
  return String(s).replace("T", " ").slice(0, 19);
}

function canWrite(p) {
  return p.rw !== "r";
}

function rwText(p) {
  return RW_LABEL[p.rw] || "读写";
}

function looksFailed(m) {
  if (!m) return false;
  if (m.status === "error") return true;
  return /StatusBad|does not exist/.test(`${m.error || ""} ${m.value || ""}`);
}

function pointStatus(p) {
  const last = state.messages.find((m) => m.pointId === p.id);
  const rx = state.messages.find((m) => m.pointId === p.id && m.direction === "rx");
  if (state.reading && !last) {
    return { value: "", label: "读取中", kind: "wait", time: "" };
  }
  if (!last) {
    return { value: "", label: "未读", kind: "idle", time: "" };
  }
  const failed = looksFailed(last) || looksFailed(rx);
  if (failed) {
    const src = looksFailed(last) ? last : rx;
    return {
      value: rx && !looksFailed(rx) ? rx.value : "",
      label: src.error || src.value || "失败",
      kind: "err",
      time: src.createdAt,
    };
  }
  return {
    value: rx ? rx.value : last.value,
    label: last.direction === "tx" ? "已写入" : "正常",
    kind: "ok",
    time: (rx || last).createdAt,
  };
}

function renderDevices() {
  const body = $("device-body");
  if (!state.devices.length) {
    body.innerHTML = `<tr><td colspan="6">还没有设备。填协议和地址后加入列表。</td></tr>`;
    return;
  }
  body.innerHTML = state.devices.map((d) => `
    <tr class="device ${d.id === state.selectedId ? "on" : ""}" data-id="${d.id}">
      <td>${escapeHtml(d.name)}</td>
      <td>${escapeHtml(PROTOCOL_LABEL[d.protocol] || d.protocol)}</td>
      <td>${escapeHtml(d.endpoint || "-")}</td>
      <td class="${d.online ? "onair" : "offair"}">${d.online ? "在线" : "离线"}</td>
      <td>${escapeHtml(heartbeatText(d))}</td>
      <td>
        <button type="button" data-connect="${d.id}">连接</button>
        <button type="button" data-del-device="${d.id}">删除</button>
      </td>
    </tr>
  `).join("");
}

function renderPoints() {
  const body = $("point-body");
  const can = !!state.selectedId;
  $("point-submit").disabled = !can;
  $("btn-browse").disabled = !can;
  $("btn-read").disabled = !can || !state.points.length || state.reading;
  const dev = state.devices.find((d) => d.id === state.selectedId);
  $("detail-title").textContent = dev ? `${dev.name} 的点` : "点";
  $("selected-device").textContent = dev
    ? `${PROTOCOL_LABEL[dev.protocol] || dev.protocol} · ${dev.endpoint || ""} · ${dev.online ? "在线" : "离线"} · ${heartbeatText(dev)}`
    : "点一台设备，查看可读写点和当前值";
  if (!can) {
    body.innerHTML = `<tr><td colspan="7">点上面一台设备</td></tr>`;
    return;
  }
  if (!state.points.length) {
    body.innerHTML = `<tr><td colspan="7">还没有点。浏览节点或在下方添加。</td></tr>`;
    return;
  }
  body.innerHTML = state.points.map((p) => {
    const st = pointStatus(p);
    const write = canWrite(p)
      ? `<span class="write">
          <input data-write-value="${p.id}" placeholder="写入值" />
          <button type="button" data-write="${p.id}">写</button>
        </span>`
      : "";
    return `
    <tr>
      <td>${escapeHtml(p.name)}</td>
      <td>${escapeHtml(rwText(p))}</td>
      <td class="addr">${escapeHtml(fmtJSON(p.address))}</td>
      <td class="val">${st.value === "" || st.value == null ? "—" : escapeHtml(st.value)}</td>
      <td class="${st.kind}">${escapeHtml(st.label)}</td>
      <td class="when">${escapeHtml(fmtTime(st.time))}</td>
      <td>
        ${write}
        <button type="button" data-del-point="${p.id}">删除</button>
      </td>
    </tr>`;
  }).join("");
}

function renderMessages(items) {
  const box = $("message-list");
  if (!state.selectedId) {
    box.innerHTML = "<p>点一台设备后，这里只显示这台设备的收发。</p>";
    return;
  }
  if (!items.length) {
    box.innerHTML = "<p>这台设备还没有收发记录。点「刷新状态」从设备读一次。</p>";
    return;
  }
  box.innerHTML = items.map((m) => {
    const dir = m.direction === "tx" ? "发" : "收";
    const cls = m.status === "error" ? "err" : m.direction;
    const extra = m.error ? ` 失败: ${escapeHtml(m.error)}` : "";
    return `<p class="${cls}">${escapeHtml(fmtTime(m.createdAt))}  ${dir}  ${escapeHtml(m.pointName || "连接")}  =  ${escapeHtml(m.value || "-")}${extra}</p>`;
  }).join("");
}

async function loadStatus() {
  try {
    const gw = await api("/api/v1/edge-gateway");
    $("gw-meta").textContent = `${gw.name} · ${gw.id}`;
  } catch (err) {
    $("gw-meta").textContent = "边端网关不可用";
    toast(err.message, true);
  }
}

async function loadDevices() {
  const data = await api("/api/v1/devices");
  state.devices = data.items || [];
  if (state.selectedId && !state.devices.some((d) => d.id === state.selectedId)) {
    state.selectedId = null;
    hideBrowse();
  }
  renderDevices();
  await loadPoints();
}

async function loadPoints() {
  if (!state.selectedId) {
    state.points = [];
    renderPoints();
    return;
  }
  const data = await api(`/api/v1/devices/${state.selectedId}/points`);
  state.points = data.items || [];
  renderPoints();
}

async function loadMessages() {
  const q = state.selectedId ? `?deviceId=${encodeURIComponent(state.selectedId)}` : "";
  const data = await api(`/api/v1/messages${q}`);
  state.messages = data.items || [];
  renderMessages(state.messages);
  renderPoints();
}

async function refreshAll() {
  await loadStatus();
  await loadDevices();
  await loadMessages();
}

async function refreshPointStatus() {
  if (!state.selectedId || !state.points.length) return;
  state.reading = true;
  renderPoints();
  try {
    await api(`/api/v1/devices/${state.selectedId}/read`, { method: "POST" });
  } catch (err) {
    toast(err.message, true);
  }
  state.reading = false;
  await loadDevices();
  await loadMessages();
}

async function selectDevice(id, { read } = {}) {
  const changed = state.selectedId !== id;
  state.selectedId = id;
  if (changed) hideBrowse();
  renderDevices();
  await loadPoints();
  await loadMessages();
  if (read && state.points.length) await refreshPointStatus();
}

$("device-protocol").addEventListener("change", applyPreset);

$("device-body").addEventListener("click", async (e) => {
  const connect = e.target.closest("[data-connect]");
  if (connect) {
    e.stopPropagation();
    try {
      await api(`/api/v1/devices/${connect.dataset.connect}/connect`, {
        method: "POST",
        body: JSON.stringify({
          username: $("device-username").value,
          password: $("device-password").value,
        }),
      });
      toast("已连接");
    } catch (err) {
      toast(err.message, true);
    }
    await loadDevices();
    if (state.selectedId === connect.dataset.connect) await loadMessages();
    return;
  }
  const del = e.target.closest("[data-del-device]");
  if (del) {
    e.stopPropagation();
    if (!confirm("删除这台设备及其点和收发记录？")) return;
    try {
      await api(`/api/v1/devices/${del.dataset.delDevice}`, { method: "DELETE" });
      if (state.selectedId === del.dataset.delDevice) state.selectedId = null;
      toast("已删除");
      await refreshAll();
    } catch (err) {
      toast(err.message, true);
    }
    return;
  }
  const tr = e.target.closest("tr.device");
  if (!tr) return;
  await selectDevice(tr.dataset.id, { read: tr.dataset.id !== state.selectedId });
});

$("point-body").addEventListener("click", async (e) => {
  const write = e.target.closest("[data-write]");
  if (write) {
    const input = $("point-body").querySelector(`[data-write-value="${write.dataset.write}"]`);
    const value = input ? input.value : "";
    if (value === "") {
      toast("先填要写的值", true);
      return;
    }
    try {
      await api("/api/v1/messages", {
        method: "POST",
        body: JSON.stringify({
          deviceId: state.selectedId,
          pointId: write.dataset.write,
          value,
        }),
      });
      if (input) input.value = "";
      toast("已写入");
    } catch (err) {
      toast(err.message, true);
    }
    await loadDevices();
    await loadMessages();
    return;
  }
  const btn = e.target.closest("[data-del-point]");
  if (!btn) return;
  try {
    await api(`/api/v1/points/${btn.dataset.delPoint}`, { method: "DELETE" });
    await loadDevices();
    await loadMessages();
  } catch (err) {
    toast(err.message, true);
  }
});

$("device-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  try {
    const created = await api("/api/v1/devices", {
      method: "POST",
      body: JSON.stringify({
        name: fd.get("name"),
        protocol: fd.get("protocol"),
        address: fd.get("address"),
        username: fd.get("username"),
        password: fd.get("password"),
      }),
    });
    e.target.reset();
    applyPreset();
    await selectDevice(created.id);
  } catch (err) {
    toast(err.message, true);
  }
});

$("point-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!state.selectedId) return;
  const fd = new FormData(e.target);
  try {
    await api(`/api/v1/devices/${state.selectedId}/points`, {
      method: "POST",
      body: JSON.stringify({
        name: fd.get("name"),
        rw: fd.get("rw") || "rw",
        address: parseJSON(fd.get("address"), {}),
      }),
    });
    e.target.reset();
    applyPreset();
    $("point-form").querySelector("[name=rw]").value = "rw";
    await loadDevices();
    await loadMessages();
  } catch (err) {
    toast(err.message, true);
  }
});

function hideBrowse() {
  const box = $("browse-list");
  box.hidden = true;
  box.innerHTML = "";
}

function renderBrowse(items) {
  const box = $("browse-list");
  if (!items.length) {
    box.hidden = false;
    box.innerHTML = "<p class=\"hint\">没有可读变量</p>";
    return;
  }
  box.hidden = false;
  box.innerHTML = items.map((n) => {
    const val = n.value != null && n.value !== "" ? `  =  ${escapeHtml(n.value)}` : "";
    const addr = n.address || (n.nodeId ? { nodeId: n.nodeId } : {});
    const shown = n.nodeId || JSON.stringify(addr);
    return `<button type="button" data-name="${escapeHtml(n.name)}" data-address="${escapeHtml(JSON.stringify(addr))}">
      <div>${escapeHtml(n.path || n.name)}</div>
      <div class="meta">${escapeHtml(shown)}${val}</div>
    </button>`;
  }).join("");
}

$("browse-list").addEventListener("click", (e) => {
  const btn = e.target.closest("[data-address]");
  if (!btn) return;
  $("point-form").querySelector("[name=name]").value = btn.dataset.name;
  $("point-address").value = JSON.stringify(parseJSON(btn.dataset.address, {}), null, 2);
  toast("已填入点地址，点「添加」保存");
});

$("btn-browse").addEventListener("click", async () => {
  if (!state.selectedId) return;
  $("btn-browse").disabled = true;
  try {
    const data = await api(`/api/v1/devices/${state.selectedId}/browse`, { method: "POST" });
    renderBrowse(data.items || []);
    toast("已浏览");
  } catch (err) {
    hideBrowse();
    toast(err.message, true);
  }
  $("btn-browse").disabled = !state.selectedId;
  await loadDevices();
});

$("btn-read").addEventListener("click", async () => {
  await refreshPointStatus();
});

$("btn-refresh").addEventListener("click", () => {
  if (state.page === "uplink") {
    loadUplink().catch((err) => toast(err.message, true));
    return;
  }
  refreshAll().catch((err) => toast(err.message, true));
});

function showPage(page) {
  state.page = page;
  $("page-devices").hidden = page !== "devices";
  $("page-uplink").hidden = page !== "uplink";
  document.querySelectorAll(".side [data-page]").forEach((btn) => {
    btn.classList.toggle("on", btn.dataset.page === page);
  });
  if (page === "uplink") {
    loadUplink().catch((err) => toast(err.message, true));
  }
}

function uplinkBody() {
  return {
    enabled: $("uplink-enabled").checked,
    broker: $("uplink-broker").value,
    clientId: $("uplink-client").value,
    topicPrefix: $("uplink-prefix").value,
    qos: Number($("uplink-qos").value),
    username: $("uplink-user").value,
    password: $("uplink-pass").value,
    cloudApiBase: $("cloud-api").value,
    grpcEnabled: $("grpc-enabled").checked,
    grpcTarget: $("grpc-target").value,
    grpcTls: $("grpc-tls").checked,
    grpcToken: $("grpc-token").value,
  };
}

function fillUplink(cfg) {
  $("uplink-enabled").checked = !!cfg.enabled;
  $("uplink-broker").value = cfg.broker || "";
  $("uplink-client").value = cfg.clientId || "";
  $("uplink-prefix").value = cfg.topicPrefix || "";
  $("uplink-qos").value = String(cfg.qos ?? 0);
  $("uplink-user").value = cfg.username || "";
  $("uplink-pass").value = cfg.password || "";
  $("cloud-api").value = cfg.cloudApiBase || "";
  $("grpc-enabled").checked = !!cfg.grpcEnabled;
  $("grpc-target").value = cfg.grpcTarget || "";
  $("grpc-tls").checked = !!cfg.grpcTls;
  $("grpc-token").value = cfg.grpcToken || "";
}

function renderMqttTopics(mqtt) {
  const box = $("mqtt-topics");
  const rows = [
    ["边端发 / 客户端订", mqtt.telemetry || "-"],
    ["客户端发 / 边端订", mqtt.command || "-"],
    ["边端回执", mqtt.ack || "-"],
  ];
  box.innerHTML = rows
    .map(([role, topic]) => `<span>${role} · ${topic}</span>`)
    .join("");
}

function renderMqttLog(messages) {
  const box = $("mqtt-io-log");
  if (!messages || !messages.length) {
    box.innerHTML = "<p class=\"hint\">还没有报文。先启用上报并保存，再用 MQTTX 对发。</p>";
    return;
  }
  box.innerHTML = messages
    .map((m) => {
      const dir = m.direction === "rx" ? "收" : "发";
      const cls = m.direction === "rx" ? "rx" : "tx";
      const t = m.time ? new Date(m.time).toLocaleTimeString() : "";
      return `<p class="${cls}">${t} ${dir} ${m.topic || ""}<br>${escapeHtml(m.payload || "")}</p>`;
    })
    .join("");
}

function applyMqttStatus(data) {
  const mqtt = data.mqtt || {};
  renderMqttTopics(mqtt);
  renderMqttLog(data.messages || []);
  if (mqtt.online) {
    $("mqtt-io-status").textContent = `已连接 · 订阅 ${mqtt.command || "-"}`;
    return;
  }
  if (mqtt.error) {
    $("mqtt-io-status").textContent = `未连接: ${mqtt.error}`;
    return;
  }
  $("mqtt-io-status").textContent = "未连接。勾选「启用上报」并保存。";
}

async function loadUplink() {
  const data = await api("/api/v1/northbound");
  const cfg = data.config || {};
  fillUplink(cfg);
  $("uplink-status").textContent = cfg.enabled
    ? `已保存 · 上报主题 ${cfg.topicPrefix || "-"}/telemetry`
    : "已保存 · 上报未启用（试连仍可用，收发测试需启用）";
  $("cloud-status").textContent = cfg.cloudApiBase
    ? `已保存 · 云入口 ${cfg.cloudApiBase}`
    : "未配置云 API 地址";
  $("grpc-status").textContent = cfg.grpcEnabled
    ? `已保存 · 控制面 ${cfg.grpcTarget || "-"}`
    : "已保存 · 控制面未启用（试连仍可用）";
  applyMqttStatus(data);
}

document.querySelector(".side").addEventListener("click", (e) => {
  const btn = e.target.closest("[data-page]");
  if (!btn) return;
  showPage(btn.dataset.page);
});

async function saveUplink() {
  const data = await api("/api/v1/northbound", {
    method: "PUT",
    body: JSON.stringify(uplinkBody()),
  });
  await loadUplink();
  return data;
}

$("uplink-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  try {
    const data = await saveUplink();
    toast(data.warning ? `已保存，MQTT 未连上: ${data.warning}` : "已保存");
  } catch (err) {
    toast(err.message, true);
  }
});

$("grpc-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  try {
    const data = await saveUplink();
    toast(data.warning ? `已保存，MQTT 未连上: ${data.warning}` : "已保存");
  } catch (err) {
    toast(err.message, true);
  }
});

$("cloud-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  try {
    const data = await saveUplink();
    toast(data.warning ? `已保存，MQTT 未连上: ${data.warning}` : "已保存");
  } catch (err) {
    toast(err.message, true);
  }
});

$("btn-cloud-test").addEventListener("click", async () => {
  $("cloud-status").textContent = "正在注册…";
  try {
    const data = await api("/api/v1/northbound/cloud/test", {
      method: "POST",
      body: JSON.stringify(uplinkBody()),
    });
    $("cloud-status").textContent = `注册成功 · ${data.apiBase}${data.path || ""}`;
    toast("云注册成功");
  } catch (err) {
    $("cloud-status").textContent = `注册失败: ${err.message}`;
    toast(err.message, true);
  }
});

$("btn-uplink-test").addEventListener("click", async () => {
  $("uplink-status").textContent = "正在试连…";
  try {
    const data = await api("/api/v1/northbound/test", {
      method: "POST",
      body: JSON.stringify(uplinkBody()),
    });
    $("uplink-status").textContent = `试连成功 · ${data.broker}`;
    toast("MQTT 试连成功");
  } catch (err) {
    $("uplink-status").textContent = `试连失败: ${err.message}`;
    toast(err.message, true);
  }
});

$("btn-grpc-test").addEventListener("click", async () => {
  $("grpc-status").textContent = "正在试连…";
  try {
    const data = await api("/api/v1/northbound/grpc/test", {
      method: "POST",
      body: JSON.stringify(uplinkBody()),
    });
    $("grpc-status").textContent = `试连成功 · ${data.target}`;
    toast("gRPC 试连成功");
  } catch (err) {
    $("grpc-status").textContent = `试连失败: ${err.message}`;
    toast(err.message, true);
  }
});

$("mqtt-send-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  try {
    const data = await api("/api/v1/northbound/send", {
      method: "POST",
      body: JSON.stringify({ payload: $("mqtt-payload").value }),
    });
    toast(`已发到 ${data.topic}`);
    await loadUplink();
  } catch (err) {
    toast(err.message, true);
  }
});

applyPreset();
refreshAll().catch((err) => toast(err.message, true));
setInterval(() => {
  if (state.page === "uplink") {
    loadUplink().catch(() => {});
    return;
  }
  if (state.page !== "devices") return;
  loadDevices().catch(() => {});
  loadMessages().catch(() => {});
}, 3000);
