/// <reference types="jest" />
import {
  BALANCE_COMMAND,
  buildBleNameCommand,
  buildReadCommand,
  buildSwitchCommand,
  CMD_BASIC_INFO,
  CMD_CELL_VOLTAGES,
  decodeFrame,
  FrameParser,
  parseBasicInfo,
  parseCellVoltages,
  parseNameResponse,
} from './protocol';

const CAPTURED_BASIC_INFO_RESPONSE = Uint8Array.from([
  0xdd, 0x03, 0x00, 0x26, 0x1f, 0x3a, 0x00, 0x00, 0x0d, 0x4b, 0x10, 0x68, 0x00, 0x25, 0x32, 0xf3,
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7d, 0x51, 0x03, 0x18, 0x03, 0x0b, 0xd0, 0x0b, 0xc5, 0x0b,
  0xc6, 0x00, 0x00, 0x00, 0x10, 0x68, 0x0d, 0x4b, 0x00, 0x00, 0xf9, 0x2f, 0x77,
]);

function responseFrame(cmd: number, data: number[]): Uint8Array {
  const sum = data.reduce((a, b) => a + b, data.length);
  const csum = (~sum + 1) & 0xffff;
  return Uint8Array.from([0xdd, cmd, 0x00, data.length, ...data, csum >> 8, csum & 0xff, 0x77]);
}

const basicInfoData = [
  0x14, 0xa0, // total voltage 52.80 V
  0xfe, 0xa2, // current -3.50 A (signed)
  0x11, 0xa8, // remaining 45.20 Ah
  0x27, 0x10, // nominal 100.00 Ah
  0x00, 0x0c, // 12 cycles
  0x30, 0xb1, // production date 2024-05-17
  0x00, 0x01, 0x00, 0x00, // balance bits: cell 1
  0x00, 0x04, // protection: pack overvoltage
  0x15, // firmware 1.5
  0x4b, // SOC 75 %
  0x03, // charge and discharge FET on
  0x04, // 4 cells
  0x02, // 2 NTC sensors
  0x0b, 0xa5, // 25.0 °C
  0x0b, 0xd7, // 30.0 °C
];

describe('checksum and read commands', () => {
  it('builds the standard basic-info read frame', () => {
    expect(Array.from(buildReadCommand(CMD_BASIC_INFO))).toEqual([0xdd, 0xa5, 0x03, 0x00, 0xff, 0xfd, 0x77]);
  });

  it('builds the cell-voltage read frame', () => {
    expect(Array.from(buildReadCommand(CMD_CELL_VOLTAGES))).toEqual([0xdd, 0xa5, 0x04, 0x00, 0xff, 0xfc, 0x77]);
  });

  it('rejects frames with a bad checksum', () => {
    const frame = responseFrame(CMD_CELL_VOLTAGES, [0x0d, 0x2c]);
    frame[frame.length - 2] ^= 0x01;
    expect(decodeFrame(frame)).toBeNull();
  });
});

describe('write commands', () => {
  it('turns both switches on with a zero lock byte', () => {
    expect(Array.from(buildSwitchCommand(true, true))).toEqual([0xdd, 0x5a, 0xe1, 0x01, 0x00, 0xff, 0x1e, 0x77]);
  });

  it('sets the charge lock bit when charging is off', () => {
    expect(Array.from(buildSwitchCommand(false, true))).toEqual([0xdd, 0x5a, 0xe1, 0x01, 0x01, 0xff, 0x1d, 0x77]);
  });

  it('builds the auto-balance control command', () => {
    expect(Array.from(BALANCE_COMMAND)).toEqual([0xdd, 0x5a, 0x0a, 0x02, 0x07, 0x00, 0xff, 0xed, 0x77]);
  });

  it('builds the BMS Bluetooth name frame with a one-byte checksum', () => {
    expect(Array.from(buildBleNameCommand('A'))).toEqual([0xff, 0xaa, 0x07, 0x01, 0x41, 0x49]);
  });

  it('reads the name-write acknowledgement status', () => {
    expect(parseNameResponse(Uint8Array.from([0xff, 0xaa, 0x07, 0x00, 0x00]))?.status).toBe(0);
    expect(parseNameResponse(Uint8Array.from([0xff, 0xaa, 0x07, 0x01, 0x00]))?.status).toBe(1);
    expect(parseNameResponse(Uint8Array.from([0xdd, 0x03, 0x00]))).toBeNull();
  });

  it('encodes non-ASCII names as UTF-8', () => {
    expect(Array.from(buildBleNameCommand('ñ').slice(4, 6))).toEqual([0xc3, 0xb1]);
  });
});

describe('captured hardware responses', () => {
  it('decodes a real basic-info response (cmd 0x03 second, status 0x00 third)', () => {
    const frame = decodeFrame(CAPTURED_BASIC_INFO_RESPONSE);
    expect(frame).not.toBeNull();
    expect(frame?.cmd).toBe(CMD_BASIC_INFO);
    expect(frame?.status).toBe(0);
    expect(frame?.data.length).toBe(0x26);
  });
});

describe('FrameParser', () => {
  it('reassembles a frame split across notifications after garbage bytes', () => {
    const frame = responseFrame(CMD_CELL_VOLTAGES, [0x0d, 0x2c, 0x0d, 0x2d]);
    const parser = new FrameParser();
    const chunks = [
      Uint8Array.from([0x00, 0x12]),
      frame.slice(0, 3),
      frame.slice(3),
    ];
    const frames = chunks.flatMap((chunk) => parser.push(chunk));
    expect(frames).toHaveLength(1);
    expect(frames[0].cmd).toBe(CMD_CELL_VOLTAGES);
    expect(Array.from(frames[0].data)).toEqual([0x0d, 0x2c, 0x0d, 0x2d]);
  });
});

describe('parseBasicInfo', () => {
  it('decodes voltage, current, capacity, SOC, FET state and temperatures', () => {
    const frame = decodeFrame(responseFrame(CMD_BASIC_INFO, basicInfoData));
    expect(frame).not.toBeNull();
    const info = parseBasicInfo(frame!.data);
    expect(info).not.toBeNull();
    expect(info!.totalVoltage).toBeCloseTo(52.8);
    expect(info!.current).toBeCloseTo(-3.5);
    expect(info!.remainingCapacity).toBeCloseTo(45.2);
    expect(info!.nominalCapacity).toBeCloseTo(100);
    expect(info!.cycles).toBe(12);
    expect(info!.productionDate).toBe('2024-05-17');
    expect(info!.soc).toBe(75);
    expect(info!.firmwareVersion).toBe('1.5');
    expect(info!.chargeOn).toBe(true);
    expect(info!.dischargeOn).toBe(true);
    expect(info!.cellCount).toBe(4);
    expect(info!.balancing).toEqual([true, false, false, false]);
    expect(info!.temperatures).toEqual([25, 30]);
  });

  it('maps protection bits to labels', () => {
    const info = parseBasicInfo(Uint8Array.from(basicInfoData))!;
    const active = info.protections.filter((p) => p.active).map((p) => p.label);
    expect(active).toEqual(['Sobretensión de batería']);
  });

  it('returns null when the payload is truncated', () => {
    expect(parseBasicInfo(Uint8Array.from(basicInfoData.slice(0, 20)))).toBeNull();
  });
});

describe('parseCellVoltages', () => {
  it('converts millivolt pairs to volts', () => {
    expect(parseCellVoltages(Uint8Array.from([0x0d, 0x2c, 0x0d, 0x2d]))).toEqual([3.372, 3.373]);
  });
});
