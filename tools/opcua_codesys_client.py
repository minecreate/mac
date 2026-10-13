"""Lab OPC UA client for CODESYS Control Win.

OpenUaExplorer cannot Discover against this PLC: the server only advertises
Username tokens on encrypted endpoints, and that GUI discovers with Anonymous.
This tool skips discovery and connects with username/password.
"""

from __future__ import annotations

import asyncio
import threading
import tkinter as tk
from tkinter import messagebox, ttk

from pathlib import Path

from cryptography.x509.oid import ExtendedKeyUsageOID

from asyncua import Client, ua
from asyncua.crypto.cert_gen import setup_self_signed_certificate
from asyncua.crypto.security_policies import SecurityPolicyBasic256Sha256
from asyncua.crypto.validator import CertificateValidator, CertificateValidatorOptions

DEFAULT_URL = "opc.tcp://192.168.3.37:4840"
DEFAULT_USER = "mplc"
DEFAULT_POLICY = "Basic256Sha256"
DEFAULT_MODE = "SignAndEncrypt"
APP_URI = "urn:linenexus:opcua-client"
CERT_DIR = Path.home() / ".linenexus" / "opcua"
CLIENT_CERT = CERT_DIR / "client_cert.der"
CLIENT_KEY = CERT_DIR / "client_key.pem"
SERVER_CERT = CERT_DIR / "codesys_server.der"


class OpcUaSession:
    def __init__(self) -> None:
        self.client: Client | None = None
        self.loop = asyncio.new_event_loop()
        self.thread = threading.Thread(target=self._run_loop, daemon=True)
        self.thread.start()

    def _run_loop(self) -> None:
        asyncio.set_event_loop(self.loop)
        self.loop.run_forever()

    def run(self, coro):
        future = asyncio.run_coroutine_threadsafe(coro, self.loop)
        return future.result(timeout=40)

    async def _ensure_client_cert(self) -> None:
        CERT_DIR.mkdir(parents=True, exist_ok=True)
        await setup_self_signed_certificate(
            CLIENT_KEY,
            CLIENT_CERT,
            APP_URI,
            "localhost",
            [ExtendedKeyUsageOID.CLIENT_AUTH],
            {"countryName": "CN", "commonName": "LineNexus OPC UA Client"},
        )

    async def _save_server_cert(self, url: str) -> None:
        probe = Client(url=url)
        endpoints = await probe.connect_and_get_server_endpoints()
        try:
            await probe.disconnect()
        except Exception:
            pass
        chosen = None
        for ep in endpoints:
            policy = str(ep.SecurityPolicyUri).split("#")[-1]
            if policy == "Basic256Sha256" and int(ep.SecurityMode) == 3:
                chosen = ep
                break
        if chosen is None and endpoints:
            chosen = endpoints[0]
        if chosen is None or not chosen.ServerCertificate:
            raise RuntimeError("未从 PLC 拿到服务器证书")
        SERVER_CERT.write_bytes(chosen.ServerCertificate)

    async def connect(self, url: str, user: str, password: str) -> None:
        await self.disconnect()
        await self._ensure_client_cert()
        await self._save_server_cert(url)
        client = Client(url=url)
        client.application_uri = APP_URI
        client.set_user(user)
        client.set_password(password)
        client.certificate_validator = CertificateValidator(
            CertificateValidatorOptions.TIME_RANGE | CertificateValidatorOptions.PEER_SERVER
        )
        await client.set_security(
            SecurityPolicyBasic256Sha256,
            str(CLIENT_CERT),
            str(CLIENT_KEY),
            None,
            str(SERVER_CERT),
            ua.MessageSecurityMode.SignAndEncrypt,
        )
        await client.connect()
        self.client = client

    async def disconnect(self) -> None:
        if self.client is None:
            return
        try:
            await self.client.disconnect()
        finally:
            self.client = None

    async def browse_tree(self, max_nodes: int = 80) -> list[str]:
        if self.client is None:
            raise RuntimeError("not connected")
        lines: list[str] = []

        async def walk(node, depth: int = 0) -> None:
            if len(lines) >= max_nodes or depth > 7:
                return
            try:
                name = await node.read_browse_name()
                display = f"{'  ' * depth}{name.Name}"
                try:
                    cls = await node.read_node_class()
                    if cls == ua.NodeClass.Variable:
                        value = await node.read_value()
                        display += f"  =  {value}"
                except Exception:
                    pass
                lines.append(display)
            except Exception as exc:
                lines.append(f"{'  ' * depth}? ({exc})")
                return
            try:
                children = await node.get_children()
            except Exception:
                return
            for child in children[:20]:
                await walk(child, depth + 1)

        await walk(self.client.nodes.objects)
        return lines


class App(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("CODESYS OPC UA 连接")
        self.geometry("720x520")
        self.session = OpcUaSession()

        pad = {"padx": 8, "pady": 4}
        frm = ttk.Frame(self)
        frm.pack(fill=tk.X, **pad)

        ttk.Label(frm, text="地址").grid(row=0, column=0, sticky=tk.W)
        self.url = ttk.Entry(frm, width=56)
        self.url.insert(0, DEFAULT_URL)
        self.url.grid(row=0, column=1, columnspan=3, sticky=tk.EW, **pad)

        ttk.Label(frm, text="用户名").grid(row=1, column=0, sticky=tk.W)
        self.user = ttk.Entry(frm, width=20)
        self.user.insert(0, DEFAULT_USER)
        self.user.grid(row=1, column=1, sticky=tk.W, **pad)

        ttk.Label(frm, text="密码").grid(row=1, column=2, sticky=tk.W)
        self.password = ttk.Entry(frm, width=20, show="*")
        self.password.grid(row=1, column=3, sticky=tk.W, **pad)

        btns = ttk.Frame(self)
        btns.pack(fill=tk.X, **pad)
        ttk.Button(btns, text="连接并浏览", command=self.on_connect).pack(side=tk.LEFT, padx=4)
        ttk.Button(btns, text="断开", command=self.on_disconnect).pack(side=tk.LEFT, padx=4)

        ttk.Label(self, text=f"固定策略：{DEFAULT_POLICY} + {DEFAULT_MODE}（与当前 CODESYS 一致）").pack(
            anchor=tk.W, padx=8
        )

        self.status = ttk.Label(self, text="未连接")
        self.status.pack(anchor=tk.W, padx=8, pady=4)

        self.tree = tk.Text(self, wrap=tk.NONE)
        self.tree.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)

        self.protocol("WM_DELETE_WINDOW", self.on_close)

    def on_connect(self) -> None:
        url = self.url.get().strip()
        user = self.user.get().strip()
        password = self.password.get()
        if not url or not user:
            messagebox.showwarning("缺少参数", "请填写地址和用户名")
            return
        try:
            self.status.config(text="正在连接...")
            self.update_idletasks()
            self.session.run(self.session.connect(url, user, password))
            lines = self.session.run(self.session.browse_tree())
            self.tree.delete("1.0", tk.END)
            self.tree.insert(tk.END, "\n".join(lines))
            self.status.config(text=f"已连接  {url}  用户 {user}")
        except Exception as exc:
            self.status.config(text="连接失败")
            messagebox.showerror("连接失败", str(exc))

    def on_disconnect(self) -> None:
        try:
            self.session.run(self.session.disconnect())
        except Exception:
            pass
        self.status.config(text="未连接")

    def on_close(self) -> None:
        try:
            self.session.run(self.session.disconnect())
        except Exception:
            pass
        self.destroy()


if __name__ == "__main__":
    App().mainloop()
