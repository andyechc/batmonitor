import { useEffect, useState } from 'react';

import { BmsSession, type BmsReading } from './connection';

const POLL_INTERVAL_MS = 2000;
const RETRY_DELAY_MS = 4000;

export type MonitorState = {
  status: 'connecting' | 'live' | 'error';
  reading?: BmsReading;
  message?: string;
};

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function useBmsMonitor(deviceId: string): MonitorState {
  const [state, setState] = useState<MonitorState>({ status: 'connecting' });

  useEffect(() => {
    let active = true;
    const session = new BmsSession(deviceId);
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
          const message = error instanceof Error ? error.message : 'Error de conexión';
          setState({ status: 'error', reading: lastReading, message });
          await delay(RETRY_DELAY_MS);
        }
      }
    };

    run();

    return () => {
      active = false;
      session.disconnect();
    };
  }, [deviceId]);

  return state;
}
