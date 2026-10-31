export const BMS_SERVICE_UUID = '0000ff00-0000-1000-8000-00805f9b34fb';
export const BMS_NOTIFY_UUID = '0000ff01-0000-1000-8000-00805f9b34fb';
export const BMS_WRITE_UUID = '0000ff02-0000-1000-8000-00805f9b34fb';

export const CMD_BASIC_INFO = 0x03;
export const CMD_CELL_VOLTAGES = 0x04;

const FRAME_START = 0xdd;
const FRAME_END = 0x77;
const READ_MODE = 0xa5;
const WRITE_MODE = 0x5a;
const FRAME_OVERHEAD = 7;

const PROTECTION_LABELS = [
  'Sobretensión de celda',
  'Subtensión de celda',
  'Sobretensión de batería',
  'Subtensión de batería',
  'Sobretemperatura en carga',
  'Baja temperatura en carga',
  'Sobretemperatura en descarga',
  'Baja temperatura en descarga',
  'Sobrecorriente en carga',
  'Sobrecorriente en descarga',
  'Cortocircuito',
  'Error de IC',
  'MOS bloqueado por software',
];

export type Frame = { status: number; cmd: number; data: Uint8Array };

export type Protection = { label: string; active: boolean };

export type BasicInfo = {
  totalVoltage: number;
  /** Amperes. Positive while charging, negative while discharging. */
  current: number;
  /** Remaining capacity in Ah. */
  remainingCapacity: number;
  /** Nominal capacity in Ah. */
  nominalCapacity: number;
  cycles: number;
  productionDate: string;
  soc: number;
  firmwareVersion: string;
  chargeOn: boolean;
  dischargeOn: boolean;
  cellCount: number;
  balancing: boolean[];
  protections: Protection[];
  temperatures: number[];
};

export function checksum(cmd: number, data: ArrayLike<number>): number {
  let sum = cmd + data.length;
  for (let i = 0; i < data.length; i++) sum += data[i];
  return (~sum + 1) & 0xffff;
}

export function buildReadCommand(cmd: number): Uint8Array {
  const sum = checksum(cmd, []);
  return Uint8Array.from([FRAME_START, READ_MODE, cmd, 0x00, sum >> 8, sum & 0xff, FRAME_END]);
}

export const CMD_CONTROL = 0x0a;
export const CMD_SWITCH = 0xe1;
const BLE_NAME_CMD = 0x07;

export function buildWriteCommand(cmd: number, content: number[]): Uint8Array {
  const sum = checksum(cmd, content);
  return Uint8Array.from([
    FRAME_START,
    WRITE_MODE,
    cmd,
    content.length,
    ...content,
    sum >> 8,
    sum & 0xff,
    FRAME_END,
  ]);
}

/** Charge and discharge MOSFET switches. A bit set to 1 turns that path off. */
export function buildSwitchCommand(chargeOn: boolean, dischargeOn: boolean): Uint8Array {
  const lock = (chargeOn ? 0 : 1) | ((dischargeOn ? 0 : 1) << 1);
  return buildWriteCommand(CMD_SWITCH, [lock]);
}

export const BALANCE_COMMAND = buildWriteCommand(CMD_CONTROL, [0x07, 0x00]);
export const CLEAR_PROTECTION_COMMAND = buildWriteCommand(CMD_CONTROL, [0x04, 0x00]);

/** Bluetooth name stored in the BMS. Uses its own frame format (FF AA 07). */
export function buildBleNameCommand(name: string): Uint8Array {
  const bytes: number[] = [];
  const encoded = encodeURIComponent(name);
  for (let i = 0; i < encoded.length; i++) {
    if (encoded[i] === '%') {
      bytes.push(parseInt(encoded.slice(i + 1, i + 3), 16));
      i += 2;
    } else {
      bytes.push(encoded.charCodeAt(i));
    }
  }
  const sum = bytes.reduce((acc, byte) => acc + byte, 0) + ((bytes.length + BLE_NAME_CMD) & 0xff);
  return Uint8Array.from([0xff, 0xaa, BLE_NAME_CMD, bytes.length, ...bytes, sum & 0xff]);
}

export const CMD_BLE_NAME = BLE_NAME_CMD;

/** Name-write acknowledgement: FF AA 07 <status>. Status 0 means the BMS stored the name. */
export function parseNameResponse(bytes: ArrayLike<number>): Frame | null {
  if (bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xaa || bytes[2] !== BLE_NAME_CMD) {
    return null;
  }
  return { status: bytes[3], cmd: BLE_NAME_CMD, data: Uint8Array.from(Array.from(bytes).slice(4)) };
}

export function decodeFrame(bytes: ArrayLike<number>): Frame | null {
  if (bytes.length < FRAME_OVERHEAD) return null;
  if (bytes[0] !== FRAME_START || bytes[bytes.length - 1] !== FRAME_END) return null;
  const cmd = bytes[1];
  const status = bytes[2];
  const length = bytes[3];
  if (bytes.length !== length + FRAME_OVERHEAD) return null;
  const data = Array.from(bytes).slice(4, 4 + length);
  const received = (bytes[4 + length] << 8) | bytes[5 + length];
  if (checksum(0, data) !== received) return null;
  return { status, cmd, data: Uint8Array.from(data) };
}

/** Reassembles BLE notification chunks into complete BMS response frames. */
export class FrameParser {
  private buffer: number[] = [];

  reset(): void {
    this.buffer = [];
  }

  push(chunk: Uint8Array): Frame[] {
    this.buffer.push(...chunk);
    const frames: Frame[] = [];
    while (this.buffer.length >= FRAME_OVERHEAD) {
      const start = this.buffer.indexOf(FRAME_START);
      if (start === -1) {
        this.buffer = [];
        break;
      }
      this.buffer.splice(0, start);
      if (this.buffer.length < 4) break;
      const total = this.buffer[3] + FRAME_OVERHEAD;
      if (this.buffer.length < total) break;
      const frame = decodeFrame(this.buffer.slice(0, total));
      if (frame) {
        frames.push(frame);
        this.buffer.splice(0, total);
      } else {
        this.buffer.splice(0, 1);
      }
    }
    return frames;
  }
}

export function parseBasicInfo(d: Uint8Array): BasicInfo | null {
  if (d.length < 23) return null;
  const ntcCount = d[22];
  if (d.length < 23 + ntcCount * 2) return null;

  const u16 = (i: number) => (d[i] << 8) | d[i + 1];
  const s16 = (i: number) => (u16(i) << 16) >> 16;
  const cellCount = d[21];

  const balancing = Array.from({ length: cellCount }, (_, i) => {
    const byteIndex = [13, 12, 15, 14][Math.floor(i / 8)];
    return ((d[byteIndex] >> (i % 8)) & 1) === 1;
  });

  const protections = PROTECTION_LABELS.map((label, i) => {
    const byte = i < 8 ? d[17] : d[16];
    return { label, active: ((byte >> (i % 8)) & 1) === 1 };
  });

  const temperatures = Array.from({ length: ntcCount }, (_, i) => (u16(23 + i * 2) - 2731) / 10);

  const dateCode = u16(10);
  const year = (dateCode >> 9) + 2000;
  const month = String((dateCode >> 5) & 0x0f).padStart(2, '0');
  const day = String(dateCode & 0x1f).padStart(2, '0');

  return {
    totalVoltage: u16(0) / 100,
    current: s16(2) / 100,
    remainingCapacity: u16(4) / 100,
    nominalCapacity: u16(6) / 100,
    cycles: u16(8),
    productionDate: `${year}-${month}-${day}`,
    soc: d[19],
    firmwareVersion: `${d[18] >> 4}.${d[18] & 0x0f}`,
    chargeOn: (d[20] & 0x01) === 0x01,
    dischargeOn: (d[20] & 0x02) === 0x02,
    cellCount,
    balancing,
    protections,
    temperatures,
  };
}

/** Cell voltages in volts, one entry per cell. */
export function parseCellVoltages(d: Uint8Array): number[] {
  const cells: number[] = [];
  for (let i = 0; i + 1 < d.length; i += 2) {
    cells.push(((d[i] << 8) | d[i + 1]) / 1000);
  }
  return cells;
}
