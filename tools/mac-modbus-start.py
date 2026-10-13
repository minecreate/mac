import json
import socket
import time
import urllib.request

import paramiko

HOST = "192.168.3.28"
DEVICE_ID = "06a63c60-a479-4acc-875e-f2d053ff8d7c"
EDGE = "http://127.0.0.1:8081"


def ssh(client, cmd, timeout=90):
    print("=" * 60)
    print(cmd[:200])
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    code = stdout.channel.recv_exit_status()
    print(f"exit={code}")
    if out.strip():
        print(out.rstrip()[:2500])
    if err.strip():
        print("STDERR:", err.rstrip()[:1500])
    return out, err, code


def tcp_ok(host, port, timeout=5.0):
    try:
        with socket.create_connection((host, port), timeout=timeout):
            print(f"TCP {host}:{port} OK")
            return True
    except OSError as e:
        print(f"TCP {host}:{port} FAIL: {e}")
        return False


def http_json(method, url, timeout=20.0):
    req = urllib.request.Request(url, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode(errors="replace")
            return resp.status, json.loads(raw) if raw else None
    except Exception as e:
        if hasattr(e, "read"):
            raw = e.read().decode(errors="replace")
            try:
                return getattr(e, "code", None), json.loads(raw)
            except Exception:
                return getattr(e, "code", None), raw
        return None, str(e)


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username="root", password="root", timeout=10, allow_agent=False, look_for_keys=False)

    ssh(c, "pkill -f /usr/local/bin/modbus-host-proxy.py || true")
    ssh(c, "docker rm -f modbus-slave || true")
    # host network + image default config (/app/modbus_server.json)
    ssh(
        c,
        "docker run -d --name modbus-slave --restart unless-stopped "
        "--network host oitc/modbus-server:latest",
    )
    time.sleep(2)
    ssh(c, "docker ps -a --filter name=modbus-slave --format '{{.Names}} {{.Status}}'")
    ssh(c, "docker logs --tail 30 modbus-slave 2>&1")
    ssh(c, "ss -lntp | grep 5020 || true")
    ssh(
        c,
        "python3 - <<'PY'\nimport socket\n"
        "for h in ('127.0.0.1','192.168.3.28'):\n"
        "  try:\n"
        "    s=socket.create_connection((h,5020),2); print(h,'ok'); s.close()\n"
        "  except Exception as e:\n"
        "    print(h,'fail',e)\n"
        "PY",
    )
    c.close()

    print("== Windows ==")
    ok = False
    for i in range(6):
        ok = tcp_ok(HOST, 5020, 4)
        if ok:
            break
        time.sleep(1)
    if not ok:
        return 1

    print("connect", http_json("POST", f"{EDGE}/api/v1/devices/{DEVICE_ID}/connect"))
    print("read", http_json("POST", f"{EDGE}/api/v1/devices/{DEVICE_ID}/read"))
    code, payload = http_json("GET", f"{EDGE}/api/v1/devices/{DEVICE_ID}")
    if isinstance(payload, dict):
        print(
            json.dumps(
                {
                    "name": payload.get("name"),
                    "online": payload.get("online"),
                    "endpoint": payload.get("endpoint"),
                    "lastHeartbeat": payload.get("lastHeartbeat"),
                    "heartbeatAgeSec": payload.get("heartbeatAgeSec"),
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
