import socket
import time

import paramiko

HOST = "192.168.3.28"


def ssh_exec(cmd, timeout=30):
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username="root", password="root", timeout=10, allow_agent=False, look_for_keys=False)
    # start background listener via nohup
    transport = c.get_transport()
    chan = transport.open_session()
    chan.exec_command(cmd)
    # give it a moment then return client kept? we'll close after tests using new client for checks
    time.sleep(0.5)
    return c, chan


def tcp_ok(host, port, timeout=4.0):
    try:
        with socket.create_connection((host, port), timeout=timeout):
            print(f"TCP {host}:{port} OK")
            return True
    except OSError as e:
        print(f"TCP {host}:{port} FAIL: {e}")
        return False


def main():
    # Start a raw host python TCP server on 5021 (not docker)
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username="root", password="root", timeout=10, allow_agent=False, look_for_keys=False)

    # kill old probe
    c.exec_command("pkill -f 'probe5021' || true")
    time.sleep(0.3)

    # nohup background
    cmd = (
        "nohup python3 -c \"import socket; "
        "s=socket.socket(); s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1); "
        "s.bind(('0.0.0.0',5021)); s.listen(5); open('/tmp/probe5021.ready','w').write('ok'); "
        "print('ready', flush=True); "
        "conn,addr=s.accept(); print('accepted',addr, flush=True); "
        "conn.send(b'ok'); conn.close(); s.close()\" "
        ">/tmp/probe5021.log 2>&1 </dev/null & echo $!"
    )
    stdin, stdout, stderr = c.exec_command(cmd)
    pid = stdout.read().decode().strip()
    print("probe pid", pid)
    time.sleep(1)
    stdin, stdout, stderr = c.exec_command("cat /tmp/probe5021.ready 2>/dev/null; ss -lntp | grep 5021 || true; cat /tmp/probe5021.log 2>/dev/null || true")
    print(stdout.read().decode())
    c.close()

    print("== Windows probes ==")
    tcp_ok(HOST, 22, 3)
    tcp_ok(HOST, 5021, 5)
    tcp_ok(HOST, 5020, 5)

    # read accept log
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username="root", password="root", timeout=10, allow_agent=False, look_for_keys=False)
    stdin, stdout, stderr = c.exec_command("cat /tmp/probe5021.log 2>/dev/null || true")
    print("probe log:", stdout.read().decode())
    c.exec_command("pkill -f 'probe5021' || true; pkill -f \"bind(('0.0.0.0',5021))\" || true")
    c.close()


if __name__ == "__main__":
    main()
