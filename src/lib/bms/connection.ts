import { PermissionsAndroid, Platform } from 'react-native';
import { BleManager, State, type Device, type Subscription } from 'react-native-ble-plx';

import { t } from '../i18n';
import {
  BMS_NOTIFY_UUID,
  BMS_SERVICE_UUID,
  BMS_WRITE_UUID,
  buildReadCommand,
  CMD_BASIC_INFO,
  CMD_CELL_VOLTAGES,
  type BasicInfo,
  type Frame,
  FrameParser,
  parseBasicInfo,
  parseCellVoltages,
  parseNameResponse,
} from './protocol';

const CONNECT_TIMEOUT_MS = 20000;
const NOTIFY_SETTLE_MS = 1000;

export type ScannedBattery = { id: string; name: string; rssi: number | null };

export type BmsReading = { basic: BasicInfo; cells: number[]; receivedAt: number };

let manager: BleManager | null = null;

function getManager(): BleManager {
  manager ??= new BleManager();
  return manager;
}

export async function requestBlePermissions(): Promise<boolean> {
  if (Platform.OS !== 'android') return true;
  const permissions =
    typeof Platform.Version === 'number' && Platform.Version >= 31
      ? [
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
        ]
      : [PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION];
  const result = await PermissionsAndroid.requestMultiple(permissions);
  return Object.values(result).every((value) => value === PermissionsAndroid.RESULTS.GRANTED);
}

async function settledBluetoothState(): Promise<State> {
  const manager = getManager();
  for (let attempt = 0; attempt < 25; attempt++) {
    const state = await manager.state();
    if (state !== State.Unknown && state !== State.Resetting) return state;
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  return State.Unknown;
}

export async function isBluetoothOn(): Promise<boolean> {
  return (await settledBluetoothState()) === State.PoweredOn;
}

export function scanForBatteries(
  onFound: (battery: ScannedBattery) => void,
  onError: (message: string) => void,
): () => void {
  const seen = new Set<string>();
  getManager().startDeviceScan([BMS_SERVICE_UUID], null, (error, device) => {
    if (error) {
      onError(error.message);
      return;
    }
    if (!device || seen.has(device.id)) return;
    seen.add(device.id);
    onFound({
      id: device.id,
      name: device.name ?? device.localName ?? 'Batería BMS',
      rssi: device.rssi,
    });
  });
  return () => getManager().stopDeviceScan();
}

function toBase64(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

function fromBase64(value: string): Uint8Array {
  return Uint8Array.from(atob(value), (char) => char.charCodeAt(0));
}

export class BmsSession {
  private device: Device | null = null;
  private notifications: Subscription | null = null;
  private parser = new FrameParser();
  private waiters = new Map<number, (frame: Frame) => void>();

  constructor(private readonly deviceId: string) {}

  async connect(): Promise<void> {
    const manager = getManager();
    if ((await settledBluetoothState()) !== State.PoweredOn) {
      throw new Error(t('ble.bluetoothOff'));
    }
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(() => {
        manager.cancelDeviceConnection(this.deviceId).catch(() => undefined);
        reject(new Error(t('ble.noConnect')));
      }, CONNECT_TIMEOUT_MS);
    });
    const link = manager
      .connectToDevice(this.deviceId)
      .then((connected) => connected.discoverAllServicesAndCharacteristics());
    try {
      this.device = await Promise.race([link, timeout]);
    } finally {
      clearTimeout(timer);
    }
    this.parser.reset();
    this.notifications = this.device.monitorCharacteristicForService(
      BMS_SERVICE_UUID,
      BMS_NOTIFY_UUID,
      (error, characteristic) => {
        if (error || !characteristic?.value) return;
        const bytes = fromBase64(characteristic.value);
        const nameAck = parseNameResponse(bytes);
        if (nameAck) {
          const waiter = this.waiters.get(nameAck.cmd);
          if (waiter) {
            this.waiters.delete(nameAck.cmd);
            waiter(nameAck);
          }
          return;
        }
        for (const frame of this.parser.push(bytes)) {
          const waiter = this.waiters.get(frame.cmd);
          if (waiter) {
            this.waiters.delete(frame.cmd);
            waiter(frame);
          }
        }
      },
    );
    await new Promise((resolve) => setTimeout(resolve, NOTIFY_SETTLE_MS));
  }

  async read(): Promise<BmsReading> {
    const basicFrame = await this.request(CMD_BASIC_INFO);
    const cellFrame = await this.request(CMD_CELL_VOLTAGES);
    const basic = parseBasicInfo(basicFrame.data);
    if (!basic) throw new Error(t('ble.invalidData'));
    return { basic, cells: parseCellVoltages(cellFrame.data), receivedAt: Date.now() };
  }

  async disconnect(): Promise<void> {
    this.notifications?.remove();
    this.notifications = null;
    this.waiters.clear();
    this.device = null;
    await getManager().cancelDeviceConnection(this.deviceId).catch(() => undefined);
  }

  /** Sends a write command (e.g. switches, balance) and resolves with the BMS response frame. */
  command(payload: Uint8Array, cmd: number): Promise<Frame> {
    return this.send(payload, cmd, 3000);
  }

  private request(cmd: number, timeoutMs = 3000): Promise<Frame> {
    return this.send(buildReadCommand(cmd), cmd, timeoutMs);
  }

  private send(payload: Uint8Array, cmd: number, timeoutMs: number): Promise<Frame> {
    const device = this.device;
    if (!device) return Promise.reject(new Error(t('ble.notConnected')));
    return new Promise<Frame>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiters.delete(cmd);
        reject(new Error(t('ble.noResponse')));
      }, timeoutMs);
      this.waiters.set(cmd, (frame) => {
        clearTimeout(timer);
        resolve(frame);
      });
      device
        .writeCharacteristicWithoutResponseForService(
          BMS_SERVICE_UUID,
          BMS_WRITE_UUID,
          toBase64(payload),
        )
        .catch((error: Error) => {
          clearTimeout(timer);
          this.waiters.delete(cmd);
          reject(error);
        });
    });
  }
}
