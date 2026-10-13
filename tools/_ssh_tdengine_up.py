"""在 Ubuntu 虚拟机 (192.168.3.45) 上拉起 LineNexus TDengine。

用法（PowerShell）:
  $env:LINENEXUS_VM_SSH_PASS = '你的密码'
  python tools/_ssh_tdengine_up.py
"""
from __future__ import annotations

import os
import sys

import paramiko

HOST = os.environ.get("LINENEXUS_VM_HOST", "192.168.3.45")
USER = os.environ.get("LINENEXUS_VM_SSH_USER", "mac")
PASSWORD = os.environ.get("LINENEXUS_VM_SSH_PASS", "")

CMD = r"""
set -e
mkdir -p ~/linenexus-tdengine
cat > ~/linenexus-tdengine/docker-compose.yml <<'EOF'
services:
  tdengine:
    image: tdengine/tdengine:3.3.2.0
    container_name: linenexus-tdengine
    hostname: tdengine
    ports:
      - "6030:6030"
      - "6041:6041"
      - "6043:6043"
      - "6060:6060"
    environment:
      TAOS_FQDN: tdengine
    volumes:
      - tdengine-data:/var/lib/taos
    restart: unless-stopped
    ulimits:
      nofile:
        soft: 65535
        hard: 65535
volumes:
  tdengine-data:
EOF
cd ~/linenexus-tdengine
docker compose up -d
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  sleep 2
  if docker exec linenexus-tdengine taos -s "show dnodes;" 2>&1 | grep -q "ready"; then
    echo "DNODES_READY"
    docker exec linenexus-tdengine taos -s "show dnodes;"
    exit 0
  fi
  echo "wait $i"
done
echo "DNODES_NOT_READY"
docker logs --tail 40 linenexus-tdengine
exit 1
"""


def main() -> int:
    if not PASSWORD:
        print("请设置环境变量 LINENEXUS_VM_SSH_PASS", file=sys.stderr)
        return 2
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(
        HOST,
        username=USER,
        password=PASSWORD,
        timeout=15,
        allow_agent=False,
        look_for_keys=False,
    )
    _, stdout, stderr = c.exec_command(CMD, timeout=360)
    print(stdout.read().decode())
    err = stderr.read().decode()
    if err:
        print("STDERR:", err[-2000:])
    code = stdout.channel.recv_exit_status()
    c.close()
    return code


if __name__ == "__main__":
    raise SystemExit(main())
