import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
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
  PencilSimple,
  Pulse,
  Scales,
  ShieldCheck,
  ShieldWarning,
  Thermometer,
  Trash,
  Sliders,
} from 'phosphor-react-native';

import { CellBars } from '@/components/CellBars';
import { Card } from '@/components/Card';
import { SocRing } from '@/components/SocRing';
import { StatTile } from '@/components/StatTile';
import { type IconComponent } from '@/components/icons';
import { type BmsReading } from '@/lib/bms/connection';
import {
  BALANCE_COMMAND,
  buildSwitchCommand,
  CLEAR_PROTECTION_COMMAND,
  CMD_CONTROL,
  CMD_SWITCH,
} from '@/lib/bms/protocol';
import { type BmsMonitor, useBmsMonitor } from '@/lib/bms/useBmsMonitor';
import { removeSavedBattery, renameSavedBattery, useSavedBatteries } from '@/lib/devices';
import { t } from '@/lib/i18n';
import { colors, radius } from '@/theme';

const CURRENT_DEADBAND_A = 0.05;

export default function BatteryScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const batteries = useSavedBatteries();
  const name = batteries?.find((battery) => battery.id === id)?.name ?? t('ble.unnamed');
  const monitor = useBmsMonitor(id);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [renaming, setRenaming] = useState(false);
  const [draftName, setDraftName] = useState('');

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setMessage(null);
    try {
      await action();
      setMessage(t('device.controlOk'));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : t('ble.writeFailed'));
    } finally {
      setBusy(false);
    }
  };

  const toggleSwitch = (which: 'charge' | 'discharge') => {
    const basic = monitor.reading?.basic;
    if (!basic) return;
    const charge = which === 'charge' ? !basic.chargeOn : basic.chargeOn;
    const discharge = which === 'discharge' ? !basic.dischargeOn : basic.dischargeOn;
    const apply = () =>
      run(() => monitor.sendCommand(buildSwitchCommand(charge, discharge), CMD_SWITCH));
    const turningOff = which === 'charge' ? !charge : !discharge;
    if (!turningOff) {
      apply();
      return;
    }
    Alert.alert(
      t(which === 'charge' ? 'device.chargeSwitch' : 'device.dischargeSwitch'),
      t(which === 'charge' ? 'device.confirmChargeOff' : 'device.confirmDischargeOff'),
      [
        { text: t('device.cancel'), style: 'cancel' },
        { text: t('device.confirm'), style: 'destructive', onPress: apply },
      ],
    );
  };

  const confirmRemove = () => {
    Alert.alert(t('device.removeTitle'), t('device.removeConfirm', { name }), [
      { text: t('device.cancel'), style: 'cancel' },
      {
        text: t('device.delete'),
        style: 'destructive',
        onPress: async () => {
          await removeSavedBattery(id);
          router.back();
        },
      },
    ]);
  };

  const openRename = () => {
    setDraftName(name);
    setRenaming(true);
  };

  const saveRename = async () => {
    const trimmed = draftName.trim();
    if (!trimmed) {
      setMessage(t('device.renameEmpty'));
      return;
    }
    await renameSavedBattery(id, trimmed);
    setRenaming(false);
    setMessage(t('device.renameOk'));
  };

  const controlsEnabled = monitor.status === 'live' && !busy;

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
            <Text style={styles.placeholderText}>{t('device.connecting')}</Text>
          </View>
        )}

        <Card title={t('device.controls')} icon={Sliders}>
          <SwitchRow
            label={t('device.chargeSwitch')}
            on={monitor.reading?.basic.chargeOn ?? false}
            disabled={!controlsEnabled || !monitor.reading}
            onToggle={() => toggleSwitch('charge')}
          />
          <SwitchRow
            label={t('device.dischargeSwitch')}
            on={monitor.reading?.basic.dischargeOn ?? false}
            disabled={!controlsEnabled || !monitor.reading}
            onToggle={() => toggleSwitch('discharge')}
          />
          <ActionRow
            label={t('device.balance')}
            disabled={!controlsEnabled}
            onPress={() => run(() => monitor.sendCommand(BALANCE_COMMAND, CMD_CONTROL))}
          />
          <ActionRow
            label={t('device.clearProtection')}
            disabled={!controlsEnabled}
            onPress={() => run(() => monitor.sendCommand(CLEAR_PROTECTION_COMMAND, CMD_CONTROL))}
          />
          <ActionRow label={t('device.rename')} disabled={false} onPress={openRename} />
          {message && <Text style={styles.controlMessage}>{message}</Text>}
        </Card>

        <Pressable
          onPress={confirmRemove}
          style={({ pressed }) => [styles.removeButton, pressed && styles.buttonPressed]}>
          <Trash size={18} color={colors.danger} weight="bold" />
          <Text style={styles.removeText}>{t('device.remove')}</Text>
        </Pressable>
      </ScrollView>

      <Modal visible={renaming} transparent animationType="fade" onRequestClose={() => setRenaming(false)}>
        <View style={styles.backdrop}>
          <View style={styles.modal}>
            <Text style={styles.modalTitle}>{t('device.renameTitle')}</Text>
            <TextInput
              value={draftName}
              onChangeText={setDraftName}
              autoFocus
              maxLength={24}
              style={styles.input}
              placeholderTextColor={colors.muted}
            />
            <Text style={styles.modalHint}>{t('device.renameHint')}</Text>
            <View style={styles.modalActions}>
              <Pressable onPress={() => setRenaming(false)} style={styles.modalButton}>
                <Text style={styles.modalCancel}>{t('device.cancel')}</Text>
              </Pressable>
              <Pressable onPress={saveRename} style={[styles.modalButton, styles.modalPrimary]}>
                <Text style={styles.modalPrimaryText}>{t('device.save')}</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </>
  );
}

function SwitchRow({
  label,
  on,
  disabled,
  onToggle,
}: {
  label: string;
  on: boolean;
  disabled: boolean;
  onToggle: () => void;
}) {
  return (
    <Pressable
      onPress={onToggle}
      disabled={disabled}
      style={({ pressed }) => [styles.actionRow, disabled && styles.disabled, pressed && styles.buttonPressed]}>
      <Text style={styles.rowLabel}>{label}</Text>
      <View style={[styles.toggle, on && styles.toggleOn]}>
        <Text style={[styles.toggleText, on && styles.toggleTextOn]}>
          {on ? t('device.on') : t('device.off')}
        </Text>
      </View>
    </Pressable>
  );
}

function ActionRow({
  label,
  disabled,
  onPress,
}: {
  label: string;
  disabled: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [styles.actionRow, disabled && styles.disabled, pressed && styles.buttonPressed]}>
      <Text style={styles.rowLabel}>{label}</Text>
      <PencilSimple size={18} color={colors.muted} />
    </Pressable>
  );
}

function StatusPill({ monitor }: { monitor: BmsMonitor }) {
  const palettes: Record<
    BmsMonitor['status'],
    { color: string; Icon: IconComponent; label: string }
  > = {
    live: { color: colors.charge, Icon: BluetoothConnected, label: t('device.live') },
    connecting: { color: colors.info, Icon: CircleNotch, label: t('device.connectingPill') },
    error: {
      color: colors.danger,
      Icon: BluetoothSlash,
      label: monitor.message ?? t('device.noConnection'),
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
  const stateLabel = charging
    ? t('device.state.charging')
    : discharging
      ? t('device.state.discharging')
      : t('device.state.idle');
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
        <StatTile
          icon={Lightning}
          label={t('device.stat.voltage')}
          value={basic.totalVoltage.toFixed(2)}
          unit="V"
        />
        <StatTile
          icon={Pulse}
          label={t('device.stat.current')}
          value={`${basic.current > 0 ? '+' : ''}${basic.current.toFixed(2)}`}
          unit="A"
          accent={accent}
        />
        <StatTile
          icon={Gauge}
          label={t('device.stat.power')}
          value={Math.abs(power).toFixed(0)}
          unit="W"
          accent={accent}
        />
        <StatTile icon={ArrowsClockwise} label={t('device.stat.cycles')} value={String(basic.cycles)} />
        <StatTile
          icon={Thermometer}
          label={t('device.stat.maxTemp')}
          value={maxTemp === null ? '—' : maxTemp.toFixed(1)}
          unit={maxTemp === null ? undefined : '°C'}
        />
        <StatTile
          icon={Scales}
          label={t('device.stat.imbalance')}
          value={cellDeltaMv.toFixed(0)}
          unit="mV"
        />
      </View>

      {cells.length > 0 && (
        <Card title={`${t('device.cells')} · ${cells.length}`} icon={BatteryMedium}>
          <CellBars cells={cells} />
        </Card>
      )}

      {basic.temperatures.length > 0 && (
        <Card title={t('device.temperatures')} icon={Thermometer}>
          {basic.temperatures.map((temperature, index) => (
            <Row
              key={index}
              label={`${t('device.sensor')} ${index + 1}`}
              value={`${temperature.toFixed(1)} °C`}
            />
          ))}
        </Card>
      )}

      <Card
        title={t('device.protections')}
        icon={activeProtections.length ? ShieldWarning : ShieldCheck}>
        {activeProtections.length === 0 ? (
          <Text style={[styles.ok, { color: colors.charge }]}>{t('device.noProtections')}</Text>
        ) : (
          activeProtections.map((protection) => (
            <Text key={protection.label} style={styles.alert}>
              {protection.label}
            </Text>
          ))
        )}
      </Card>

      <Card title={t('device.status')} icon={Info}>
        <Row label={t('device.chargeSwitch')} value={basic.chargeOn ? t('device.on') : t('device.off')} />
        <Row
          label={t('device.dischargeSwitch')}
          value={basic.dischargeOn ? t('device.on') : t('device.off')}
        />
        <Row
          label={t('device.balancingCells')}
          value={String(basic.balancing.filter(Boolean).length)}
        />
        <Row label={t('device.firmware')} value={basic.firmwareVersion} />
        <Row label={t('device.manufactured')} value={basic.productionDate} />
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
  actionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
  },
  disabled: { opacity: 0.4 },
  toggle: {
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    paddingVertical: 4,
    paddingHorizontal: 12,
  },
  toggleOn: { borderColor: colors.charge, backgroundColor: colors.charge },
  toggleText: { color: colors.muted, fontSize: 13, fontWeight: '600' },
  toggleTextOn: { color: colors.bg },
  controlMessage: { color: colors.muted, fontSize: 13, marginTop: 4 },
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
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  modal: {
    width: '100%',
    backgroundColor: colors.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 20,
    gap: 12,
  },
  modalTitle: { color: colors.text, fontSize: 18, fontWeight: '700' },
  input: {
    color: colors.text,
    fontSize: 16,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.pill,
    paddingVertical: 10,
    paddingHorizontal: 16,
  },
  modalHint: { color: colors.muted, fontSize: 13 },
  modalActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 12 },
  modalButton: { paddingVertical: 10, paddingHorizontal: 16, borderRadius: radius.pill },
  modalPrimary: { backgroundColor: colors.charge },
  modalCancel: { color: colors.muted, fontSize: 15, fontWeight: '600' },
  modalPrimaryText: { color: colors.bg, fontSize: 15, fontWeight: '700' },
});
