const PRESETS = {
  opcua: {
    endpoint: "opc.tcp://192.168.3.37:4840",
    username: "mplc",
    address: { nodeId: "ns=4;s=|var|CODESYS Control Win V3 x64.Application.PLC_PRG.i0" },
  },
  modbus: {
    endpoint: "192.168.3.28:5020",
    address: { func: 3, offset: 0, type: "int16" },
  },
  mqtt: {
    endpoint: "tcp://127.0.0.1:1883",
    address: { topic: "lab/line1/status" },
  },
};
