import { router, Stack, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import {
  ArrowDown,
  ArrowsClockwise,
  BatteryMedium,
  BluetoothConnected,
  BluetoothSlash,
  CircleNotch,
  Gauge,
  Info,
  Lightning,
  Pause,
  Pulse,
  Scales,
  ShieldCheck,
  ShieldWarning,
  Thermometer,
  Trash,
} from 'phosphor-react-native';

import { CellBars } from '@/components/CellBars';
import { Card } from '@/components/Card';
import { SocRing } from '@/components/SocRing';
import { StatTile } from '@/components/StatTile';
import { type IconComponent } from '@/components/icons';
import { type BmsReading } from '@/lib/bms/connection';
import { useBmsMonitor, type MonitorState } from '@/lib/bms/useBmsMonitor';
import { removeSavedBattery, useSavedBatteries } from '@/lib/devices';
import { colors, radius } from '@/theme';

const CURRENT_DEADBAND_A = 0.05;

export default function BatteryScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const batteries = useSavedBatteries();
  const name = batteries?.find((battery) => battery.id === id)?.name ?? 'Batería';
  const monitor = useBmsMonitor(id);

  const confirmRemove = () => {
    Alert.alert('Eliminar batería', `¿Quitar ${name} de la app?`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Eliminar',
        style: 'destructive',
        onPress: async () => {
          await removeSavedBattery(id);
          router.back();
        },
      },
    ]);
  };

  return (
    <>
      <Stack.Screen options={{ title: name }} />
      <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
        <StatusPill monitor={monitor} />
        {monitor.reading ? (
          <Dashboard reading={monitor.reading} />
        ) : (
          <View style={styles.placeholder}>
            <ActivityIndicator color={colors.charge} />
            <Text style={styles.placeholderText}>Conectando con la batería…</Text>
          </View>
        )}
        <Pressable
          onPress={confirmRemove}
          style={({ pressed }) => [styles.removeButton, pressed && styles.buttonPressed]}>
          <Trash size={18} color={colors.danger} weight="bold" />
          <Text style={styles.removeText}>Eliminar batería</Text>
        </Pressable>
      </ScrollView>
    </>
  );
}

function StatusPill({ monitor }: { monitor: MonitorState }) {
  const palettes: Record<
    MonitorState['status'],
    { color: string; Icon: IconComponent; label: string }
  > = {
    live: { color: colors.charge, Icon: BluetoothConnected, label: 'En vivo' },
    connecting: { color: colors.info, Icon: CircleNotch, label: 'Conectando…' },
    error: {
      color: colors.danger,
      Icon: BluetoothSlash,
      label: monitor.message ?? 'Sin conexión',
    },
  };
  const palette = palettes[monitor.status];

  return (
    <View style={[styles.pill, { borderColor: palette.color }]}>
      <palette.Icon size={16} color={palette.color} weight="bold" />
      <Text style={[styles.pillText, { color: palette.color }]}>{palette.label}</Text>
    </View>
  );
}

function Dashboard({ reading }: { reading: BmsReading }) {
  const { basic, cells } = reading;
  const power = basic.totalVoltage * basic.current;
  const charging = basic.current > CURRENT_DEADBAND_A;
  const discharging = basic.current < -CURRENT_DEADBAND_A;
  const accent = charging ? colors.charge : discharging ? colors.discharge : colors.info;
  const stateLabel = charging ? 'Cargando' : discharging ? 'Descargando' : 'En reposo';
  const StateIcon: IconComponent = charging ? Lightning : discharging ? ArrowDown : Pause;

  const cellDeltaMv = cells.length ? (Math.max(...cells) - Math.min(...cells)) * 1000 : 0;
  const maxTemp = basic.temperatures.length ? Math.max(...basic.temperatures) : null;
  const activeProtections = basic.protections.filter((protection) => protection.active);

  return (
    <>
      <Card>
        <View style={styles.hero}>
          <SocRing soc={basic.soc} color={accent} />
          <View style={styles.heroStateRow}>
            <StateIcon size={22} color={accent} weight="duotone" />
            <Text style={[styles.heroState, { color: accent }]}>{stateLabel}</Text>
          </View>
          <Text style={styles.heroSub}>
            {basic.remainingCapacity.toFixed(2)} de {basic.nominalCapacity.toFixed(0)} Ah
          </Text>
        </View>
      </Card>

      <View style={styles.grid}>
        <StatTile icon={Lightning} label="Voltaje" value={basic.totalVoltage.toFixed(2)} unit="V" />
        <StatTile
          icon={Pulse}
          label="Corriente"
          value={`${basic.current > 0 ? '+' : ''}${basic.current.toFixed(2)}`}
          unit="A"
          accent={accent}
        />
        <StatTile
          icon={Gauge}
          label="Potencia"
          value={Math.abs(power).toFixed(0)}
          unit="W"
          accent={accent}
        />
        <StatTile icon={ArrowsClockwise} label="Ciclos" value={String(basic.cycles)} />
        <StatTile
          icon={Thermometer}
          label="Temp. máx."
          value={maxTemp === null ? '—' : maxTemp.toFixed(1)}
          unit={maxTemp === null ? undefined : '°C'}
        />
        <StatTile icon={Scales} label="Desbalance" value={cellDeltaMv.toFixed(0)} unit="mV" />
      </View>

      {cells.length > 0 && (
        <Card title={`Celdas · ${cells.length}`} icon={BatteryMedium}>
          <CellBars cells={cells} />
        </Card>
      )}

      {basic.temperatures.length > 0 && (
        <Card title="Temperaturas" icon={Thermometer}>
          {basic.temperatures.map((temperature, index) => (
            <Row key={index} label={`Sensor ${index + 1}`} value={`${temperature.toFixed(1)} °C`} />
          ))}
        </Card>
      )}

      <Card
        title="Protecciones"
        icon={activeProtections.length ? ShieldWarning : ShieldCheck}>
        {activeProtections.length === 0 ? (
          <Text style={[styles.ok, { color: colors.charge }]}>Sin protecciones activas</Text>
        ) : (
          activeProtections.map((protection) => (
            <Text key={protection.label} style={styles.alert}>
              {protection.label}
            </Text>
          ))
        )}
      </Card>

      <Card title="Estado" icon={Info}>
        <Row label="Carga" value={basic.chargeOn ? 'Activada' : 'Desactivada'} />
        <Row label="Descarga" value={basic.dischargeOn ? 'Activada' : 'Desactivada'} />
        <Row label="Celdas en balanceo" value={String(basic.balancing.filter(Boolean).length)} />
        <Row label="Firmware" value={basic.firmwareVersion} />
        <Row label="Fabricación" value={basic.productionDate} />
      </Card>
    </>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { padding: 16, gap: 14, paddingBottom: 40 },
  pill: {
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderWidth: 1,
    borderRadius: radius.pill,
    paddingVertical: 6,
    paddingHorizontal: 14,
  },
  pillText: { fontSize: 13, fontWeight: '600' },
  placeholder: { alignItems: 'center', gap: 12, paddingVertical: 60 },
  placeholderText: { color: colors.muted, fontSize: 15 },
  hero: { alignItems: 'center', gap: 12, paddingVertical: 8 },
  heroStateRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  heroState: { fontSize: 20, fontWeight: '700' },
  heroSub: { color: colors.muted, fontSize: 14, fontVariant: ['tabular-nums'] },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  rowLabel: { color: colors.muted, fontSize: 15 },
  rowValue: { color: colors.text, fontSize: 15, fontWeight: '500', fontVariant: ['tabular-nums'] },
  ok: { fontSize: 15, fontWeight: '600' },
  alert: { color: colors.danger, fontSize: 15, fontWeight: '600' },
  removeButton: {
    marginTop: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 14,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
  },
  buttonPressed: { opacity: 0.8 },
  removeText: { color: colors.danger, fontSize: 15, fontWeight: '600' },
});
