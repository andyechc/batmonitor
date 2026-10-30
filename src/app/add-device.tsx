import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import {
  isBluetoothOn,
  requestBlePermissions,
  scanForBatteries,
  type ScannedBattery,
} from '@/lib/bms/connection';
import { addSavedBattery } from '@/lib/devices';
import { colors, radius } from '@/theme';

const SCAN_TIMEOUT_MS = 12000;

export default function AddDeviceScreen() {
  const [attempt, setAttempt] = useState(0);
  const [found, setFound] = useState<ScannedBattery[]>([]);
  const [scanning, setScanning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const stopRef = useRef<(() => void) | null>(null);

  const stopScan = () => {
    stopRef.current?.();
    stopRef.current = null;
    setScanning(false);
  };

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const fail = (message: string) => {
      if (!active) return;
      setError(message);
      setScanning(false);
    };

    const begin = async () => {
      if (!(await requestBlePermissions())) {
        fail('Concede los permisos de Bluetooth para buscar baterías.');
        return;
      }
      if (!(await isBluetoothOn())) {
        fail('Activa el Bluetooth para buscar baterías.');
        return;
      }
      if (!active) return;
      setScanning(true);
      stopRef.current = scanForBatteries(
        (battery) => {
          if (!active) return;
          setFound((current) =>
            current.some((item) => item.id === battery.id) ? current : [...current, battery],
          );
        },
        fail,
      );
      timer = setTimeout(stopScan, SCAN_TIMEOUT_MS);
    };

    begin();

    return () => {
      active = false;
      clearTimeout(timer);
      stopRef.current?.();
      stopRef.current = null;
    };
  }, [attempt]);

  const retry = () => {
    setError(null);
    setFound([]);
    setAttempt((value) => value + 1);
  };

  const select = async (battery: ScannedBattery) => {
    stopScan();
    await addSavedBattery({ id: battery.id, name: battery.name });
    router.replace({ pathname: '/device/[id]', params: { id: battery.id } });
  };

  return (
    <View style={styles.screen}>
      <View style={styles.header}>
        {scanning ? <ActivityIndicator color={colors.charge} /> : null}
        <Text style={styles.headerText}>
          {scanning ? 'Buscando baterías cerca…' : 'Búsqueda terminada'}
        </Text>
      </View>

      {error && <Text style={styles.error}>{error}</Text>}

      <FlatList
        data={found}
        keyExtractor={(battery) => battery.id}
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          scanning ? undefined : (
            <Text style={styles.empty}>
              No encontramos baterías. Asegúrate de que el BMS esté encendido y cerca.
            </Text>
          )
        }
        renderItem={({ item }) => (
          <Pressable
            onPress={() => select(item)}
            style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}>
            <View style={styles.cardText}>
              <Text style={styles.cardName}>{item.name}</Text>
              <Text style={styles.cardId}>{item.id}</Text>
            </View>
            {item.rssi !== null && <Text style={styles.rssi}>{item.rssi} dBm</Text>}
          </Pressable>
        )}
      />

      {!scanning && (
        <Pressable
          onPress={retry}
          style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}>
          <Text style={styles.buttonText}>Buscar de nuevo</Text>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg, padding: 16, gap: 12 },
  header: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 8 },
  headerText: { color: colors.text, fontSize: 17, fontWeight: '600' },
  error: { color: colors.danger, fontSize: 15, lineHeight: 21 },
  list: { gap: 12, flexGrow: 1 },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 16,
  },
  cardPressed: { backgroundColor: colors.surfaceHigh },
  cardText: { flex: 1, gap: 4 },
  cardName: { color: colors.text, fontSize: 16, fontWeight: '600' },
  cardId: { color: colors.muted, fontSize: 13, fontVariant: ['tabular-nums'] },
  rssi: { color: colors.muted, fontSize: 13, fontVariant: ['tabular-nums'] },
  empty: { color: colors.muted, fontSize: 15, textAlign: 'center', marginTop: 40, lineHeight: 22 },
  button: {
    backgroundColor: colors.surfaceHigh,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: 14,
    alignItems: 'center',
  },
  buttonPressed: { opacity: 0.8 },
  buttonText: { color: colors.text, fontSize: 16, fontWeight: '600' },
});
