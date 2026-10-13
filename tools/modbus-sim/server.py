"""Minimal Modbus TCP slave for local debug (port 5020). Holding register 0 = 100."""
from __future__ import annotations

import logging

from pymodbus.datastore import (
    ModbusDeviceContext,
    ModbusSequentialDataBlock,
    ModbusServerContext,
)
from pymodbus.server import StartTcpServer

logging.basicConfig(level=logging.INFO)


def main() -> None:
    holding = [100 + i * 100 for i in range(16)]
    store = ModbusDeviceContext(
        di=ModbusSequentialDataBlock(0, [0] * 100),
        co=ModbusSequentialDataBlock(0, [0] * 100),
        hr=ModbusSequentialDataBlock(0, holding),
        ir=ModbusSequentialDataBlock(0, [0] * 100),
    )
    context = ModbusServerContext(devices=store, single=True)
    print("modbus-sim listening on 0.0.0.0:5020 (unit=1, HR0=100)", flush=True)
    StartTcpServer(context=context, address=("0.0.0.0", 5020))


if __name__ == "__main__":
    main()
