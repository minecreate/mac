import paramiko

HOST = "192.168.3.28"
USER = "root"
PASSWORD = "root"


def run(client, cmd, timeout=60):
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    code = stdout.channel.recv_exit_status()
    print("=" * 60)
    print(cmd)
    print(f"exit={code}")
    if out.strip():
        print(out.rstrip())
    if err.strip():
        print("STDERR:", err.rstrip())
    return out, err, code


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASSWORD, timeout=10, allow_agent=False, look_for_keys=False)

    cmds = [
        "ufw status verbose || true",
        "iptables -L INPUT -n -v | head -40",
        "iptables -L DOCKER -n -v 2>/dev/null | head -40 || true",
        "iptables -L DOCKER-USER -n -v 2>/dev/null | head -40 || true",
        "firewall-cmd --list-all 2>/dev/null || true",
        "python3 - <<'PY'\nimport socket\ns=socket.create_connection(('127.0.0.1',5020),2); print('local ok', s.getpeername()); s.close()\ntry:\n s=socket.create_connection(('192.168.3.28',5020),2); print('ens33 ok', s.getpeername()); s.close()\nexcept Exception as e:\n print('ens33 fail', e)\nPY",
        # probe modbus read holding reg 0 from localhost with a tiny python if available
        "docker logs --tail 30 modbus-slave 2>&1 || true",
        # open firewall if ufw active
        "command -v ufw >/dev/null && ufw allow 5020/tcp && ufw status || true",
        # also nft
        "nft list ruleset 2>/dev/null | head -80 || true",
    ]
    for cmd in cmds:
        run(c, cmd)
    c.close()


if __name__ == "__main__":
    main()
