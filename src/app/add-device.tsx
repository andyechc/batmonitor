import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import {
  ArrowClockwise,
  BluetoothConnected,
  BluetoothSlash,
  Broadcast,
  MagnifyingGlass,
  WarningCircle,
} from 'phosphor-react-native';

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
        {scanning ? (
          <ActivityIndicator color={colors.charge} />
        ) : (
          <BluetoothSlash size={20} color={colors.muted} weight="duotone" />
        )}
        <Text style={styles.headerText}>
          {scanning ? 'Buscando baterías cerca…' : 'Búsqueda terminada'}
        </Text>
      </View>

      {error && (
        <View style={styles.errorRow}>
          <WarningCircle size={20} color={colors.danger} weight="duotone" />
          <Text style={styles.error}>{error}</Text>
        </View>
      )}

      <FlatList
        data={found}
        keyExtractor={(battery) => battery.id}
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          scanning ? undefined : (
            <View style={styles.emptyState}>
              <MagnifyingGlass size={44} color={colors.muted} weight="duotone" />
              <Text style={styles.empty}>
                No encontramos baterías. Asegúrate de que el BMS esté encendido y cerca.
              </Text>
            </View>
          )
        }
        renderItem={({ item }) => (
          <Pressable
            onPress={() => select(item)}
            style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}>
            <View style={styles.cardIcon}>
              <BluetoothConnected size={22} color={colors.info} weight="duotone" />
            </View>
            <View style={styles.cardText}>
              <Text style={styles.cardName}>{item.name}</Text>
              <Text style={styles.cardId}>{item.id}</Text>
            </View>
            {item.rssi !== null && (
              <View style={styles.signal}>
                <Broadcast size={16} color={colors.muted} />
                <Text style={styles.rssi}>{item.rssi} dBm</Text>
              </View>
            )}
          </Pressable>
        )}
      />

      {!scanning && (
        <Pressable
          onPress={retry}
          style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}>
          <ArrowClockwise size={20} color={colors.text} weight="bold" />
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
  errorRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  error: { color: colors.danger, fontSize: 15, lineHeight: 21, flex: 1 },
  list: { gap: 12, flexGrow: 1 },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    backgroundColor: colors.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 16,
  },
  cardPressed: { backgroundColor: colors.surfaceHigh },
  cardIcon: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.surfaceHigh,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardText: { flex: 1, gap: 4 },
  cardName: { color: colors.text, fontSize: 16, fontWeight: '600' },
  cardId: { color: colors.muted, fontSize: 13, fontVariant: ['tabular-nums'] },
  signal: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  rssi: { color: colors.muted, fontSize: 13, fontVariant: ['tabular-nums'] },
  emptyState: { alignItems: 'center', gap: 12, marginTop: 40 },
  empty: { color: colors.muted, fontSize: 15, textAlign: 'center', lineHeight: 22 },
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: colors.surfaceHigh,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: 14,
  },
  buttonPressed: { opacity: 0.8 },
  buttonText: { color: colors.text, fontSize: 16, fontWeight: '600' },
});
