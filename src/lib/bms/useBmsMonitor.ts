import { useCallback, useEffect, useRef, useState } from 'react';

import { t } from '../i18n';
import { BmsSession, type BmsReading } from './connection';
import { type Frame } from './protocol';

const POLL_INTERVAL_MS = 2000;
const RETRY_DELAY_MS = 4000;

export type MonitorState = {
  status: 'connecting' | 'live' | 'error';
  reading?: BmsReading;
  message?: string;
};

export type BmsMonitor = MonitorState & {
  sendCommand: (payload: Uint8Array, cmd: number) => Promise<Frame>;
};

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function useBmsMonitor(deviceId: string): BmsMonitor {
  const [state, setState] = useState<MonitorState>({ status: 'connecting' });
  const sessionRef = useRef<BmsSession | null>(null);

  useEffect(() => {
    let active = true;
    const session = new BmsSession(deviceId);
    sessionRef.current = session;
    let lastReading: BmsReading | undefined;

    const run = async () => {
      let connected = false;
      while (active) {
        try {
          if (!connected) {
            setState({ status: 'connecting', reading: lastReading });
            await session.connect();
            connected = true;
          }
          const reading = await session.read();
          lastReading = reading;
          if (active) setState({ status: 'live', reading });
          await delay(POLL_INTERVAL_MS);
        } catch (error) {
          connected = false;
          await session.disconnect();
          if (!active) break;
          const message = error instanceof Error ? error.message : t('device.noConnection');
          setState({ status: 'error', reading: lastReading, message });
          await delay(RETRY_DELAY_MS);
        }
      }
    };

    run();

    return () => {
      active = false;
      sessionRef.current = null;
      session.disconnect();
    };
  }, [deviceId]);

  const sendCommand = useCallback(
    (payload: Uint8Array, cmd: number) => {
      const session = sessionRef.current;
      if (!session) return Promise.reject(new Error(t('ble.notConnected')));
      return session.command(payload, cmd);
    },
    [],
  );

  return { ...state, sendCommand };
}
